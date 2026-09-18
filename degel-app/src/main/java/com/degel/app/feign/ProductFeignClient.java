package com.degel.app.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.ProductFeignFallback;
import com.degel.app.feign.fallback.ProductFeignFallbackFactory;
import com.degel.app.vo.CategoryTreeVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品服务 Feign 客户端
 *
 * 注意：Feign 直连 degel-product（lb），不经过网关，因此不带网关的 /product 路由前缀
 * （网关 /product/** 路由带 StripPrefix=1，直连服务时端点就是控制器原始路径）。
 */
@FeignClient(
        name = "degel-product",
        contextId = "productFeignClient",
        configuration = FeignConfig.class,
        fallbackFactory = ProductFeignFallbackFactory.class
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
     * @param sort        排序方式（sale=按销量倒序，可选）
     *
     * <p>注意返回类型必须用具体类 Page 而非接口 IPage——IPage 是抽象类型，
     * Jackson 反序列化会抛 InvalidDefinitionException 触发 Feign 降级
     */
    @GetMapping("/spu/page")
    R<Page<ProductSpuVO>> getSpuPage(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestParam(value = "sort", required = false) String sort
    );

    /**
     * 获取 SPU 详情（扁平结构，字段与 ProductSpuVO 对齐；
     * /spu/{id} 是管理端嵌套结构 {spu, skuList}，C 端不可用）
     */
    @GetMapping("/spu/{spuId}/flat")
    R<ProductSpuVO> getSpuDetail(@PathVariable("spuId") Long spuId);

    /**
     * 搜索埋点落库（product_search_log，搜索词分析数据源；best-effort，失败即丢不影响搜索）
     */
    @PostMapping("/inner/spu/search-log")
    R<Void> recordSearchLog(@RequestBody java.util.Map<String, Object> body);

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

    /**
     * 批量取 SPU 主图（内部接口）。注意：product 侧实际只返回 id + mainImage 两个字段
     * （SpuImageVo），此处声明的 ProductSpuVO 其余字段均为 null——需要完整信息用 batchGetSpu
     */
    @PostMapping("/inner/spu/batch-images")
    R<List<ProductSpuVO>> batchGetSpuImages(@RequestBody List<Long> spuIds);

    /**
     * 批量查 SPU 列表信息（收藏/足迹展示，返回实时名称/价格/主图/状态；不存在的 id 跳过）
     */
    @PostMapping("/inner/spu/batch")
    R<List<ProductSpuVO>> batchGetSpu(@RequestBody List<Long> spuIds);

    /**
     * 搜索联想（product 侧 ES 异常自行吞掉返回空列表；Feign 级故障走 fallback 返回 fail）
     */
    @GetMapping("/spu/suggest")
    R<List<String>> getSuggest(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "size", defaultValue = "5") Integer size);
}
