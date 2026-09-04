package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.service.IProductSkuService;
import com.degel.product.vo.AppSkuVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SKU 查询（C 端 BFF 专用：degel-app 经 Feign 调用）
 */
@RestController
@RequestMapping("/sku")
@RequiredArgsConstructor
public class ProductSkuController {

    private final IProductSkuService skuService;

    /**
     * 按 SPU 查 SKU 列表
     */
    @GetMapping("/list")
    public R<List<AppSkuVo>> list(@RequestParam Long spuId) {
        return R.ok(skuService.getSkuList(spuId));
    }
}
