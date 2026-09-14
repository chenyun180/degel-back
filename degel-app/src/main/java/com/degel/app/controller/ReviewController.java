package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.service.ReviewService;
import com.degel.app.vo.ReviewCreateReqVO;
import com.degel.app.vo.ReviewVO;
import com.degel.app.context.UserContext;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C-07 评价：
 *   POST /app/review                      创建评价（登录）
 *   GET  /app/review/list?spuId=          商品评价分页（匿名，挂 AppSecurityFilter 公开前缀）
 *   GET  /app/review/mine                 我的评价分页（登录）
 *   GET  /app/review/reviewed-item-ids    订单内已评价明细（登录）
 */
@RestController
@RequestMapping("/app/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public R<Void> create(@Validated @RequestBody ReviewCreateReqVO reqVO) {
        reviewService.createReview(UserContext.getUserId(), reqVO);
        return R.ok();
    }

    @GetMapping("/list")
    public R<IPage<ReviewVO>> listBySpu(
            @RequestParam Long spuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.pageBySpu(spuId, page, pageSize));
    }

    @GetMapping("/mine")
    public R<IPage<ReviewVO>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.pageMine(UserContext.getUserId(), page, pageSize));
    }

    @GetMapping("/reviewed-item-ids")
    public R<List<Long>> reviewedItemIds(@RequestParam Long orderId) {
        return R.ok(reviewService.reviewedItemIds(orderId, UserContext.getUserId()));
    }

    /**
     * 待评价订单分页（订单列表"待评价"Tab；items 仅含未评明细）
     */
    @GetMapping("/pending")
    public R<IPage<com.degel.app.vo.PendingOrderVO>> pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.pagePendingOrders(UserContext.getUserId(), page, pageSize));
    }
}
