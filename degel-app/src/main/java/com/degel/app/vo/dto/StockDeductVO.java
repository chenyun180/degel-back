package com.degel.app.vo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存扣减请求 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductVO {

    /** SKU ID */
    private Long skuId;

    /** 扣减数量 */
    private Integer quantity;
}
