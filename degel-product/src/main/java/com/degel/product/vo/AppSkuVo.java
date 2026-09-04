package com.degel.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端/BFF SKU 视图（与 degel-app 的 ProductSkuVO 字段对齐）
 * spuName/skuName 由服务层关联 SPU 填充（degel-app 下单快照依赖）
 */
@Data
public class AppSkuVo {

    private Long id;
    private Long spuId;
    private String skuCode;
    private String skuName;
    private String spuName;
    private String specData;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String image;
    private Integer status;
}
