package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.common.core.R;
import com.degel.order.service.IOrderReviewService;
import com.degel.order.vo.ReviewReplyVo;
import com.degel.order.vo.ReviewVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 店铺评价管理（经网关 /order/review/**，X-Shop-Id 由网关 AuthFilter 注入）。
 * c_end 令牌已被网关限制在 /app/**，本 controller 天然与 C 端隔离。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ShopReviewController {

    private final IOrderReviewService orderReviewService;

    /**
     * 本店评价分页（店铺工作台）
     */
    @GetMapping("/list")
    public R<IPage<ReviewVo>> list(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        if (shopId == null || shopId <= 0) {
            return R.fail("仅店铺账号可查看店铺评价");
        }
        return R.ok(orderReviewService.pageByShop(shopId, page, pageSize));
    }

    /**
     * 商家回复评价（仅本店；一条评价仅回复一次）
     */
    @PutMapping("/reply")
    public R<Void> reply(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @Valid @RequestBody ReviewReplyVo vo) {
        if (shopId == null || shopId <= 0) {
            return R.fail("仅店铺账号可回复评价");
        }
        orderReviewService.reply(shopId, vo);
        return R.ok();
    }
}
