package com.degel.product.es;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.Constants;
import com.degel.product.vo.SpuListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * C 端商品搜索（ES）。
 *
 * 可见性是服务端硬约束（status=1 + auditStatus=2 写死在 filter 里），
 * 方法签名没有任何 status 参数——调用方想绕也绕不过，区别于 /spu/page 靠调用方自觉传参的旧模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ElasticsearchOperations operations;

    /**
     * @param page     从 1 起
     * @param sort     "sale"=销量倒序；null 且有 keyword 时按相关性；null 无 keyword 时按创建时间倒序
     */
    public Page<SpuListVo> search(Integer page, Integer pageSize, String keyword, Long categoryId, String sort) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);

        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("status", 1))
                .filter(QueryBuilders.termQuery("auditStatus", Constants.AUDIT_APPROVED));
        boolean hasKeyword = StrUtil.isNotBlank(keyword);
        if (hasKeyword) {
            bool.must(QueryBuilders.multiMatchQuery(keyword)
                    .field("name", 3.0f)
                    .field("keyword", 2.0f)
                    .field("subtitle", 1.0f)
                    .operator(Operator.AND));
        }
        if (categoryId != null) {
            // categoryIds 已含祖先链，选大类直接命中其下所有层级商品
            bool.filter(QueryBuilders.termQuery("categoryIds", categoryId));
        }

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                .withQuery(bool)
                .withPageable(PageRequest.of(p - 1, size));
        if ("sale".equals(sort)) {
            builder.withSort(org.elasticsearch.search.sort.SortBuilders.fieldSort("saleCount")
                    .order(org.elasticsearch.search.sort.SortOrder.DESC));
        } else if (!hasKeyword) {
            // 无关键词无排序：新品在前；有关键词默认按 ES 相关性（_score）
            builder.withSort(org.elasticsearch.search.sort.SortBuilders.fieldSort("createTime")
                    .order(org.elasticsearch.search.sort.SortOrder.DESC));
        }

        NativeSearchQuery query = builder.build();
        query.setTrackTotalHits(true);
        // 执行前打印 DSL（QueryBuilder.toString() 输出 JSON 片段），排查搜索逻辑用
        log.info("ES DSL: POST /product_spu/_search from={} size={} sort={} query={}",
                (p - 1) * size, size, sort, bool);
        SearchHits<SpuDocument> hits = operations.search(query, SpuDocument.class);

        Page<SpuListVo> result = new Page<>(p, size, hits.getTotalHits());
        result.setRecords(hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toVo)
                .collect(Collectors.toList()));
        return result;
    }

    private SpuListVo toVo(SpuDocument doc) {
        SpuListVo vo = new SpuListVo();
        vo.setId(doc.getId());
        vo.setShopId(doc.getShopId());
        vo.setCategoryId(doc.getCategoryId());
        vo.setName(doc.getName());
        vo.setSubtitle(doc.getSubtitle());
        vo.setMainImage(doc.getMainImage());
        vo.setAuditStatus(doc.getAuditStatus());
        vo.setStatus(doc.getStatus());
        vo.setSaleCount(doc.getSaleCount());
        vo.setMinPrice(doc.getMinPrice() != null ? BigDecimal.valueOf(doc.getMinPrice()) : null);
        vo.setTotalStock(doc.getTotalStock());
        vo.setCreateTime(doc.getCreateTime() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(doc.getCreateTime()), ZoneId.systemDefault())
                : null);
        return vo;
    }
}
