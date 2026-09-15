package com.degel.marketing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.entity.SeckillProduct;
import com.degel.marketing.entity.SeckillSession;
import com.degel.marketing.mapper.SeckillSessionMapper;
import com.degel.marketing.service.SeckillProductService;
import com.degel.marketing.service.SeckillSessionService;
import com.degel.marketing.vo.SeckillSessionCreateVo;
import com.degel.marketing.vo.SeckillSessionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionMapper, SeckillSession>
        implements SeckillSessionService {

    private final SeckillProductService seckillProductService;

    @Override
    public IPage<SeckillSession> page(Long page, Long pageSize, String name, Integer status) {
        LambdaQueryWrapper<SeckillSession> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(name)) {
            wrapper.like(SeckillSession::getName, name);
        }
        if (status != null) {
            wrapper.eq(SeckillSession::getStatus, status);
        }
        wrapper.orderByAsc(SeckillSession::getStartTime);
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public void saveOrUpdateSession(SeckillSessionCreateVo vo) {
        if (!vo.getStartTime().isBefore(vo.getEndTime())) {
            throw new BusinessException("开始时间必须早于结束时间");
        }
        if (vo.getId() == null) {
            SeckillSession session = new SeckillSession();
            session.setName(vo.getName());
            session.setStartTime(vo.getStartTime());
            session.setEndTime(vo.getEndTime());
            session.setSort(vo.getSort());
            // status 不设置：走 DB DEFAULT 0（新增默认停用，人工启用）
            save(session);
            log.info("seckill session created id={} name={}", session.getId(), session.getName());
            return;
        }
        if (getById(vo.getId()) == null) {
            throw new BusinessException("秒杀场次不存在");
        }
        // 显式 set 全部可编辑字段（对齐 banner 编辑分支）：全字段必填无 null 清除问题，
        // 但仍统一用 LambdaUpdateWrapper 显式 set，避免 updateById NOT_NULL 策略的隐性差异
        update(new LambdaUpdateWrapper<SeckillSession>()
                .eq(SeckillSession::getId, vo.getId())
                .set(SeckillSession::getName, vo.getName())
                .set(SeckillSession::getStartTime, vo.getStartTime())
                .set(SeckillSession::getEndTime, vo.getEndTime())
                .set(SeckillSession::getSort, vo.getSort()));
        log.info("seckill session updated id={}", vo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("秒杀场次不存在");
        }
        // 级联逻辑删场次下全部商品，避免孤儿商品仍被 /inner/seckill/detail 查到
        // （@TableLogic 下 remove 即逻辑删）
        seckillProductService.remove(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getSessionId, id));
        log.info("seckill session deleted id={}, cascaded products", id);
    }

    @Override
    public void toggleStatus(Long id) {
        // 原子翻转，避免"读 status 再写回"的竞态（并发 toggle 会互相覆盖/丢翻转）；
        // @TableLogic 自动拼 del_flag=0，命中 0 行即记录不存在
        boolean updated = update(new LambdaUpdateWrapper<SeckillSession>()
                .eq(SeckillSession::getId, id)
                .setSql("status = 1 - status"));
        if (!updated) {
            throw new BusinessException("秒杀场次不存在");
        }
        log.info("seckill session toggle-status id={}", id);
    }

    @Override
    public List<SeckillSessionVo> listCurrent() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillSession> sessions = list(new LambdaQueryWrapper<SeckillSession>()
                .eq(SeckillSession::getStatus, 1)
                .gt(SeckillSession::getEndTime, now)
                .lt(SeckillSession::getStartTime, now.plusHours(24))
                .orderByAsc(SeckillSession::getSort)
                .orderByAsc(SeckillSession::getStartTime));
        return sessions.stream().map(session -> {
            SeckillSessionVo vo = SeckillSessionVo.from(session);
            vo.setProducts(seckillProductService.listBySession(session.getId()));
            return vo;
        }).collect(Collectors.toList());
    }
}
