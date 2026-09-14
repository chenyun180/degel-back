package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表 VO（C端展示）
 */
@Data
public class AppSpuListVO {

    private Long spuId;

    private String name;

    /**
     * 关键词高亮名称（仅关键词搜索返回，命中词以 <em></em> 包裹；推荐/分类浏览为 null）。
     * 前端需按 <em> 切分后转义渲染，禁止 innerHTML。
     */
    private String highlightName;

    private String mainImage;

    /**
     * 最低 SKU 价格
     */
    private BigDecimal minPrice;

    private Integer saleCount;
}
