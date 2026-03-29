package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表 VO（C端展示）
 */
@Data
public class AppSpuListVO {

    private Long spuId;

    private String name;

    private String mainImage;

    /**
     * 最低 SKU 价格
     */
    private BigDecimal minPrice;

    private Integer saleCount;
}
