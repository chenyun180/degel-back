package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 SPU VO（内部 Feign 调用用，含审核状态字段）
 */
@Data
public class ProductSpuVO {

    private Long id;

    /** 归属店铺（product 侧 flat 接口带出，详情页按店领券用） */
    private Long shopId;

    private String name;

    /** ES 搜索高亮名（仅 /spu/page 关键词搜索返回，含 <em> 标签；其余路径为 null） */
    private String highlightName;

    private String subtitle;

    private String mainImage;

    /**
     * 商品图片列表（JSON 字符串，需解析）
     */
    private String images;

    private String detailContent;

    private Integer saleCount;

    private Integer viewCount;

    private BigDecimal minPrice;

    /**
     * 商品状态：1=上架，0=下架
     */
    private Integer status;

    /**
     * 审核状态：1=审核通过，0=待审核，2=驳回
     */
    private Integer auditStatus;

    private Long categoryId;
}
