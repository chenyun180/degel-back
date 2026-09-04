package com.degel.product.es;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SPU 变更事件：事务提交后异步同步 ES 索引。
 * delete=true 表示商品已删/不可见，直接从索引移除。
 */
@Getter
@AllArgsConstructor
public class SpuIndexEvent {

    private final Long spuId;
    private final boolean delete;
}
