package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预览结算明细项 VO
 */
@Data
public class CartCheckItemVO {

    private Long cartId;

    private Long skuId;

    private Long spuId;

    private String spuName;

    private String skuSpec;

    private String skuImage;

    private BigDecimal price;

    private Integer quantity;

    private Integer stock;

    /**
     * 小计 = price × quantity
     */
    private BigDecimal subtotal;
}
