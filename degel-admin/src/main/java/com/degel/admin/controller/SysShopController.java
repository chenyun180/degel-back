package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysShop;
import com.degel.admin.service.ISysShopService;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public R<Void> create(@RequestBody SysShop shop) {
        shopService.createShop(shop);
        return R.ok();
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
}
