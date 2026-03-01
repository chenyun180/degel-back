package com.degel.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.product.service.DashboardService;
import com.degel.product.vo.DashboardOverviewVo;
import com.degel.product.vo.PendingCountsVo;
import com.degel.product.vo.StockWarningVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/today-overview")
    public R<DashboardOverviewVo> todayOverview(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(dashboardService.getTodayOverview(shopId));
    }

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
