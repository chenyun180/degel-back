package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车列表项 VO
 */
@Data
public class CartItemVO {

    private Long id;

    private Long spuId;

    private Long skuId;

    private String spuName;

    /**
     * SKU 规格描述，如 "颜色:红 / 尺码:XL"
     */
    private String skuSpec;

    private String skuImage;

    private BigDecimal price;

    private Integer quantity;

    private Integer stock;

    /**
     * 小计 = price × quantity（后端计算）
     */
    private BigDecimal subtotal;

    /**
     * 是否失效（商品已下架则 true）
     */
    private Boolean invalid;
}
