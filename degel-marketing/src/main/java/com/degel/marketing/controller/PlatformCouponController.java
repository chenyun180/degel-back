package com.degel.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.marketing.service.CouponService;
import com.degel.marketing.vo.CouponCreateVo;
import com.degel.marketing.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 平台券管理（经网关 /marketing/platform/**，admin-urls 限平台角色）。
 *
 * 网关安全：AuthFilter 已将 c_end 令牌限制在 /app/**（2026-09-11 根治，见 known-issues），
 * 本 controller 不再依赖 X-Shop-Id==0 弱兜底作为唯一防线。
 */
@RestController
@RequestMapping("/platform/coupon")
@RequiredArgsConstructor
public class PlatformCouponController {

    private final CouponService couponService;

    @PostMapping
    public R<CouponVO> create(
            @Valid @RequestBody CouponCreateVo vo,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可创建平台券");
        }
        return R.ok(couponService.createPlatformCoupon(vo, userId));
    }

    /** 停发（已发出的券仍可用） */
    @PutMapping("/stop/{couponId}")
    public R<Void> stop(@PathVariable Long couponId) {
        couponService.stopCoupon(couponId, null);
        return R.ok();
    }

    /** 平台审核店铺券（通过→生效可领；驳回必填理由） */
    @PutMapping("/audit")
    public R<Void> audit(
            @Valid @RequestBody com.degel.marketing.vo.AuditCouponVo vo,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long auditorId) {
        couponService.auditCoupon(vo.getCouponId(), Boolean.TRUE.equals(vo.getPassed()), vo.getRejectReason());
        return R.ok();
    }

    @GetMapping("/list")
    public R<IPage<CouponVO>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus) {
        return R.ok(couponService.pagePlatformCoupons(new Page<>(current, size), name, status, auditStatus));
    }
}
