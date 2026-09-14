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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
        if (hasKeyword) {
            // 关键词高亮 name：numberOfFragments(0) 返回整段字段值（商品名是短文本，不能截断成片段）
            builder.withHighlightBuilder(new org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder()
                    .field("name")
                    .preTags("<em>").postTags("</em>")
                    .numOfFragments(0));
        }

        NativeSearchQuery query = builder.build();
        query.setTrackTotalHits(true);
        // 执行前打印 DSL（QueryBuilder.toString() 输出 JSON 片段），排查搜索逻辑用
        log.info("ES DSL: POST /product_spu/_search from={} size={} sort={} query={}",
                (p - 1) * size, size, sort, bool);
        SearchHits<SpuDocument> hits = operations.search(query, SpuDocument.class);

        Page<SpuListVo> result = new Page<>(p, size, hits.getTotalHits());
        result.setRecords(hits.getSearchHits().stream()
                .map(hit -> {
                    SpuListVo vo = toVo(hit.getContent());
                    vo.setHighlightName(getHighlightName(hit));
                    return vo;
                })
                .collect(Collectors.toList()));
        return result;
    }

    /** 取 name 高亮片段（numOfFragments(0) 时至多一条，即完整字段值）；无命中返回 null */
    private String getHighlightName(SearchHit<SpuDocument> hit) {
        List<String> fragments = hit.getHighlightField("name");
        if (fragments == null || fragments.isEmpty()) {
            return null;
        }
        return String.join("", fragments);
    }

    /**
     * 搜索联想：对 name 做 match_phrase_prefix 前缀短语匹配（零 mapping 变更，无需重建索引）。
     * 只出已上架+过审商品名；外层不 try-catch，由 Controller 统一兜（与 /spu/page 降级写法同风格）。
     */
    public List<String> suggest(String keyword, int size) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        int s = Math.min(Math.max(size, 1), 10);
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("status", 1))
                .filter(QueryBuilders.termQuery("auditStatus", Constants.AUDIT_APPROVED))
                .must(QueryBuilders.matchPhrasePrefixQuery("name", keyword));
        // 多取一些，按同名去重后截断，保持相关性顺序
        SearchHits<SpuDocument> hits = operations.search(
                new NativeSearchQueryBuilder().withQuery(bool)
                        .withPageable(PageRequest.of(0, s * 3))
                        .build(),
                SpuDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getName())
                .filter(Objects::nonNull)
                .distinct()
                .limit(s)
                .collect(Collectors.toList());
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
