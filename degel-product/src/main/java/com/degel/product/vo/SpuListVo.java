package com.degel.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SpuListVo {

    private Long id;
    private Long shopId;
    private Long categoryId;
    private String name;
    /**
     * 关键词高亮名称（仅 C 端关键词搜索返回，关键词以 <em></em> 包裹；无关键词/MySQL 降级时为 null）。
     * 前端禁止直接 innerHTML 渲染，需按 <em> 切分后转义输出。
     */
    private String highlightName;
    private String subtitle;
    private String mainImage;
    private Integer auditStatus;
    private String rejectReason;
    private Integer status;
    private Integer saleCount;
    private BigDecimal minPrice;
    private Integer totalStock;
    private LocalDateTime createTime;
}
