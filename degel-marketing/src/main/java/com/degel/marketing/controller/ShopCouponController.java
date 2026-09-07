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
 * 店铺券管理（经网关 /marketing/shop/**，店铺工作台）。
 *
 * 数据权限照 degel-product /spu/list 模式：网关注入 X-Shop-Id，controller 强制本店。
 * ⚠️ 该路径不在网关 admin-urls（不能拦 shop 角色），且 c_end token 可穿透网关校验
 * （known-issues 已记）——本 controller 对 X-Shop-Id<=0 一律拒绝（c_end 请求该 header
 * 缺省为 0，比 PlatformCouponController 的兜底更严）。根治需网关 AuthFilter 改造。
 */
@RestController
@RequestMapping("/shop/coupon")
@RequiredArgsConstructor
public class ShopCouponController {

    private final CouponService couponService;

    /** 创建店铺券（funder_type=2，强制归属 X-Shop-Id；总量≤5000、限领≤5 防滥用） */
    @PostMapping
    public R<CouponVO> create(
            @Valid @RequestBody CouponCreateVo vo,
            @RequestHeader("X-Shop-Id") Long shopId,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失，无权创建店铺券");
        }
        return R.ok(couponService.createShopCoupon(vo, shopId, userId));
    }

    /** 停发（校验归属：只能停自己店的券；已发出的券仍可用） */
    @PutMapping("/stop/{couponId}")
    public R<Void> stop(
            @PathVariable Long couponId,
            @RequestHeader("X-Shop-Id") Long shopId) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        couponService.stopCoupon(couponId, shopId);
        return R.ok();
    }

    /** 本店店铺券分页（强制 funder_type=2 + 本店） */
    @GetMapping("/list")
    public R<IPage<CouponVO>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestHeader("X-Shop-Id") Long shopId) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        return R.ok(couponService.pageShopCoupons(new Page<>(current, size), shopId, name, status));
    }
}
