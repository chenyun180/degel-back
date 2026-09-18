package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商家结算账户。余额可为负：仅退款扣回路径允许扣成负数（欠款），
 * 提现路径用 WHERE balance>=? 守卫，永不产生负余额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_account")
public class SettlementAccount extends BaseEntity {

    private Long shopId;
    private BigDecimal balance;
    /** 累计结算入账（审计口径，退款扣回不冲减此列，冲减看流水 biz_type=3） */
    private BigDecimal totalSettled;
    private BigDecimal totalWithdrawn;
}
