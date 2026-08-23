package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 店铺流水排行项
 */
@Data
public class ShopGmvRankVo {

    private Long shopId;

    /** 已支付流水合计 */
    private BigDecimal gmv;

    /** 已支付订单数 */
    private Integer orderCount;
}
