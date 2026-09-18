package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 商家提现申请 */
@Data
public class WithdrawApplyVo {

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.01", message = "提现金额必须大于 0")
    private BigDecimal amount;

    private String applyRemark;
}
