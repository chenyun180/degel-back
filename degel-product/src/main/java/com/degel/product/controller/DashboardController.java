package com.degel.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.product.service.DashboardService;
import com.degel.product.vo.PendingCountsVo;
import com.degel.product.vo.StockWarningVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺工作台看板（商品域部分：库存预警、库存预警数、待审核商品数）。
 * GMV/订单数/待发货/待处理售后等订单域指标在 degel-order /shop/dashboard/overview
 * ——曾在此提供 /today-overview 空壳端点（GMV 恒 0），已删除，前端直连 order。
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stock-warning")
    public R<IPage<StockWarningVo>> stockWarning(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(dashboardService.getStockWarnings(new Page<>(current, size), shopId));
    }

    @GetMapping("/pending-counts")
    public R<PendingCountsVo> pendingCounts(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(dashboardService.getPendingCounts(shopId));
    }
}
