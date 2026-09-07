package com.degel.app.feign.fallback;

import com.degel.app.feign.MarketingFeignClient;
import com.degel.app.vo.dto.AppCouponVO;
import com.degel.app.vo.dto.AppUserCouponVO;
import com.degel.app.vo.dto.CouponConfirmReqVO;
import com.degel.app.vo.dto.CouponLockReqVO;
import com.degel.app.vo.dto.CouponLockRespVO;
import com.degel.app.vo.dto.CouponOrderRefVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * MarketingFeignClient 降级实现。
 * lock/unlock/confirm 降级返回 fail 由调用方处理（下单阻断 / log 重试口径）。
 */
@Slf4j
@Component
public class MarketingFeignFallback implements MarketingFeignClient {

    @Override
    public R<List<AppCouponVO>> listReceivable(Long shopId, Long userId) {
        log.error("[MarketingFeignFallback] listReceivable 降级");
        return R.fail(50001, "营销服务暂不可用");
    }

    @Override
    public R<List<AppUserCouponVO>> mine(Long userId, Integer status) {
        log.error("[MarketingFeignFallback] mine userId={} 降级", userId);
        return R.fail(50001, "营销服务暂不可用");
    }

    @Override
    public R<List<AppUserCouponVO>> usable(Long userId, BigDecimal totalAmount, Long shopId) {
        log.error("[MarketingFeignFallback] usable userId={} 降级", userId);
        return R.fail(50001, "营销服务暂不可用");
    }

    @Override
    public R<Void> receive(Long couponId, Long userId) {
        log.error("[MarketingFeignFallback] receive couponId={} 降级", couponId);
        return R.fail(50001, "营销服务暂不可用，请稍后重试");
    }

    @Override
    public R<CouponLockRespVO> lock(CouponLockReqVO reqVO) {
        log.error("[MarketingFeignFallback] lock userCouponId={} 降级", reqVO.getUserCouponId());
        return R.fail(50001, "优惠券服务暂不可用，请移除优惠券后重试");
    }

    @Override
    public R<Void> unlock(CouponOrderRefVO reqVO) {
        // unlock 是补偿路径，降级只能 log——漏解锁由 marketing 60 分钟僵尸锁任务兜底
        log.error("[MarketingFeignFallback] unlock orderNo={} 降级（等 60 分钟兜底任务释放）", reqVO.getOrderNo());
        return R.fail(50001, "unlock 降级");
    }

    @Override
    public R<Void> confirm(CouponConfirmReqVO reqVO) {
        // confirm 失败不阻断支付主流程（调用方 log 后依赖幂等重试/人工）
        log.error("[MarketingFeignFallback] confirm orderId={} 降级", reqVO.getOrderId());
        return R.fail(50001, "confirm 降级");
    }
}
