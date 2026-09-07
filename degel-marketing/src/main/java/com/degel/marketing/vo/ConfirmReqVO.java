package com.degel.marketing.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * confirm（核销）入参：orderId + orderNo 双定位。
 * orderId 落库后才有，confirm 由支付成功回调触发，此时两者都已知。
 */
@Data
public class ConfirmReqVO {

    @NotNull(message = "订单id不能为空")
    private Long orderId;

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
