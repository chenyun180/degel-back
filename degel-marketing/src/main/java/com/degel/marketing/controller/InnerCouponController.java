package com.degel.marketing.controller;

import com.degel.common.core.R;
import com.degel.marketing.service.CouponService;
import com.degel.marketing.service.UserCouponService;
import com.degel.marketing.vo.ConfirmReqVO;
import com.degel.marketing.vo.LockReqVO;
import com.degel.marketing.vo.LockRespVO;
import com.degel.marketing.vo.OrderRefVO;
import com.degel.marketing.vo.UserCouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * 服务间内部接口（/inner/coupon/**，InnerTokenFilter 校验 X-Inner-Token，
 * 网关 internal-urls 拒绝外部经网关访问，Feign 经 Nacos 直连）。
 *
 * 全部幂等：unlock/confirm 状态不匹配时返回成功（防重试场景误报）；
 * lock 失败抛 BusinessException（R.code=500，调用方下单阻断）。
 */
@RestController
@RequestMapping("/inner/coupon")
@RequiredArgsConstructor
public class InnerCouponController {

    private final UserCouponService userCouponService;
    private final CouponService couponService;

    /** C 端可领列表（degel-app BFF 转发）。shopId 可空：null=仅平台券；传=该店券+平台券；userId 可空=未登录不过滤限领 */
    @GetMapping("/receivable")
    public R<List<com.degel.marketing.vo.CouponVO>> receivable(
            @RequestParam(value = "shopId", required = false) Long shopId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return R.ok(couponService.listReceivable(shopId, userId));
    }

    /** C 端我的券（degel-app BFF 转发） */
    @GetMapping("/mine")
    public R<List<UserCouponVO>> mine(@RequestParam("userId") Long userId,
                                      @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(userCouponService.mine(userId, status));
    }

    /** C 端下单可用券（degel-app BFF 转发）。shopId 可空：null=仅平台券；传=平台券+该店店铺券 */
    @GetMapping("/usable")
    public R<List<UserCouponVO>> usable(@RequestParam("userId") Long userId,
                                        @RequestParam("totalAmount") BigDecimal totalAmount,
                                        @RequestParam(value = "shopId", required = false) Long shopId) {
        return R.ok(userCouponService.usable(userId, totalAmount, shopId));
    }

    /** C 端领券（degel-app BFF 转发；防超发原子更新在 service 内） */
    @PostMapping("/receive/{couponId}")
    public R<Void> receive(@PathVariable Long couponId, @RequestParam("userId") Long userId) {
        userCouponService.receive(couponId, userId);
        return R.ok();
    }

    /** 下单锁券：0/4→1，返回优惠额与出资拆分 */
    @PostMapping("/lock")
    public R<LockRespVO> lock(@Valid @RequestBody LockReqVO req) {
        return R.ok(userCouponService.lock(req));
    }

    /** 释放：订单创建失败补偿 / 未支付取消 */
    @PostMapping("/unlock")
    public R<Void> unlock(@RequestBody OrderRefVO req) {
        userCouponService.unlock(req.getOrderNo());
        return R.ok();
    }

    /** 核销：支付成功（回填 orderId） */
    @PostMapping("/confirm")
    public R<Void> confirm(@Valid @RequestBody ConfirmReqVO req) {
        userCouponService.confirm(req.getOrderId(), req.getOrderNo());
        return R.ok();
    }

    /** 整单退回：售后审核同意（未过期→4 可再用，过期→5 作废） */
    @PostMapping("/return")
    public R<Void> returnCoupon(@RequestBody OrderRefVO req) {
        if (req.getOrderId() == null) {
            return R.fail("orderId 不能为空");
        }
        userCouponService.returnByOrder(req.getOrderId());
        return R.ok();
    }
}
