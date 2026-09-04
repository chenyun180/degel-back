package com.degel.product.vo;

import lombok.Data;

/**
 * 内部库存变更请求（与 degel-app 的 StockDeductVO/StockRestoreVO 字段对齐）
 */
@Data
public class InnerStockReqVo {

    private Long skuId;
    private Integer quantity;
}
