package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.common.core.R;
import com.degel.order.service.IOrderReviewService;
import com.degel.order.vo.ReviewCreateInnerVo;
import com.degel.order.vo.ReviewVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 内部接口（degel-app 专用，经 Feign 直连；网关侧 /order/inner/ 已列入 internal-urls 禁止外部访问）
 */
@RestController
@RequestMapping("/inner/review")
@RequiredArgsConstructor
public class InnerReviewController {

    private final IOrderReviewService orderReviewService;

    /**
     * 创建评价（订单已完成 status=3 后；一明细一评）
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody ReviewCreateInnerVo vo) {
        return R.ok(orderReviewService.createReview(vo));
    }

    /**
     * 商品维度评价分页（商品详情页，匿名可看的公共数据）
     */
    @GetMapping("/spu/{spuId}")
    public R<IPage<ReviewVo>> pageBySpu(
            @PathVariable Long spuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(orderReviewService.pageBySpu(spuId, page, pageSize));
    }

    /**
     * 我的评价分页
     */
    @GetMapping("/mine")
    public R<IPage<ReviewVo>> pageMine(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(orderReviewService.pageMine(userId, page, pageSize));
    }

    /**
     * 订单内已评价的明细 id（订单详情展示"已评价"标记用）
     */
    @GetMapping("/reviewed-item-ids")
    public R<List<Long>> reviewedItemIds(@RequestParam Long orderId, @RequestParam Long userId) {
        return R.ok(orderReviewService.reviewedItemIds(orderId, userId));
    }

    /**
     * 待评价订单分页（status=3 且存在未评明细；items 仅含未评明细）
     */
    @GetMapping("/pending-orders")
    public R<IPage<com.degel.order.vo.PendingOrderVo>> pendingOrders(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(orderReviewService.pagePendingOrders(userId, page, pageSize));
    }
}
