package com.degel.marketing.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 平台审核店铺券入参（复用 SPU 审核流交互语义）。
 */
@Data
public class AuditCouponVo {

    @NotNull(message = "券ID不能为空")
    private Long couponId;

    @NotNull(message = "审核结果不能为空")
    private Boolean passed;

    /** 驳回时必填 */
    private String rejectReason;
}
