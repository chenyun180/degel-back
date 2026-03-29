package com.degel.app.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品详情 VO（C端展示）
 */
@Data
public class AppSpuDetailVO {

    private Long spuId;

    private String name;

    private String subtitle;

    private String mainImage;

    /**
     * 商品图片列表（从 JSON 解析）
     */
    private List<String> images;

    private String detailContent;

    private Integer saleCount;

    private Integer viewCount;

    /**
     * SKU 列表
     */
    private List<AppSkuVO> skuList;
}
