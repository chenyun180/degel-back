package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 SKU VO（内部 Feign 调用用，含库存/状态字段）
 */
@Data
public class ProductSkuVO {

    private Long id;

    private Long spuId;

    private String skuCode;

    private String skuName;

    /**
     * SPU 名称（商品标题），由 product 服务批量查 SKU 时一并返回
     */
    private String spuName;

    /**
     * 规格数据 JSON，如 {"颜色":"红","尺码":"XL"}
     */
    private String specData;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String image;

    /**
     * SKU 状态：1=正常，0=禁用
     */
    private Integer status;
}
