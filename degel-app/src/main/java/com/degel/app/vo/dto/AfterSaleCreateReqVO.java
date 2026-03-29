package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 申请售后/退款请求 VO
 */
@Data
public class AfterSaleCreateReqVO {

    /** 订单ID */
    @NotNull(message = "请选择订单")
    private Long orderId;

    /** 申请原因 */
    @NotBlank(message = "请填写申请原因")
    private String reason;
}
