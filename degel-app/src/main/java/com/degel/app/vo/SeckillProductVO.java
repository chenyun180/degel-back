package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀商品 C 端出参（skuName/mainImage/originalPrice 实时查 product；
 * remaining 实时读 Redis，-1=未预热，null=缓存快照未填充）
 */
@Data
public class SeckillProductVO {

    private String id;
    private String sessionId;
    private String spuId;
    private String skuId;
    /** SKU 规格名（实时查 product） */
    private String skuName;
    /** 主图完整 URL（fileUrl 拼好） */
    private String mainImage;
    /** 秒杀价 */
    private BigDecimal seckillPrice;
    /** 原价（SKU 实时价） */
    private BigDecimal originalPrice;
    /** 秒杀总库存 */
    private Integer totalStock;
    /** 剩余库存（实时 MGET；-1=未预热，null=缓存快照不含实时数据） */
    private Long remaining;
    /** 已抢百分比（0-100，向上取整；未预热为 null） */
    private Integer percent;
    /** 每人限购 */
    private Integer perLimit;
}
