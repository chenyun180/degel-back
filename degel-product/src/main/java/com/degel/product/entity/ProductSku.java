package com.degel.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {

    private Long spuId;
    private String skuCode;
    private String specData;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal costPrice;
    private Integer stock;
    private Integer stockWarning;
    private BigDecimal weight;
    private String image;
    private Integer status;
}
