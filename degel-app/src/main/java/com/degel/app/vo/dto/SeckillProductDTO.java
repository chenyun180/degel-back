package com.degel.app.vo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 场次秒杀商品 DTO（Feign 反序列化 marketing SeckillProductVo）
 */
@Data
public class SeckillProductDTO {

    private Long id;
    private Long sessionId;
    private Long spuId;
    private Long skuId;
    /** 秒杀价 */
    private BigDecimal seckillPrice;
    /** 秒杀库存 */
    private Integer seckillStock;
    /** 每人限购 */
    private Integer perLimit;
    private Integer sort;
}
