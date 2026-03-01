package com.degel.product.vo;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class StockUpdateVo {

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
}
