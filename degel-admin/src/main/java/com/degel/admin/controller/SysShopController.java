package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysShop;
import com.degel.admin.service.ISysShopService;
import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class SysShopController {

    private final ISysShopService shopService;

    @GetMapping("/list")
    public R<IPage<SysShop>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            SysShop query) {
        return R.ok(shopService.pageShops(new Page<>(current, size), query));
    }

    @GetMapping("/{id}")
    public R<SysShop> getById(@PathVariable Long id) {
        return R.ok(shopService.getById(id));
    }

    @PostMapping
    public R<Map<String, String>> create(@RequestBody SysShop shop) {
        Map<String, String> account = shopService.createShop(shop);
        return R.ok(account);
    }

    @PutMapping
    public R<Void> update(@RequestBody SysShop shop) {
        shopService.updateShop(shop);
        return R.ok();
    }

    @PutMapping("/status")
    public R<Void> toggleStatus(@RequestParam Long id, @RequestParam Integer status) {
        shopService.toggleStatus(id, status);
        return R.ok();
    }

    @GetMapping("/mine")
    public R<SysShop> getMine(@RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == 0) {
            throw new BusinessException("平台账号无店铺信息");
        }
        return R.ok(shopService.getById(shopId));
    }

    // 店铺自助改资料已改为审批制：POST /shop/mine/change（ShopChangeController），
    // 原 PUT /shop/mine 直接 updateById 的入口已移除，否则可绕过审批
}
