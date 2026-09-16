package com.degel.order.vo.inner;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 内部退款流水请求 VO（degel-order → degel-app POST /app/inner/pay/refund）。
 * 字段与 degel-app 的 InnerRefundReqVO 对齐（跨服务契约，服务间不共享该类）。
 */
@Data
public class PayRefundInnerVo {

    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotNull(message = "orderId不能为空")
    private Long orderId;

    @NotNull(message = "orderNo不能为空")
    private String orderNo;

    @NotNull(message = "amount不能为空")
    private BigDecimal amount;
}
