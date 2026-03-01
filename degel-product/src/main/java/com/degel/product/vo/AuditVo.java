package com.degel.product.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AuditVo {

    @NotNull(message = "商品ID不能为空")
    private Long spuId;

    @NotNull(message = "审核结果不能为空")
    private Boolean passed;

    private String rejectReason;
}
