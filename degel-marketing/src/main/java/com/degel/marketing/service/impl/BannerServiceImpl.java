package com.degel.marketing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.entity.Banner;
import com.degel.marketing.mapper.BannerMapper;
import com.degel.marketing.service.BannerService;
import com.degel.marketing.vo.BannerCreateVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    /** linkType：0=无跳转 1=内部页面 2=商品详情 3=外部链接 */
    private static final int LINK_NONE = 0;
    private static final int LINK_PAGE = 1;
    private static final int LINK_SPU = 2;
    private static final int LINK_HTTPS = 3;

    @Override
    public IPage<Banner> page(Long page, Long pageSize, String title, Integer status) {
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(title)) {
            wrapper.like(Banner::getTitle, title);
        }
        if (status != null) {
            wrapper.eq(Banner::getStatus, status);
        }
        wrapper.orderByDesc(Banner::getCreateTime);
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public void saveOrUpdateBanner(BannerCreateVo vo) {
        validate(vo);
        if (vo.getId() == null) {
            Banner banner = new Banner();
            copy(vo, banner);
            save(banner);
            log.info("banner created id={} title={}", banner.getId(), banner.getTitle());
            return;
        }
        if (getById(vo.getId()) == null) {
            throw new BusinessException("轮播图不存在");
        }
        // 显式 set 全部可编辑字段：LambdaUpdateWrapper.set 对 null 生成 SET xxx = NULL，
        // 避免 updateById 的 NOT_NULL 策略导致 linkValue/startTime/endTime 清空后旧值残留
        update(new LambdaUpdateWrapper<Banner>()
                .eq(Banner::getId, vo.getId())
                .set(Banner::getTitle, vo.getTitle())
                .set(Banner::getImage, vo.getImage())
                .set(Banner::getLinkType, vo.getLinkType())
                .set(Banner::getLinkValue, vo.getLinkValue())
                .set(Banner::getSort, vo.getSort())
                .set(Banner::getStartTime, vo.getStartTime())
                .set(Banner::getEndTime, vo.getEndTime()));
        log.info("banner updated id={}", vo.getId());
    }

    @Override
    public void delete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("轮播图不存在");
        }
    }

    @Override
    public void toggleStatus(Long id) {
        // 原子翻转，避免"读 status 再写回"的竞态（并发 toggle 会互相覆盖/丢翻转）；
        // @TableLogic 自动拼 del_flag=0，命中 0 行即记录不存在
        boolean updated = update(new LambdaUpdateWrapper<Banner>()
                .eq(Banner::getId, id)
                .setSql("status = 1 - status"));
        if (!updated) {
            throw new BusinessException("轮播图不存在");
        }
        log.info("banner toggle-status id={}", id);
    }

    @Override
    public List<Banner> listActive() {
        return list(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, 1)
                .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, LocalDateTime.now()))
                .and(w -> w.isNull(Banner::getEndTime).or().ge(Banner::getEndTime, LocalDateTime.now()))
                .orderByAsc(Banner::getSort).orderByDesc(Banner::getId)
                .last("LIMIT 10"));
    }

    private void validate(BannerCreateVo vo) {
        Integer linkType = vo.getLinkType();
        if (linkType == null || (linkType != LINK_NONE && linkType != LINK_PAGE
                && linkType != LINK_SPU && linkType != LINK_HTTPS)) {
            throw new BusinessException("跳转类型必须是 无跳转(0)/内部页面(1)/商品详情(2)/外部链接(3)");
        }
        if (linkType > LINK_NONE && StrUtil.isBlank(vo.getLinkValue())) {
            throw new BusinessException("跳转类型非无跳转时必须填写跳转值");
        }
        if (linkType == LINK_HTTPS && !vo.getLinkValue().startsWith("https://")) {
            throw new BusinessException("外部链接必须以 https:// 开头");
        }
        if (linkType == LINK_SPU && !vo.getLinkValue().matches("\\d+")) {
            throw new BusinessException("商品详情的跳转值必须是纯数字 spuId");
        }
        if (vo.getStartTime() != null && vo.getEndTime() != null
                && !vo.getStartTime().isBefore(vo.getEndTime())) {
            throw new BusinessException("生效时间必须早于失效时间");
        }
    }

    private void copy(BannerCreateVo vo, Banner banner) {
        banner.setTitle(vo.getTitle());
        banner.setImage(vo.getImage());
        banner.setLinkType(vo.getLinkType());
        banner.setLinkValue(vo.getLinkValue());
        banner.setSort(vo.getSort());
        banner.setStartTime(vo.getStartTime());
        banner.setEndTime(vo.getEndTime());
    }
}
