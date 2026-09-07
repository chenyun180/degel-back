package com.degel.order.controller;

import com.degel.common.core.R;
import com.degel.order.mapper.OrderStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 店铺工作台统计（补贴数据在 degel_order 库，故由本服务出数——店铺看板其余指标
 * 在 degel-product /dashboard（前端双接口并行，先例一致））。
 * 口径：已支付且非取消（status IN (1,2,3,5)）；售后不冲减，数值略偏高（known-issues 已记）。
 */
@RestController
@RequestMapping("/shop/dashboard")
@RequiredArgsConstructor
public class ShopDashboardController {

    private final OrderStatsMapper orderStatsMapper;

    /** 本月店铺补贴（shop_subsidy 合计） */
    @GetMapping("/subsidy-summary")
    public R<Map<String, BigDecimal>> subsidySummary(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        Map<String, BigDecimal> data = new HashMap<>(1);
        data.put("monthShopSubsidy", orderStatsMapper.sumMonthShopSubsidy(shopId));
        return R.ok(data);
    }
}
