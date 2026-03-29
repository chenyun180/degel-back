package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * C端 SKU VO
 */
@Data
public class AppSkuVO {

    private Long skuId;

    private String skuCode;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String image;

    /**
     * 规格数据，如 {"颜色":"红","尺码":"XL"}
     */
    private Map<String, String> specData;

    /**
     * 是否售罄（stock <= 0 则 true）
     */
    private Boolean soldOut;
}
