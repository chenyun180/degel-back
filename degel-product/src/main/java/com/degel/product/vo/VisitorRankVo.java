package com.degel.product.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class VisitorRankVo {
    private Long spuId;
    private String spuName;
    private String mainImage;
    private Integer viewCount;
    private Integer orderCount;
    private BigDecimal conversionRate;
}
