package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品评分冗余回写请求体（字段与 degel-product 的 InnerRatingVo 对齐，Feign 按字段名序列化；
 * 不直接依赖 degel-product 模块，保持订单/商品服务间无编译期耦合）
 */
@Data
public class InnerRatingVo {

    private Long spuId;

    /** 平均评分 0.0-5.0，保留 1 位小数 */
    private BigDecimal ratingAvg;

    /** 评价数 */
    private Integer ratingCount;
}
