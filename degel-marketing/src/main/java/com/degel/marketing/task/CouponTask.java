package com.degel.marketing.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.marketing.entity.UserCoupon;
import com.degel.marketing.service.UserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户券双定时任务（照 OrderTimeoutCancelTask 模式：失败只 log 不抛）。
 *
 * 1. 过期：0 未用 → 3 已过期（惰性校验之外的批量兜底）
 * 2. 僵尸锁释放：1 已锁定 且 lock_time 超过 60 分钟 → 0 未用。
 *    ⚠️ 阈值必须 > 订单未支付超时窗口（30 分钟）：保证释放时订单必已取消，
 *    只兜「unlock 补偿调用丢失」的场景，绝不会释放仍可支付的订单的券（否则会出现
 *    记账已优惠但券回流池子的双花）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponTask {

    private final UserCouponService userCouponService;

    /** 每小时整点后 5 分：批量过期 */
    @Scheduled(cron = "0 5 * * * ?")
    public void expireCoupons() {
        try {
            boolean updated = userCouponService.update(new LambdaUpdateWrapper<UserCoupon>()
                    .set(UserCoupon::getStatus, 3)
                    .eq(UserCoupon::getStatus, 0)
                    .lt(UserCoupon::getExpireTime, java.time.LocalDateTime.now()));
            if (updated) {
                log.info("[CouponTask] 批量过期完成");
            }
        } catch (Exception e) {
            log.error("[CouponTask] 批量过期失败", e);
        }
    }

    /** 每 10 分钟：释放锁券超 60 分钟的僵尸锁 */
    @Scheduled(cron = "20 0/10 * * * ?")
    public void releaseStaleLocks() {
        try {
            boolean updated = userCouponService.update(new LambdaUpdateWrapper<UserCoupon>()
                    .set(UserCoupon::getStatus, 0)
                    .set(UserCoupon::getOrderNo, null)
                    .set(UserCoupon::getLockTime, null)
                    .eq(UserCoupon::getStatus, 1)
                    .lt(UserCoupon::getLockTime,
                            java.time.LocalDateTime.now().minusMinutes(60)));
            if (updated) {
                log.warn("[CouponTask] 释放僵尸锁券（unlock 补偿曾丢失）");
            }
        } catch (Exception e) {
            log.error("[CouponTask] 僵尸锁释放失败", e);
        }
    }
}
