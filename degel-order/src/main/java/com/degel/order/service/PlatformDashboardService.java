package com.degel.order.service;

import com.degel.order.mapper.OrderStatsMapper;
import com.degel.order.vo.DailyGmvVo;
import com.degel.order.vo.PlatformDashboardOverviewVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台工作台统计（全平台维度，接口经网关 admin-urls 限定平台管理员访问）
 */
@Service
@RequiredArgsConstructor
public class PlatformDashboardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderStatsMapper orderStatsMapper;

    public PlatformDashboardOverviewVo getOverview() {
        PlatformDashboardOverviewVo vo = new PlatformDashboardOverviewVo();
        vo.setTotalGmv(orderStatsMapper.sumTotalGmv());
        vo.setTodayGmv(orderStatsMapper.sumTodayGmv());
        vo.setTodayOrderCount(orderStatsMapper.countTodayOrders());
        vo.setPendingShipCount(orderStatsMapper.countPendingShip());
        vo.setShopTop5(orderStatsMapper.selectShopGmvTop5());
        vo.setProductTop5(orderStatsMapper.selectProductGmvTop5());
        return vo;
    }

    /**
     * 近 days 天按日流水，缺日补零保证 X 轴连续。
     * days 钳制到 [7, 90]。
     */
    public List<DailyGmvVo> getTrend(Integer days) {
        int clamped = Math.min(Math.max(days == null ? 30 : days, 7), 90);

        List<DailyGmvVo> rows = orderStatsMapper.selectDailyGmv(clamped);
        Map<String, DailyGmvVo> byDate = new HashMap<>();
        for (DailyGmvVo row : rows) {
            byDate.put(row.getDate(), row);
        }

        List<DailyGmvVo> result = new ArrayList<>(clamped);
        LocalDate today = LocalDate.now();
        for (int i = clamped - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(DATE_FMT);
            DailyGmvVo row = byDate.get(date);
            if (row != null) {
                result.add(row);
            } else {
                result.add(new DailyGmvVo(date, BigDecimal.ZERO, 0));
            }
        }
        return result;
    }
}
