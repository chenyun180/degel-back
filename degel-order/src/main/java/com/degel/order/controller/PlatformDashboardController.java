package com.degel.order.controller;

import com.degel.common.core.R;
import com.degel.order.service.PlatformDashboardService;
import com.degel.order.vo.DailyGmvVo;
import com.degel.order.vo.PlatformDashboardOverviewVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台工作台数据看板（全平台维度）
 * 鉴权：网关 degel.security.admin-urls 的 "GET:/order/platform/dashboard" 限定平台管理员
 */
@RestController
@RequestMapping("/platform/dashboard")
@RequiredArgsConstructor
public class PlatformDashboardController {

    private final PlatformDashboardService platformDashboardService;

    @GetMapping("/overview")
    public R<PlatformDashboardOverviewVo> overview() {
        return R.ok(platformDashboardService.getOverview());
    }

    @GetMapping("/trend")
    public R<List<DailyGmvVo>> trend(@RequestParam(defaultValue = "30") Integer days) {
        return R.ok(platformDashboardService.getTrend(days));
    }
}
