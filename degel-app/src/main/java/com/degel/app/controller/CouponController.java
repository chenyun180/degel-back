package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.feign.MarketingFeignClient;
import com.degel.app.vo.dto.AppCouponVO;
import com.degel.app.vo.dto.AppUserCouponVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * C 端优惠券入口（AppSecurityFilter 登录态）。
 * marketing 查询降级时返回空列表（券入口不可用不阻断浏览/下单），
 * 领券/锁券失败原样透出（R.code!=200 由前端 toast）。
 */
@RestController
@RequestMapping("/app/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final MarketingFeignClient marketingFeignClient;

    /** 可领券列表（首页/详情页领券入口）。shopId 可空：null=平台券；传=该店券+平台券；登录态过滤已达限领的券 */
    @GetMapping("/list")
    public R<List<AppCouponVO>> list(@RequestParam(value = "shopId", required = false) Long shopId) {
        Long userId = UserContext.getUserId();
        R<List<AppCouponVO>> resp = marketingFeignClient.listReceivable(shopId, userId);
        return resp != null && resp.getCode() == 200
                ? R.ok(resp.getData())
                : R.ok(Collections.emptyList());
    }

    /** 领取 */
    @PostMapping("/receive/{couponId}")
    public R<Void> receive(@PathVariable Long couponId) {
        R<Void> resp = marketingFeignClient.receive(couponId, UserContext.getUserId());
        if (resp == null || resp.getCode() != 200) {
            return R.fail(resp != null ? resp.getMsg() : "领券失败，请稍后重试");
        }
        return R.ok();
    }

    /** 我的券（status 可选：0未用 2已核销 3已过期 4已退回） */
    @GetMapping("/mine")
    public R<List<AppUserCouponVO>> mine(
            @RequestParam(value = "status", required = false) Integer status) {
        R<List<AppUserCouponVO>> resp = marketingFeignClient.mine(UserContext.getUserId(), status);
        return resp != null && resp.getCode() == 200
                ? R.ok(resp.getData())
                : R.ok(Collections.emptyList());
    }

    /** 下单可用券（结算页选券）。shopId 可空：null=平台券；传=平台券+该店店铺券（按店子单口径） */
    @GetMapping("/usable")
    public R<List<AppUserCouponVO>> usable(@RequestParam("totalAmount") BigDecimal totalAmount,
                                           @RequestParam(value = "shopId", required = false) Long shopId) {
        R<List<AppUserCouponVO>> resp = marketingFeignClient.usable(UserContext.getUserId(), totalAmount, shopId);
        return resp != null && resp.getCode() == 200
                ? R.ok(resp.getData())
                : R.ok(Collections.emptyList());
    }
}
