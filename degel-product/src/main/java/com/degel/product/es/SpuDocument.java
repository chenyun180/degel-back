package com.degel.product.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

/**
 * C 端商品搜索索引文档。
 *
 * 设计约定（见 degel-back/docker/es/README.md 与集成方案）：
 * - 只索引 C 端可见商品（status=1 且 auditStatus=2），不可见商品直接从索引删除，
 *   可见性约束内建在数据里，查询侧仍加双保险 term 过滤
 * - categoryIds 为类目祖先链（含自身），索引时展开，查询端一次 term 命中任意层级
 * - minPrice/totalStock/saleCount 冗余进索引，消灭 MySQL 路径的 N+1 SKU 查询
 * - 时间存 epoch millis（Long），绕开 LocalDateTime 与 ES date 格式互转的坑
 */
@Data
@Document(indexName = "product_spu", createIndex = false)
public class SpuDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long shopId;

    /** 实际挂载类目 */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 祖先链（含自身），类目过滤用 */
    @Field(type = FieldType.Long)
    private List<Long> categoryIds;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String subtitle;

    /** 商家设置的搜索关键词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String keyword;

    /** 列表展示图，只存不索引 */
    @Field(type = FieldType.Keyword, index = false)
    private String mainImage;

    /** SKU 最低价（元）。展示/排序用，资金计算仍走 MySQL BigDecimal */
    @Field(type = FieldType.Double)
    private Double minPrice;

    @Field(type = FieldType.Integer)
    private Integer totalStock;

    @Field(type = FieldType.Integer)
    private Integer saleCount;

    /** 0 下架 / 1 上架（双保险，正常情况下索引里只有 1） */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 审核状态（同上，索引里只有 2=已通过） */
    @Field(type = FieldType.Integer)
    private Integer auditStatus;

    /** epoch millis */
    @Field(type = FieldType.Long)
    private Long createTime;
}
