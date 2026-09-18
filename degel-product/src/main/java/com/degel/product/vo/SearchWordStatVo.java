package com.degel.product.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索词分析统计行（product_search_log 聚合，平台报表用）
 */
@Data
public class SearchWordStatVo {

    private String keyword;

    /** 搜索次数 */
    private Long searchCount;

    /** 去重搜索人数（匿名搜索不计入 distinct，user_id 为 NULL） */
    private Long userCount;

    /** 空结果次数（搜了没货，缺货缺口信号） */
    private Long zeroResultCount;

    private LocalDateTime lastSearchTime;
}
