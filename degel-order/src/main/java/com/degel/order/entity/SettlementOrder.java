package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺结算明细（T+7），一订单一行（uk_order_id 幂等）。
 * 基数口径：gross = pay + platform_subsidy + points_deduct（平台承担部分补给商家，店铺自担券除外）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_order")
public class SettlementOrder extends BaseEntity {

    private Long orderId;
    private String orderNo;
    private Long shopId;
    private Long userId;
    private BigDecimal payAmount;
    private BigDecimal platformSubsidy;
    private BigDecimal pointsDeduct;
    /** 结算基数（毛收入）= payAmount + platformSubsidy + pointsDeduct */
    private BigDecimal grossAmount;
    /** 生成时快照的全局佣金比例（%），改配置不影响已生成行 */
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    /** 入余额金额 = gross - commission */
    private BigDecimal netAmount;
    /** 0=待入账 1=已入账 2=已扣回（结算后整单退款） */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settleTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deductTime;
}
