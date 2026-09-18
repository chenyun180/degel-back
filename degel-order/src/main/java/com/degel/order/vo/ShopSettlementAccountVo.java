package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 店铺结算账户概览（店铺工作台余额卡片） */
@Data
public class ShopSettlementAccountVo {

    private Long shopId;
    /** 可提现余额（结算后退款扣回不足时可为负） */
    private BigDecimal balance;
    private BigDecimal totalSettled;
    private BigDecimal totalWithdrawn;
}
