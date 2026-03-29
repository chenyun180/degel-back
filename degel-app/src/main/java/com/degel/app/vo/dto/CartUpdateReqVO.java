package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 修改购物车数量请求 VO
 */
@Data
public class CartUpdateReqVO {

    /**
     * 数量，最小为 1
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    private Integer quantity;
}
