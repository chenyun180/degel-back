package com.degel.product.es;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.Constants;
import com.degel.common.core.exception.BusinessException;
import com.degel.product.entity.ProductSku;
import com.degel.product.entity.ProductSpu;
import com.degel.product.service.IProductCategoryService;
import com.degel.product.service.IProductSkuService;
import com.degel.product.service.IProductSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * SPU → ES 索引同步器。
 *
 * 失败语义：单文档同步（index/delete）失败只 log.warn 绝不上抛——ES 宕机不能影响商品主流程，
 * 索引漂移靠 POST /spu/reindex 全量重建兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuIndexer {

    private static final int REINDEX_BATCH = 500;

    private final ElasticsearchOperations operations;
    private final IProductSpuService spuService;
    private final IProductSkuService skuService;
    private final IProductCategoryService categoryService;
    /** 写操作前打印文档 JSON 用 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 同步单个 SPU：可见则写入（含 SKU 聚合冗余字段），不可见/已删则从索引删除。
     * 一处逻辑覆盖所有状态变化（上下架、审核、编辑、逻辑删）。
     */
    public void index(Long spuId) {
        try {
            ProductSpu spu = spuService.getById(spuId);
            if (spu == null || !isVisible(spu)) {
                delete(spuId);
                return;
            }
            SpuDocument doc = buildDocument(spu);
            log.info("ES DSL: PUT /product_spu/_doc/{} doc={}", spuId, toJson(doc));
            operations.save(doc);
        } catch (Exception e) {
            log.warn("ES 索引同步失败 spuId={}，可稍后调用 POST /spu/reindex 修复: {}", spuId, e.getMessage());
        }
    }

    /** 从索引删除（文档本就不存在时静默） */
    public void delete(Long spuId) {
        try {
            log.info("ES DSL: DELETE /product_spu/_doc/{}", spuId);
            operations.delete(String.valueOf(spuId), SpuDocument.class);
        } catch (Exception e) {
            log.warn("ES 索引删除失败 spuId={}: {}", spuId, e.getMessage());
        }
    }

    private String toJson(SpuDocument doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            return String.valueOf(doc);
        }
    }

    /**
     * 全量重建索引（平台管理动作）。
     *
     * @param recreate true 时先删索引按当前 mapping 重建（切换分词器/mapping 变更后用）；
     *                 期间 C 端搜索自动降级 MySQL（查询侧 catch）
     * @return 写入文档数
     */
    public int reindexAll(boolean recreate) {
        try {
            IndexOperations indexOps = operations.indexOps(SpuDocument.class);
            if (recreate && indexOps.exists()) {
                indexOps.delete();
            }
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
            int count = 0;
            long current = 1;
            while (true) {
                Page<ProductSpu> p = spuService.page(new Page<>(current, REINDEX_BATCH),
                        new LambdaQueryWrapper<ProductSpu>()
                                .eq(ProductSpu::getStatus, 1)
                                .eq(ProductSpu::getAuditStatus, Constants.AUDIT_APPROVED));
                if (p.getRecords().isEmpty()) {
                    break;
                }
                List<SpuDocument> docs = new ArrayList<>();
                for (ProductSpu spu : p.getRecords()) {
                    try {
                        docs.add(buildDocument(spu));
                    } catch (Exception e) {
                        log.warn("reindex 跳过异常 SPU id={}: {}", spu.getId(), e.getMessage());
                    }
                }
                if (!docs.isEmpty()) {
                    log.info("ES DSL: POST /_bulk 批量写入 {} 个文档（第 {} 批，示例 id={}）",
                            docs.size(), current, docs.get(0).getId());
                    operations.save(docs);
                    count += docs.size();
                }
                if (current >= p.getPages()) {
                    break;
                }
                current++;
            }
            log.info("ES 全量重建完成，共写入 {} 个文档", count);
            return count;
        } catch (Exception e) {
            log.error("ES 全量重建失败", e);
            throw new BusinessException("ES 全量重建失败：" + e.getMessage());
        }
    }

    private boolean isVisible(ProductSpu spu) {
        return Integer.valueOf(1).equals(spu.getStatus())
                && Integer.valueOf(Constants.AUDIT_APPROVED).equals(spu.getAuditStatus());
    }

    private SpuDocument buildDocument(ProductSpu spu) {
        List<ProductSku> skuList = skuService.listBySpuId(spu.getId());
        Double minPrice = skuList.stream()
                .map(ProductSku::getPrice)
                .filter(price -> price != null)
                .min(BigDecimal::compareTo)
                .map(BigDecimal::doubleValue)
                .orElse(null);
        Integer totalStock = skuList.stream()
                .map(ProductSku::getStock)
                .filter(stock -> stock != null)
                .reduce(0, Integer::sum);

        SpuDocument doc = new SpuDocument();
        doc.setId(spu.getId());
        doc.setShopId(spu.getShopId());
        doc.setCategoryId(spu.getCategoryId());
        doc.setCategoryIds(categoryService.collectAncestorIds(spu.getCategoryId()));
        doc.setName(spu.getName());
        doc.setSubtitle(spu.getSubtitle());
        doc.setKeyword(spu.getKeyword());
        doc.setMainImage(spu.getMainImage());
        doc.setMinPrice(minPrice);
        doc.setTotalStock(totalStock);
        doc.setSaleCount(spu.getSaleCount() != null ? spu.getSaleCount() : 0);
        doc.setStatus(spu.getStatus());
        doc.setAuditStatus(spu.getAuditStatus());
        doc.setCreateTime(spu.getCreateTime() != null
                ? spu.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null);
        return doc;
    }
}
