package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 交易配置读写（佣金比例 + 售后窗口） */
@Data
public class SettlementConfigVo {

    /** 全局佣金比例（%），0~100，两位小数 */
    private BigDecimal commissionRate;

    /** 售后窗口（天）：确认收货后 N 天内可申请售后，1~90 */
    private Integer aftersaleDays;
}
