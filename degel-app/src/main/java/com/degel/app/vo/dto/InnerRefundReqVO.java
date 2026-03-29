package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 内部退款请求 VO（degel-order 调用 degel-app 的内部退款接口）
 */
@Data
public class InnerRefundReqVO {

    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotNull(message = "orderId不能为空")
    private Long orderId;

    @NotNull(message = "orderNo不能为空")
    private String orderNo;

    @NotNull(message = "amount不能为空")
    private BigDecimal amount;
}
