package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 平台提现审核（approve=true 通过并模拟打款 / false 驳回） */
@Data
public class WithdrawAuditVo {

    @NotNull(message = "提现单ID不能为空")
    private Long withdrawId;

    @NotNull(message = "审核动作不能为空")
    private Boolean approve;

    /** 驳回时必填（service 内校验） */
    private String auditRemark;
}
