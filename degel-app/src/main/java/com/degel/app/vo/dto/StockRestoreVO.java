package com.degel.app.vo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存恢复请求 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRestoreVO {

    /** SKU ID */
    private Long skuId;

    /** 恢复数量 */
    private Integer quantity;
}
