package com.degel.product.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * SPU 变更 → ES 索引同步监听器。
 *
 * AFTER_COMMIT：事务提交后才同步（索引器重查 DB 能读到已提交数据），事务回滚则不同步。
 * 异步执行（esIndexExecutor）：不拖慢商品主流程；队列打满时丢弃（DiscardPolicy），
 * 丢的更新靠 POST /spu/reindex 兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuIndexListener {

    private final SpuIndexer spuIndexer;

    @Async("esIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpuChanged(SpuIndexEvent event) {
        if (event.isDelete()) {
            spuIndexer.delete(event.getSpuId());
        } else {
            spuIndexer.index(event.getSpuId());
        }
    }
}
