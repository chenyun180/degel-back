package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台工作台总览
 */
@Data
public class PlatformDashboardOverviewVo {

    /** 累计总流水（已支付订单实付金额合计，含售后中，退款不冲减） */
    private BigDecimal totalGmv;

    /** 今日流水 */
    private BigDecimal todayGmv;

    /** 今日订单数 */
    private Integer todayOrderCount;

    /** 待发货订单数（已付款待发货） */
    private Integer pendingShipCount;

    /** 店铺流水 TOP5 */
    private List<ShopGmvRankVo> shopTop5;

    /** 畅销商品 TOP5（按销售额，含所属店铺） */
    private List<ProductGmvRankVo> productTop5;
}
