package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 结算账户流水。uk_biz(biz_type, biz_no) 是全链路幂等核心；
 * balance_after 快照用于对账自校验（SUM(amount) 应等于当前余额）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_account_log")
public class SettlementAccountLog extends BaseEntity {

    private Long shopId;
    /** 1=结算入账 2=提现支出 3=退款扣回 */
    private Integer bizType;
    /** 1→结算明细id 2→提现单id 3→售后单id */
    private Long bizNo;
    /** 有符号：入账为正，提现/扣回为负 */
    private BigDecimal amount;
    /** 变动后余额快照 */
    private BigDecimal balanceAfter;
    private String remark;
}
