package com.degel.admin.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ShopChangeAuditVo {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    @Size(max = 500, message = "审批意见不能超过500字")
    private String remark;
}
