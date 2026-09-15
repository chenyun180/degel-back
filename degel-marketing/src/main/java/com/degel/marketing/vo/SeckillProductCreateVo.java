package com.degel.marketing.vo;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 场次秒杀商品新增/编辑入参（id 空=新增，非空=编辑）。
 */
@Data
public class SeckillProductCreateVo {

    /** 空=新增（MP 自动雪花）；非空=编辑 */
    private Long id;

    @NotNull(message = "所属场次不能为空")
    private Long sessionId;

    @NotNull(message = "商品 spuId 不能为空")
    private Long spuId;

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于0")
    private BigDecimal seckillPrice;

    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存至少为1")
    private Integer seckillStock;

    @NotNull(message = "每人限购不能为空")
    @Min(value = 1, message = "每人限购至少为1")
    private Integer perLimit;

    @NotNull(message = "排序不能为空")
    private Integer sort;
}
