package com.degel.marketing.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 锁券响应：本单优惠额与出资拆分（订单侧记账依据）。
 */
@Data
public class LockRespVO {

    /** 本单优惠总额（= platformAmount + shopAmount；满减封顶后） */
    private BigDecimal discountAmount;

    private BigDecimal platformAmount;
    private BigDecimal shopAmount;

    /** 券名（订单快照展示用） */
    private String couponName;
}
