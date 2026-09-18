package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.mapper.ProductSearchLogMapper;
import com.degel.product.vo.SearchWordStatVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台搜索词分析（数据源 product_search_log，2026-09-18 埋点上线后开始积累）。
 * /product/platform 前缀已列入网关 admin-urls（仅平台角色可访问）。
 */
@RestController
@RequestMapping("/platform/search")
@RequiredArgsConstructor
public class PlatformSearchController {

    private final ProductSearchLogMapper searchLogMapper;

    /**
     * 近 N 天搜索词聚合 Top100（次数/人数/空结果次数/最近搜索时间）
     */
    @GetMapping("/stats")
    public R<List<SearchWordStatVo>> stats(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        int d = (days == null || days < 1) ? 30 : Math.min(days, 90);
        return R.ok(searchLogMapper.aggregateStats(d));
    }
}
