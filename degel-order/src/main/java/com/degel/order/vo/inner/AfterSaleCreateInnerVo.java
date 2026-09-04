package com.degel.order.vo.inner;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端创建售后单内部请求（与 degel-app AfterSaleCreateInnerReqVO 字段对齐）
 */
@Data
public class AfterSaleCreateInnerVo {

    private Long orderId;
    private Long userId;
    private Long shopId;
    private Integer type;
    private String reason;
    private BigDecimal refundAmount;
}
