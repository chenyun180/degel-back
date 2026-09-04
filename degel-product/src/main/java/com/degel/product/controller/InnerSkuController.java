package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.service.IProductSkuService;
import com.degel.product.vo.AppSkuVo;
import com.degel.product.vo.InnerStockReqVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口（degel-app 专用，经 Feign 直连；网关侧 /product/inner/ 已列入 internal-urls 禁止外部访问）
 */
@RestController
@RequestMapping("/inner/sku")
@RequiredArgsConstructor
public class InnerSkuController {

    private final IProductSkuService skuService;

    /**
     * 批量查询 SKU（购物车/下单校验用）
     */
    @PostMapping("/batch")
    public R<List<AppSkuVo>> batch(@RequestBody List<Long> skuIds) {
        return R.ok(skuService.batchGetSku(skuIds));
    }

    /**
     * 原子扣减库存：SQL: UPDATE product_sku SET stock=stock-? WHERE id=? AND stock>=? AND status=1
     * 影响行数=0 → 返回 data=false（库存不足/已下架），由调用方决定业务语义
     */
    @PutMapping("/stock/deduct")
    public R<Boolean> deduct(@RequestBody InnerStockReqVo req) {
        return R.ok(skuService.deductStock(req.getSkuId(), req.getQuantity()));
    }

    /**
     * 恢复库存（取消订单 / 退款时调用）
     * SQL: UPDATE product_sku SET stock=stock+? WHERE id=?
     */
    @PutMapping("/stock/restore")
    public R<Boolean> restore(@RequestBody InnerStockReqVo req) {
        return R.ok(skuService.restoreStock(req.getSkuId(), req.getQuantity()));
    }
}
