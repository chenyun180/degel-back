package com.degel.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品评分冗余回写（degel-order 评价写入后经 Feign 调用）
 */
@Data
public class InnerRatingVo {

    private Long spuId;

    /** 平均评分 0.0-5.0，保留 1 位小数 */
    private BigDecimal ratingAvg;

    /** 评价数 */
    private Integer ratingCount;
}
