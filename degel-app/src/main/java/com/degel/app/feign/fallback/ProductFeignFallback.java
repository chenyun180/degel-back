package com.degel.app.feign.fallback;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.vo.CategoryTreeVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * ProductFeignClient 降级实现
 */
@Slf4j
@Component
public class ProductFeignFallback implements ProductFeignClient {

    @Override
    public R<List<CategoryTreeVO>> getCategoryTree() {
        log.error("[ProductFeignFallback] getCategoryTree 降级");
        return R.fail(50001, "商品分类服务暂不可用");
    }

    @Override
    public R<IPage<ProductSpuVO>> getSpuPage(Long categoryId, String keyword, Integer page,
                                              Integer pageSize, Integer status, Integer auditStatus) {
        log.error("[ProductFeignFallback] getSpuPage 降级");
        return R.fail(50001, "商品列表服务暂不可用");
    }

    @Override
    public R<ProductSpuVO> getSpuDetail(Long spuId) {
        log.error("[ProductFeignFallback] getSpuDetail spuId={} 降级", spuId);
        return R.fail(50001, "商品详情服务暂不可用");
    }

    @Override
    public R<List<ProductSkuVO>> getSkuList(Long spuId) {
        log.error("[ProductFeignFallback] getSkuList spuId={} 降级", spuId);
        return R.fail(50001, "商品SKU服务暂不可用");
    }

    @Override
    public R<List<ProductSkuVO>> batchGetSku(List<Long> skuIds) {
        log.error("[ProductFeignFallback] batchGetSku 降级，skuIds={}", skuIds);
        return R.fail(50001, "商品SKU服务暂不可用");
    }
}
