package com.degel.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.entity.SeckillProduct;
import com.degel.marketing.entity.SeckillSession;
import com.degel.marketing.mapper.SeckillProductMapper;
import com.degel.marketing.mapper.SeckillSessionMapper;
import com.degel.marketing.service.SeckillProductService;
import com.degel.marketing.vo.SeckillProductCreateVo;
import com.degel.marketing.vo.SeckillProductVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillProductServiceImpl extends ServiceImpl<SeckillProductMapper, SeckillProduct>
        implements SeckillProductService {

    /** 用 Mapper 而非 SeckillSessionService，避免 service 循环依赖 */
    private final SeckillSessionMapper seckillSessionMapper;

    @Override
    public IPage<SeckillProduct> pageBySession(Long sessionId, Long page, Long pageSize) {
        return page(new Page<>(page, pageSize), new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getSessionId, sessionId)
                .orderByAsc(SeckillProduct::getSort));
    }

    @Override
    public void saveOrUpdateProduct(SeckillProductCreateVo vo) {
        // 挂靠的场次必须存在（selectById 自动过滤逻辑删记录）
        if (seckillSessionMapper.selectById(vo.getSessionId()) == null) {
            throw new BusinessException("秒杀场次不存在");
        }
        // 同场次 skuId 唯一（编辑时排除自身；@TableLogic 自动过滤已逻辑删的记录）
        long duplicated = count(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getSessionId, vo.getSessionId())
                .eq(SeckillProduct::getSkuId, vo.getSkuId())
                .ne(vo.getId() != null, SeckillProduct::getId, vo.getId()));
        if (duplicated > 0) {
            throw new BusinessException("该场次已存在此 SKU");
        }
        if (vo.getId() == null) {
            SeckillProduct product = new SeckillProduct();
            copy(vo, product);
            save(product);
            log.info("seckill product created id={} sessionId={} skuId={}",
                    product.getId(), product.getSessionId(), product.getSkuId());
            return;
        }
        if (getById(vo.getId()) == null) {
            throw new BusinessException("秒杀商品不存在");
        }
        // 显式 set 全部可编辑字段（对齐 banner 编辑分支）
        update(new LambdaUpdateWrapper<SeckillProduct>()
                .eq(SeckillProduct::getId, vo.getId())
                .set(SeckillProduct::getSessionId, vo.getSessionId())
                .set(SeckillProduct::getSpuId, vo.getSpuId())
                .set(SeckillProduct::getSkuId, vo.getSkuId())
                .set(SeckillProduct::getSeckillPrice, vo.getSeckillPrice())
                .set(SeckillProduct::getSeckillStock, vo.getSeckillStock())
                .set(SeckillProduct::getPerLimit, vo.getPerLimit())
                .set(SeckillProduct::getSort, vo.getSort()));
        log.info("seckill product updated id={}", vo.getId());
    }

    @Override
    public void delete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("秒杀商品不存在");
        }
    }

    @Override
    public List<SeckillProductVo> listBySession(Long sessionId) {
        return list(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getSessionId, sessionId)
                .orderByAsc(SeckillProduct::getSort))
                .stream().map(SeckillProductVo::from).collect(Collectors.toList());
    }

    @Override
    public SeckillProductVo getDetail(Long sessionId, Long skuId) {
        // 场次校验：不存在（含已逻辑删）或未启用时返回 null，
        // 场次停用后 C 端不应再取到价格快照
        SeckillSession session = seckillSessionMapper.selectById(sessionId);
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())) {
            return null;
        }
        SeckillProduct product = getOne(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getSessionId, sessionId)
                .eq(SeckillProduct::getSkuId, skuId)
                .last("LIMIT 1"));
        return product == null ? null : SeckillProductVo.from(product);
    }

    private void copy(SeckillProductCreateVo vo, SeckillProduct product) {
        product.setSessionId(vo.getSessionId());
        product.setSpuId(vo.getSpuId());
        product.setSkuId(vo.getSkuId());
        product.setSeckillPrice(vo.getSeckillPrice());
        product.setSeckillStock(vo.getSeckillStock());
        product.setPerLimit(vo.getPerLimit());
        product.setSort(vo.getSort());
    }
}
