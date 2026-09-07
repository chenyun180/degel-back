package com.degel.app.feign;

import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.MarketingFeignFallback;
import com.degel.app.vo.dto.*;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营销服务 Feign 客户端（优惠券）
 * 直连 lb://degel-marketing，不带网关 /marketing 前缀（Feign 直连端点即控制器原始路径）。
 *
 * 降级语义（重要）：
 * - lock 失败必须让调用方下单阻断（返回 R.fail，OrderServiceImpl 判 code!=200 抛异常）——
 *   券不可用不能静默原价下单
 * - 查询类（receivable/mine/usable）降级返回 fail，BFF 层转空列表给前端（可接受）
 */
@FeignClient(name = "degel-marketing", path = "/inner/coupon", configuration = FeignConfig.class, fallback = MarketingFeignFallback.class)
public interface MarketingFeignClient {

    /** C 端可领券列表。shopId 可空：null=仅平台券；传=该店券+平台券；userId 可空=未登录不过滤限领 */
    @GetMapping("/receivable")
    R<List<AppCouponVO>> listReceivable(@RequestParam(value = "shopId", required = false) Long shopId,
                                        @RequestParam(value = "userId", required = false) Long userId);

    /** C 端我的券 */
    @GetMapping("/mine")
    R<List<AppUserCouponVO>> mine(@RequestParam("userId") Long userId,
                                  @RequestParam(value = "status", required = false) Integer status);

    /** C 端下单可用券。shopId 可空：null=仅平台券；传=平台券+该店店铺券 */
    @GetMapping("/usable")
    R<List<AppUserCouponVO>> usable(@RequestParam("userId") Long userId,
                                    @RequestParam("totalAmount") BigDecimal totalAmount,
                                    @RequestParam(value = "shopId", required = false) Long shopId);

    /** 领券 */
    @PostMapping("/receive/{couponId}")
    R<Void> receive(@PathVariable("couponId") Long couponId, @RequestParam("userId") Long userId);

    /** 下单锁券 */
    @PostMapping("/lock")
    R<CouponLockRespVO> lock(@RequestBody CouponLockReqVO reqVO);

    /** 释放（下单失败补偿 / 未支付取消） */
    @PostMapping("/unlock")
    R<Void> unlock(@RequestBody CouponOrderRefVO reqVO);

    /** 核销（支付成功） */
    @PostMapping("/confirm")
    R<Void> confirm(@RequestBody CouponConfirmReqVO reqVO);
}
