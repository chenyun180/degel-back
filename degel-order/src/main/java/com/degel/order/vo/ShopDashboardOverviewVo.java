package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 店铺工作台看板核心指标（GMV/订单数/待办都在 degel_order 库，由本服务出数）。
 * 口径与平台看板一致：已支付且非取消（status IN (1,2,3,5)），售后退款不冲减。
 */
@Data
public class ShopDashboardOverviewVo {

    /** 今日 GMV */
    private BigDecimal todayGmv;

    /** 今日订单数 */
    private Integer todayOrderCount;

    /** 昨日 GMV */
    private BigDecimal yesterdayGmv;

    /** 昨日订单数 */
    private Integer yesterdayOrderCount;

    /** 本月 GMV */
    private BigDecimal monthGmv;

    /** 待发货订单数（已付款待发货 status=1） */
    private Integer pendingShipment;

    /** 待处理售后数（order_after_sale status=0） */
    private Integer pendingAfterSale;
}
