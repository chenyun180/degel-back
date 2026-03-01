package com.degel.product.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HotSaleVo {
    private Long spuId;
    private String spuName;
    private String mainImage;
    private Integer saleCount;
    private BigDecimal saleAmount;
    private BigDecimal growthRate;
}
