package com.degel.app.feign;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.ProductFeignFallback;
import com.degel.app.vo.CategoryTreeVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品服务 Feign 客户端
 */
@FeignClient(
        name = "degel-product",
        path = "/product",
        configuration = FeignConfig.class,
        fallback = ProductFeignFallback.class
)
public interface ProductFeignClient {

    /**
     * 获取分类树
     */
    @GetMapping("/category/tree")
    R<List<CategoryTreeVO>> getCategoryTree();

    /**
     * 分页查询 SPU 列表
     *
     * @param categoryId  分类 ID（可选）
     * @param keyword     关键词（可选）
     * @param page        页码
     * @param pageSize    每页大小
     * @param status      状态
     * @param auditStatus 审核状态
     */
    @GetMapping("/spu/page")
    R<IPage<ProductSpuVO>> getSpuPage(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus
    );

    /**
     * 获取 SPU 详情
     */
    @GetMapping("/spu/{spuId}")
    R<ProductSpuVO> getSpuDetail(@PathVariable("spuId") Long spuId);

    /**
     * 获取 SKU 列表
     */
    @GetMapping("/sku/list")
    R<List<ProductSkuVO>> getSkuList(@RequestParam("spuId") Long spuId);

    /**
     * 批量查询 SKU（内部接口）
     */
    @PostMapping("/inner/sku/batch")
    R<List<ProductSkuVO>> batchGetSku(@RequestBody List<Long> skuIds);
}
