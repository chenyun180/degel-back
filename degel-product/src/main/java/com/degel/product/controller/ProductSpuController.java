package com.degel.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.product.es.SpuIndexer;
import com.degel.product.es.SpuSearchService;
import com.degel.product.entity.ProductSpu;
import com.degel.product.service.IProductSpuService;
import com.degel.product.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class ProductSpuController {

    private final IProductSpuService spuService;
    private final SpuSearchService spuSearchService;
    private final SpuIndexer spuIndexer;

    @GetMapping("/list")
    public R<IPage<SpuListVo>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            ProductSpu query) {
        if (shopId > 0) {
            query.setShopId(shopId);
        }
        return R.ok(spuService.pageSpu(new Page<>(current, size), query));
    }

    /**
     * 分页查询 SPU（C 端 BFF 专用：degel-app 经 Feign 调用）
     * 参数名与 degel-app ProductFeignClient.getSpuPage 声明对齐。
     *
     * 实现走 ES 搜索（可见性 status=1+auditStatus=2 为服务端硬约束，IK 中文分词），
     * ES 不可用时自动降级回 MySQL pageSpu——调用方无感知，契约不变。
     * 注意：ES 路径忽略调用方传入的 status/auditStatus/shopId 等过滤（仅 BFF C 端语义）。
     */
    @GetMapping("/page")
    public R<IPage<SpuListVo>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(value = "sort", required = false) String sort,
            ProductSpu query) {
        try {
            return R.ok(spuSearchService.search(page, pageSize, query.getKeyword(), query.getCategoryId(), sort));
        } catch (Exception e) {
            log.warn("ES 搜索不可用，降级 MySQL LIKE 查询: {}", e.getMessage());
            return R.ok(spuService.pageSpu(new Page<>(page, pageSize), query, sort));
        }
    }

    /**
     * 全量重建 ES 索引（平台管理动作，网关侧已限平台角色）。
     *
     * @param recreate true=先删索引按当前 mapping 重建（切换分词器/mapping 变更后用），
     *                 期间 C 端搜索自动降级 MySQL
     */
    @PostMapping("/reindex")
    public R<Integer> reindex(@RequestParam(defaultValue = "false") boolean recreate) {
        return R.ok(spuIndexer.reindexAll(recreate));
    }

    @GetMapping("/{id}")
    public R<SpuDetailVo> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        Long effectiveShopId = shopId > 0 ? shopId : null;
        return R.ok(spuService.getSpuDetail(id, effectiveShopId));
    }

    /**
     * 扁平结构 SPU 详情（C 端 BFF 专用：degel-app 经 Feign 调用，字段与其 ProductSpuVO 对齐；
     * 不做店铺归属过滤，状态校验由 BFF 负责）
     */
    @GetMapping("/{id}/flat")
    public R<ProductSpu> getFlatById(@PathVariable Long id) {
        return R.ok(spuService.getById(id));
    }

    @PostMapping
    public R<Void> create(
            @Valid @RequestBody SpuCreateVo vo,
            @RequestHeader("X-Shop-Id") Long shopId) {
        spuService.createSpu(vo, shopId);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(
            @Valid @RequestBody SpuUpdateVo vo,
            @RequestHeader("X-Shop-Id") Long shopId) {
        spuService.updateSpu(vo, shopId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Shop-Id") Long shopId) {
        spuService.deleteSpu(id, shopId);
        return R.ok();
    }

    @PutMapping("/submit/{id}")
    public R<Void> submitAudit(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        spuService.submitAudit(id, shopId);
        return R.ok();
    }

    /** 批量提交审核 */
    @PutMapping("/submitBatch")
    public R<Integer> submitAuditBatch(
            @RequestBody List<Long> ids,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(spuService.submitAuditBatch(ids, shopId));
    }

    @PutMapping("/audit")
    public R<Void> audit(
            @Valid @RequestBody AuditVo auditVo,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long auditorId) {
        spuService.audit(auditVo, auditorId);
        return R.ok();
    }

    @PutMapping("/toggle-status/{id}")
    public R<Void> toggleStatus(
            @PathVariable Long id,
            @RequestHeader("X-Shop-Id") Long shopId) {
        spuService.toggleStatus(id, shopId);
        return R.ok();
    }

    @PutMapping("/sku/stock")
    public R<Void> updateStock(
            @Valid @RequestBody StockUpdateVo vo,
            @RequestHeader("X-Shop-Id") Long shopId) {
        spuService.updateStock(vo, shopId);
        return R.ok();
    }
}
