package com.degel.marketing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.entity.Coupon;
import com.degel.marketing.entity.UserCoupon;
import com.degel.marketing.mapper.CouponMapper;
import com.degel.marketing.mapper.UserCouponMapper;
import com.degel.marketing.service.CouponService;
import com.degel.marketing.vo.CouponCreateVo;
import com.degel.marketing.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    private final UserCouponMapper userCouponMapper;

    /** 优惠类型：1=满减 2=折扣（三期） 3=无门槛 */
    private static final int TYPE_THRESHOLD = 1;
    private static final int TYPE_DISCOUNT = 2;
    private static final int TYPE_NO_THRESHOLD = 3;

    /** 券型：1=平台 2=店铺 3=分摊 */
    private static final int FUNDER_PLATFORM = 1;
    private static final int FUNDER_SHOP = 2;
    private static final int FUNDER_SHARED = 3;

    /** 店铺券防滥用上限（设计 §5.1：一期免平台审核，用上限防滥用） */
    private static final int SHOP_COUPON_MAX_TOTAL = 5000;
    private static final int SHOP_COUPON_MAX_PER_USER = 5;

    @Override
    public CouponVO createPlatformCoupon(CouponCreateVo vo, Long userId) {
        // 三期：平台端可建分摊券（vo.funderType=3 + shopId + 出资拆分）；默认/显式 1 为平台券
        int funderType = vo.getFunderType() != null && vo.getFunderType() == FUNDER_SHARED ? FUNDER_SHARED : FUNDER_PLATFORM;
        if (funderType == FUNDER_SHARED) {
            if (vo.getShopId() == null || vo.getShopId() <= 0) {
                throw new BusinessException("分摊券必须指定合作店铺");
            }
            if (vo.getPlatformAmount() == null || vo.getShopAmount() == null
                    || vo.getPlatformAmount().compareTo(BigDecimal.ZERO) <= 0
                    || vo.getShopAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("分摊券平台/店铺承担金额必须都大于0");
            }
            if (vo.getDiscountType() != null && vo.getDiscountType() == TYPE_THRESHOLD) {
                // 满减分摊券：出资拆分之和必须等于优惠额（记账口径硬约束）
                BigDecimal sum = vo.getPlatformAmount().add(vo.getShopAmount());
                if (sum.compareTo(vo.getDiscountValue()) != 0) {
                    throw new BusinessException("满减分摊券的平台+店铺承担之和必须等于优惠金额");
                }
            }
            // 折扣分摊券：两金额为"预期减免额"拆分，仅定 lock 时的出资比例，不强制等于折数
        }
        validateCreate(vo);
        Coupon coupon = buildCoupon(vo, funderType, funderType == FUNDER_SHARED ? vo.getShopId() : null, userId);
        save(coupon);
        return toVO(coupon);
    }

    @Override
    public CouponVO createShopCoupon(CouponCreateVo vo, Long shopId, Long userId) {
        if (shopId == null || shopId <= 0) {
            throw new BusinessException("店铺券必须指定店铺");
        }
        validateCreate(vo);
        // 防滥用上限（仅店铺券；平台券不受限）
        if (vo.getTotalCount() > SHOP_COUPON_MAX_TOTAL) {
            throw new BusinessException("店铺券发放总量不能超过 " + SHOP_COUPON_MAX_TOTAL);
        }
        if (vo.getPerUserLimit() > SHOP_COUPON_MAX_PER_USER) {
            throw new BusinessException("店铺券每人限领不能超过 " + SHOP_COUPON_MAX_PER_USER);
        }
        Coupon coupon = buildCoupon(vo, FUNDER_SHOP, shopId, userId);
        // 三期起平台审核：创建即待审核、未生效（此前二期为创建即生效）
        coupon.setAuditStatus(1);
        coupon.setStatus(0);
        save(coupon);
        return toVO(coupon);
    }

    @Override
    public void auditCoupon(Long couponId, boolean passed, String rejectReason) {
        Coupon coupon = getById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getAuditStatus() == null || coupon.getAuditStatus() != 1) {
            throw new BusinessException("仅待审核状态的券可以执行审核操作");
        }
        Coupon update = new Coupon();
        update.setId(couponId);
        if (passed) {
            update.setAuditStatus(2);
            // 审核通过才生效可领
            update.setStatus(1);
        } else {
            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                throw new BusinessException("驳回必须填写理由");
            }
            update.setAuditStatus(3);
            update.setRejectReason(rejectReason);
        }
        updateById(update);
    }

    private Coupon buildCoupon(CouponCreateVo vo, int funderType, Long shopId, Long userId) {
        Coupon coupon = new Coupon();
        coupon.setName(vo.getName());
        coupon.setFunderType(funderType);
        coupon.setShopId(shopId);
        // 出资拆分（快照比例，lock 按此拆实算优惠）：
        // 平台券 100:0、店铺券 0:100、分摊券创建端显式拆。
        // 折扣券的 discountValue 是折数，单方出资时金额列仅承载比例（100:0/0:100），无金额语义
        if (funderType == FUNDER_PLATFORM) {
            coupon.setPlatformAmount(vo.getDiscountValue());
            coupon.setShopAmount(BigDecimal.ZERO);
        } else if (funderType == FUNDER_SHOP) {
            coupon.setPlatformAmount(BigDecimal.ZERO);
            coupon.setShopAmount(vo.getDiscountValue());
        } else {
            coupon.setPlatformAmount(vo.getPlatformAmount());
            coupon.setShopAmount(vo.getShopAmount());
        }
        coupon.setDiscountType(vo.getDiscountType());
        coupon.setThresholdAmount(vo.getThresholdAmount());
        coupon.setDiscountValue(vo.getDiscountValue());
        coupon.setScopeType(0);
        coupon.setTotalCount(vo.getTotalCount());
        coupon.setIssuedCount(0);
        coupon.setPerUserLimit(vo.getPerUserLimit());
        coupon.setReceiveStart(vo.getReceiveStart());
        coupon.setReceiveEnd(vo.getReceiveEnd());
        coupon.setValidType(vo.getValidType());
        coupon.setValidStart(vo.getValidStart());
        coupon.setValidEnd(vo.getValidEnd());
        coupon.setValidDays(vo.getValidDays());
        // 平台端券型创建即生效；店铺券在 createShopCoupon 里覆盖为待审核
        coupon.setStatus(1);
        coupon.setAuditStatus(2);
        coupon.setCreateBy(userId);
        return coupon;
    }

    private void validateCreate(CouponCreateVo vo) {
        if (vo.getDiscountType() == null
                || (vo.getDiscountType() != TYPE_THRESHOLD && vo.getDiscountType() != TYPE_DISCOUNT
                        && vo.getDiscountType() != TYPE_NO_THRESHOLD)) {
            throw new BusinessException("优惠类型必须是 满减(1)/折扣(2)/无门槛(3)");
        }
        if (vo.getDiscountValue() == null || vo.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(vo.getDiscountType() == TYPE_DISCOUNT ? "折数必须大于0" : "优惠金额必须大于0");
        }
        if (vo.getDiscountType() == TYPE_DISCOUNT) {
            // 折数语义：8.5 = 8.5折。合法区间 (0, 10)，禁止 0 折（全免）与 ≥10 折（无优惠）
            if (vo.getDiscountValue().compareTo(BigDecimal.TEN) >= 0) {
                throw new BusinessException("折数必须小于10（如 8.5 表示八五折）");
            }
            // 折扣券门槛可配可不配（满X可打Y折）；无门槛语义下置 0
            if (vo.getThresholdAmount() == null) {
                vo.setThresholdAmount(BigDecimal.ZERO);
            }
        } else if (vo.getDiscountType() == TYPE_THRESHOLD) {
            if (vo.getThresholdAmount() == null || vo.getThresholdAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("满减券必须设置大于0的使用门槛");
            }
            // 优惠额 ≤ 门槛：防止"满10减20"这类资损配置
            if (vo.getDiscountValue().compareTo(vo.getThresholdAmount()) > 0) {
                throw new BusinessException("优惠金额不能大于使用门槛");
            }
        } else {
            vo.setThresholdAmount(BigDecimal.ZERO);
        }
        if (vo.getReceiveStart() == null || vo.getReceiveEnd() == null
                || !vo.getReceiveStart().isBefore(vo.getReceiveEnd())) {
            throw new BusinessException("可领时间不合法（开始必须早于结束）");
        }
        if (vo.getValidType() == null || (vo.getValidType() != 1 && vo.getValidType() != 2)) {
            throw new BusinessException("有效期类型必须是 1(绝对时间) 或 2(领取后N天)");
        }
        if (vo.getValidType() == 1
                && (vo.getValidStart() == null || vo.getValidEnd() == null
                    || !vo.getValidStart().isBefore(vo.getValidEnd()))) {
            throw new BusinessException("绝对有效期不合法（开始必须早于结束）");
        }
        if (vo.getValidType() == 2 && (vo.getValidDays() == null || vo.getValidDays() <= 0)) {
            throw new BusinessException("领取后N天有效必须 N>0");
        }
    }

    @Override
    public void stopCoupon(Long couponId, Long shopId) {
        Coupon coupon = getById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        // 店铺模式：只能停自己店的券（funderType 不限——防御历史数据）
        if (shopId != null && shopId > 0 && !shopId.equals(coupon.getShopId())) {
            throw new BusinessException("无权操作其他店铺的优惠券");
        }
        if (coupon.getStatus() != 1) {
            throw new BusinessException("仅进行中的券可以停发");
        }
        Coupon update = new Coupon();
        update.setId(couponId);
        update.setStatus(2);
        updateById(update);
    }

    @Override
    public IPage<CouponVO> pagePlatformCoupons(Page<Coupon> page, String name, Integer status, Integer auditStatus) {
        return pageCoupons(page, name, status, auditStatus, null, 1);
    }

    @Override
    public IPage<CouponVO> pageShopCoupons(Page<Coupon> page, Long shopId, String name, Integer status) {
        if (shopId == null || shopId <= 0) {
            throw new BusinessException("店铺id不合法");
        }
        return pageCoupons(page, name, status, null, shopId, 2);
    }

    private IPage<CouponVO> pageCoupons(Page<Coupon> page, String name, Integer status,
                                        Integer auditStatus, Long shopId, Integer funderType) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>();
        // 三期：平台列表展示全部券型（含店铺券审核）；funderType 仅在明确指定时过滤
        if (funderType != null) {
            wrapper.eq(Coupon::getFunderType, funderType);
        }
        if (shopId != null) {
            wrapper.eq(Coupon::getShopId, shopId);
        }
        if (StrUtil.isNotBlank(name)) {
            wrapper.like(Coupon::getName, name);
        }
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        if (auditStatus != null) {
            wrapper.eq(Coupon::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(Coupon::getCreateTime);
        IPage<Coupon> result = page(page, wrapper);

        Page<CouponVO> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public List<CouponVO> listReceivable(Long shopId, Long userId) {
        // shopId=null 仅平台券；shopId>0 → 该店店铺券+分摊券 + 平台券
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                // 审核通过的券才可领（平台券/分摊券创建即 2；店铺券审核通过后 2）
                .eq(Coupon::getAuditStatus, 2)
                .in(Coupon::getFunderType, shopId != null && shopId > 0 ? new Integer[]{1, 2, 3} : new Integer[]{1})
                .le(Coupon::getReceiveStart, LocalDateTime.now())
                .ge(Coupon::getReceiveEnd, LocalDateTime.now())
                .apply("issued_count < total_count")
                .orderByDesc(Coupon::getCreateTime);
        if (shopId != null && shopId > 0) {
            // 店铺券/分摊券限定本店；平台券 shop_id IS NULL
            wrapper.and(w -> w.eq(Coupon::getShopId, shopId).or().isNull(Coupon::getShopId));
        }
        List<Coupon> coupons = list(wrapper);
        // 登录态下过滤已达到每人限领的券：不排除的话用户领完列表里还挂着，点了必失败
        if (userId != null && !coupons.isEmpty()) {
            Set<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toSet());
            Map<Long, Long> receivedCount = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getUserId, userId)
                            .in(UserCoupon::getCouponId, couponIds))
                    .stream()
                    .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
            coupons = coupons.stream()
                    .filter(c -> receivedCount.getOrDefault(c.getId(), 0L) <
                            (c.getPerUserLimit() == null ? 1 : c.getPerUserLimit()))
                    .collect(Collectors.toList());
        }
        return coupons.stream().map(this::toVO).collect(Collectors.toList());
    }

    private CouponVO toVO(Coupon coupon) {
        CouponVO vo = new CouponVO();
        vo.setId(coupon.getId());
        vo.setName(coupon.getName());
        vo.setFunderType(coupon.getFunderType());
        vo.setShopId(coupon.getShopId());
        vo.setDiscountType(coupon.getDiscountType());
        vo.setThresholdAmount(coupon.getThresholdAmount());
        vo.setDiscountValue(coupon.getDiscountValue());
        vo.setTotalCount(coupon.getTotalCount());
        vo.setIssuedCount(coupon.getIssuedCount());
        vo.setPerUserLimit(coupon.getPerUserLimit());
        vo.setReceiveStart(coupon.getReceiveStart());
        vo.setReceiveEnd(coupon.getReceiveEnd());
        vo.setValidType(coupon.getValidType());
        vo.setValidStart(coupon.getValidStart());
        vo.setValidEnd(coupon.getValidEnd());
        vo.setValidDays(coupon.getValidDays());
        vo.setStatus(coupon.getStatus());
        vo.setAuditStatus(coupon.getAuditStatus());
        vo.setRejectReason(coupon.getRejectReason());
        vo.setPlatformAmount(coupon.getPlatformAmount());
        vo.setShopAmount(coupon.getShopAmount());
        vo.setCreateTime(coupon.getCreateTime());
        return vo;
    }
}
