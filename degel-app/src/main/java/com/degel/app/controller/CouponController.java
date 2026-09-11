package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.feign.MarketingFeignClient;
import com.degel.app.vo.dto.AppCouponVO;
import com.degel.app.vo.dto.AppUserCouponVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端优惠券入口（AppSecurityFilter 登录态）。
 * marketing 查询失败透传错误（R.code!=200 由前端 toast）——不伪装成空列表，
 * 否则用户会把服务故障当成"没券可领"（2026-09 排查领券中心空白时定位的坑）。
 */
@RestController
@RequestMapping("/app/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final MarketingFeignClient marketingFeignClient;

    /** 可领券列表（首页/详情页领券入口）。shopId 可空：null=平台券；传=该店券+平台券；登录态过滤已达限领的券。
     *  下游失败不能伪装成空列表返回 200——前端会把"服务故障"当"没券可领"展示 */
    @GetMapping("/list")
    public R<List<AppCouponVO>> list(@RequestParam(value = "shopId", required = false) Long shopId) {
        Long userId = UserContext.getUserId();
        R<List<AppCouponVO>> resp = marketingFeignClient.listReceivable(shopId, userId);
        if (resp == null) {
            return R.fail("券服务暂不可用，请稍后重试");
        }
        return resp.getCode() == 200 ? R.ok(resp.getData()) : R.fail(resp.getMsg());
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

    /** 我的券（status 可选：0未用 2已核销 3已过期 4已退回）。下游失败透传错误，不伪装成空列表 */
    @GetMapping("/mine")
    public R<List<AppUserCouponVO>> mine(
            @RequestParam(value = "status", required = false) Integer status) {
        R<List<AppUserCouponVO>> resp = marketingFeignClient.mine(UserContext.getUserId(), status);
        if (resp == null) {
            return R.fail("券服务暂不可用，请稍后重试");
        }
        return resp.getCode() == 200 ? R.ok(resp.getData()) : R.fail(resp.getMsg());
    }

    /** 下单可用券（结算页选券）。shopId 可空：null=平台券；传=平台券+该店店铺券（按店子单口径）。失败透传：宁可提示重试，不可让用户误以为无券可用 */
    @GetMapping("/usable")
    public R<List<AppUserCouponVO>> usable(@RequestParam("totalAmount") BigDecimal totalAmount,
                                           @RequestParam(value = "shopId", required = false) Long shopId) {
        R<List<AppUserCouponVO>> resp = marketingFeignClient.usable(UserContext.getUserId(), totalAmount, shopId);
        if (resp == null) {
            return R.fail("券服务暂不可用，请稍后重试");
        }
        return resp.getCode() == 200 ? R.ok(resp.getData()) : R.fail(resp.getMsg());
    }
}
