package com.degel.marketing.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 积分抵扣试算（C 端确认页开关展示） */
@Data
public class PointsPreviewVO {

    /** 当前可用积分余额 */
    private Integer available;

    /** 本单最多可用积分数（整百，受余额与抵扣上限双重约束） */
    private Integer maxRedeemPoints;

    /** maxRedeemPoints 对应抵扣金额 */
    private BigDecimal maxDeductAmount;

    /** 换算说明，如"100积分=1元，最多抵订单金额的20%" */
    private String rule;
}
