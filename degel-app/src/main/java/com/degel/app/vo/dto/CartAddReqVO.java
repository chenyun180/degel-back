package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 加入购物车请求 VO
 */
@Data
public class CartAddReqVO {

    /**
     * SKU ID
     */
    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    /**
     * 数量
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    private Integer quantity;
}
