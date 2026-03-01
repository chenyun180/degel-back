package com.degel.product.vo;

import lombok.Data;

@Data
public class StockWarningVo {
    private String spuName;
    private String skuCode;
    private String specData;
    private Integer stock;
    private Integer stockWarning;
}
