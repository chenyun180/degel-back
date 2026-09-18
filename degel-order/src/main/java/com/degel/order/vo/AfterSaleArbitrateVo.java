package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 平台仲裁判定（status=6 的售后单） */
@Data
public class AfterSaleArbitrateVo {

    @NotNull(message = "售后单ID不能为空")
    private Long afterSaleId;

    /** true=支持用户（执行退款）false=维持拒绝（终态 7） */
    @NotNull(message = "判定结果不能为空")
    private Boolean supportUser;

    @NotBlank(message = "仲裁意见必填")
    private String remark;
}
