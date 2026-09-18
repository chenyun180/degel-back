package com.degel.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 滞销预警行（近 30 天零销量且库存>0，按占压金额倒序） */
@Data
public class UnsalableVo {

    private Long skuId;
    private Long spuId;
    private String spuName;
    private String skuCode;
    private String specData;
    private Integer stock;
    private BigDecimal price;
    /** 占压金额 = stock × price */
    private BigDecimal occupyAmount;
    /** SPU 累计销量（参考） */
    private Integer saleCount;
}
