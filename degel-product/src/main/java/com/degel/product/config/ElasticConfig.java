package com.degel.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * ES 索引同步线程池。
 *
 * ES 慢/挂时队列打满直接丢弃（DiscardPolicy + 告警日志），
 * 丢的索引更新靠 POST /spu/reindex 全量重建兜底——绝不允许 ES 问题拖垮商品主流程内存。
 */
@Slf4j
@Configuration
@EnableAsync
public class ElasticConfig {

    @Bean("esIndexExecutor")
    public ThreadPoolTaskExecutor esIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("es-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("ES 索引同步队列已满，丢弃本次同步（reindex 可修复）");
                super.rejectedExecution(r, e);
            }
        });
        executor.initialize();
        return executor;
    }
}
