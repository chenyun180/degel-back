package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 平台资金总览（纯聚合，无资金动作）。
 * 口径：
 * - 佣金收入/补贴支出：已入账结算明细（status=1；已扣回的不算）
 * - 净现金流 = 用户支付净额 − 平台对商家负债合计（负=平台垫资，补贴所致属预期）
 */
@Data
public class PlatformSettlementOverviewVo {

    /** 累计佣金收入 */
    private BigDecimal commissionIncome;
    /** 累计平台承担补贴支出（平台券+积分抵扣） */
    private BigDecimal subsidyPaid;
    /** 累计提现打款 */
    private BigDecimal withdrawPaid;
    /** 商家欠款（负余额合计，正数展示） */
    private BigDecimal shopDebt;
    /** 平台对商家负债合计（全部商家余额之和，可为负） */
    private BigDecimal shopBalanceTotal;
    /** 用户支付净额（支付-退款，来自 degel-app 流水） */
    private BigDecimal userPayNet;
    /** 平台净现金流 = 用户支付净额 − 商家余额合计 */
    private BigDecimal netCashFlow;
}
