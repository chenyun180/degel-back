package com.degel.app.vo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 申请售后内部请求 VO（传给 degel-order）
 */
@Data
public class AfterSaleCreateInnerReqVO {

    private Long orderId;
    private Long userId;
    private Long shopId;
    /** 类型：1=仅退款 */
    private Integer type;
    /** 原因 */
    private String reason;
    /** 退款金额 */
    private BigDecimal refundAmount;
}
