package com.degel.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.entity.Coupon;
import com.degel.marketing.entity.UserCoupon;
import com.degel.marketing.mapper.UserCouponMapper;
import com.degel.marketing.service.CouponService;
import com.degel.marketing.service.UserCouponService;
import com.degel.marketing.vo.LockReqVO;
import com.degel.marketing.vo.LockRespVO;
import com.degel.marketing.vo.UserCouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon>
        implements UserCouponService {

    private final CouponService couponService;

    @Override
    public void receive(Long couponId, Long userId) {
        Coupon coupon = couponService.getById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }

        // 每人限领预校验。⚠️ 极限并发下可能超领个位数（校验-插入非原子）——MVP 口径：
        // 总量绝不超发由下面的原子 UPDATE 保证，超领个位数业务可接受，注释明示不引入分布式锁
        long myCount = count(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId)
                .eq(UserCoupon::getUserId, userId));
        if (myCount >= coupon.getPerUserLimit()) {
            throw new BusinessException("已达到每人限领数量");
        }

        // 防超发原子更新：status=1、可领时间内、余量充足，一步到位
        boolean updated = couponService.update(
                new LambdaUpdateWrapper<Coupon>()
                        .setSql("issued_count = issued_count + 1")
                        .eq(Coupon::getId, couponId)
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getReceiveStart, LocalDateTime.now())
                        .ge(Coupon::getReceiveEnd, LocalDateTime.now())
                        .apply("issued_count < total_count"));
        if (!updated) {
            throw new BusinessException("该券已领完或不在可领时间范围内");
        }

        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(0);
        // 领取时快照出资拆分：模板后续调整不影响已发券的记账口径
        uc.setPlatformAmount(coupon.getPlatformAmount());
        uc.setShopAmount(coupon.getShopAmount() != null ? coupon.getShopAmount() : BigDecimal.ZERO);
        uc.setReceiveTime(LocalDateTime.now());
        uc.setExpireTime(calcExpire(coupon));
        save(uc);
    }

    /** 按 valid_type 计算过期时间（领取时落库，展示/用券不再算） */
    private LocalDateTime calcExpire(Coupon coupon) {
        if (coupon.getValidType() == 1) {
            return coupon.getValidEnd();
        }
        int days = coupon.getValidDays() != null ? coupon.getValidDays() : 7;
        // 领取后N天：当天剩余时间算第1天（当天23:59:59 起算N-1整天，行业惯例）
        LocalDateTime base = LocalDateTime.now();
        return base.plusDays(days).withSecond(0).withNano(0);
    }

    @Override
    public List<UserCouponVO> mine(Long userId, Integer status) {
        // 惰性过期：把过期的未用券先置 3（定时任务兜底之外的实时修正）
        update(new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 3)
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, 0)
                .lt(UserCoupon::getExpireTime, LocalDateTime.now()));

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getReceiveTime);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        return toVOList(list(wrapper), null);
    }

    @Override
    public List<UserCouponVO> usable(Long userId, BigDecimal totalAmount, Long shopId) {
        // status∈{0未用,4已退回}、未过期；批量查模板（修 N+1，照 mine 的 toVOList 模式）
        List<UserCoupon> list = list(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .in(UserCoupon::getStatus, 0, 4)
                .gt(UserCoupon::getExpireTime, LocalDateTime.now())
                .orderByDesc(UserCoupon::getReceiveTime));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Coupon> couponMap = couponService.listByIds(
                        list.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));

        List<UserCouponVO> result = new ArrayList<>();
        for (UserCoupon uc : list) {
            Coupon coupon = couponMap.get(uc.getCouponId());
            if (coupon == null) {
                continue;
            }
            // 店铺券/分摊券只能抵本店子单：shopId 传入时过滤非本店券；未传时只出平台券
            if (coupon.getFunderType() != null && coupon.getFunderType() != 1) {
                if (shopId == null || !shopId.equals(coupon.getShopId())) {
                    continue;
                }
            }
            BigDecimal threshold = coupon.getThresholdAmount() != null ? coupon.getThresholdAmount() : BigDecimal.ZERO;
            if (totalAmount.compareTo(threshold) < 0) {
                continue;
            }
            result.add(toVO(uc, coupon, calcDiscount(coupon, totalAmount)));
        }
        return result;
    }

    /**
     * 本单优惠额：满减=减免额；无门槛=min(减免额, 订单额)；折扣=总额×(10-折数)/10（三期）。
     * 优惠后 pay_amount ≥ 0 天然满足（折扣封顶即订单额）。
     */
    private BigDecimal calcDiscount(Coupon coupon, BigDecimal totalAmount) {
        if (coupon.getDiscountType() != null && coupon.getDiscountType() == 2) {
            // 8.5折 → 优惠 = 总额 × 1.5/10，HALF_UP 2位
            return totalAmount.multiply(BigDecimal.TEN.subtract(coupon.getDiscountValue()))
                    .divide(BigDecimal.TEN, 2, BigDecimal.ROUND_HALF_UP);
        }
        BigDecimal discount = coupon.getDiscountValue();
        if (discount.compareTo(totalAmount) > 0) {
            return totalAmount;
        }
        return discount;
    }

    @Override
    public LockRespVO lock(LockReqVO req) {
        UserCoupon uc = getById(req.getUserCouponId());
        if (uc == null || !uc.getUserId().equals(req.getUserId())) {
            throw new BusinessException("用户券不存在");
        }
        Coupon coupon = couponService.getById(uc.getCouponId());
        if (coupon == null) {
            throw new BusinessException("券模板不存在");
        }
        // 店铺券/分摊券只能抵本店子单（req.shopId 为该子单归属店铺，平台券跳过）
        if (coupon.getFunderType() != null && coupon.getFunderType() != 1
                && (req.getShopId() == null || !req.getShopId().equals(coupon.getShopId()))) {
            throw new BusinessException("店铺券仅本店可用");
        }
        BigDecimal threshold = coupon.getThresholdAmount() != null ? coupon.getThresholdAmount() : BigDecimal.ZERO;
        if (req.getTotalAmount().compareTo(threshold) < 0) {
            throw new BusinessException("订单金额未满足用券门槛");
        }

        // 原子锁券：status∈{0,4} 且未过期 → 1。行数=0 即已被锁/已用/已过期
        boolean updated = update(new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getOrderNo, req.getOrderNo())
                .set(UserCoupon::getLockTime, LocalDateTime.now())
                .eq(UserCoupon::getId, req.getUserCouponId())
                .in(UserCoupon::getStatus, 0, 4)
                .gt(UserCoupon::getExpireTime, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("优惠券不可用（已使用/已锁定或已过期）");
        }

        // 锁券金额按下单时计算（封顶订单额），出资拆分按快照等比例缩放
        BigDecimal discount = calcDiscount(coupon, req.getTotalAmount());
        LockRespVO resp = new LockRespVO();
        resp.setDiscountAmount(discount);
        BigDecimal snapshotTotal = uc.getPlatformAmount().add(uc.getShopAmount());
        if (snapshotTotal.compareTo(BigDecimal.ZERO) > 0) {
            // 平台:店铺 按快照比例拆分（一期平台券 shopAmount=0，全落 platform）
            resp.setPlatformAmount(discount.multiply(uc.getPlatformAmount())
                    .divide(snapshotTotal, 2, BigDecimal.ROUND_HALF_UP));
            resp.setShopAmount(discount.subtract(resp.getPlatformAmount()));
        } else {
            resp.setPlatformAmount(discount);
            resp.setShopAmount(BigDecimal.ZERO);
        }
        resp.setCouponName(coupon.getName());
        return resp;
    }

    @Override
    public void unlock(String orderNo) {
        // 幂等：非锁定状态直接成功（重复取消/补偿重试场景）
        boolean updated = update(new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 0)
                .set(UserCoupon::getOrderNo, null)
                .set(UserCoupon::getLockTime, null)
                .eq(UserCoupon::getOrderNo, orderNo)
                .eq(UserCoupon::getStatus, 1));
        if (updated) {
            log.info("解锁用户券 orderNo={}", orderNo);
        }
    }

    @Override
    public void confirm(Long orderId, String orderNo) {
        boolean updated = update(new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 2)
                .set(UserCoupon::getOrderId, orderId)
                .set(UserCoupon::getUseTime, LocalDateTime.now())
                .eq(UserCoupon::getOrderNo, orderNo)
                .eq(UserCoupon::getStatus, 1));
        if (updated) {
            log.info("核销用户券 orderId={} orderNo={}", orderId, orderNo);
        }
    }

    @Override
    public void returnByOrder(Long orderId) {
        // 整单退：2→未过期?4:5（退回的券可再次使用；过期直接作废）
        UserCoupon uc = getOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, orderId)
                .eq(UserCoupon::getStatus, 2)
                .last("LIMIT 1"));
        if (uc == null) {
            return;
        }
        boolean expired = uc.getExpireTime().isBefore(LocalDateTime.now());
        UserCoupon update = new UserCoupon();
        update.setId(uc.getId());
        update.setStatus(expired ? 5 : 4);
        updateById(update);
        log.info("整单退回用户券 id={} → status={}", uc.getId(), expired ? 5 : 4);
    }

    private List<UserCouponVO> toVOList(List<UserCoupon> list, BigDecimal discountAmount) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> couponIds = list.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        Map<Long, Coupon> couponMap = couponService.listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));
        return list.stream()
                .map(uc -> toVO(uc, couponMap.get(uc.getCouponId()), discountAmount))
                .collect(Collectors.toList());
    }

    private UserCouponVO toVO(UserCoupon uc, Coupon coupon, BigDecimal discountAmount) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(uc.getCouponId());
        vo.setStatus(uc.getStatus());
        if (coupon != null) {
            vo.setCouponName(coupon.getName());
            vo.setDiscountType(coupon.getDiscountType());
            vo.setThresholdAmount(coupon.getThresholdAmount());
            vo.setDiscountValue(coupon.getDiscountValue());
        }
        vo.setDiscountAmount(discountAmount);
        vo.setPlatformAmount(uc.getPlatformAmount());
        vo.setShopAmount(uc.getShopAmount());
        vo.setReceiveTime(uc.getReceiveTime());
        vo.setExpireTime(uc.getExpireTime());
        vo.setUseTime(uc.getUseTime());
        return vo;
    }
}
