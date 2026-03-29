package com.degel.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.product.entity.ProductSpu;
import com.degel.product.service.IProductSpuService;
import com.degel.product.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class ProductSpuController {

    private final IProductSpuService spuService;

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

    @GetMapping("/{id}")
    public R<SpuDetailVo> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        Long effectiveShopId = shopId > 0 ? shopId : null;
        return R.ok(spuService.getSpuDetail(id, effectiveShopId));
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
    public R<Void> submitAudit(@PathVariable Long id) {
        spuService.submitAudit(id);
        return R.ok();
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
