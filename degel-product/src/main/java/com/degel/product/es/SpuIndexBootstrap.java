package com.degel.product.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 product_spu 索引存在（含 IK 分词 mapping）。
 *
 * 整体 try/catch：ES 集群不可用时只告警、不阻塞服务启动——
 * 商品主流程（MySQL）不依赖 ES，索引稍后可用 POST /spu/reindex 补建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuIndexBootstrap implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = operations.indexOps(SpuDocument.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("ES 索引 product_spu 不存在，已按 SpuDocument mapping 创建");
            }
        } catch (Exception e) {
            log.warn("ES 索引 bootstrap 失败（服务继续启动，商品主流程不受影响）: {}", e.getMessage());
        }
    }
}
