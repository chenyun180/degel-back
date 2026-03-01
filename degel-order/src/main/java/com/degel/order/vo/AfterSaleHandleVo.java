package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AfterSaleHandleVo {

    @NotNull(message = "售后ID不能为空")
    private Long afterSaleId;

    @NotBlank(message = "操作不能为空")
    private String action;

    private String merchantRemark;
}
