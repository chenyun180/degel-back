package com.degel.admin.controller;

import com.degel.admin.entity.SysMenu;
import com.degel.admin.service.ISysMenuService;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final ISysMenuService menuService;

    /** 获取菜单树（角色分配菜单时使用） */
    @GetMapping("/tree")
    public R<List<SysMenu>> tree() {
        return R.ok(menuService.listMenuTree());
    }

    /** 获取所有菜单列表（树形表格） */
    @GetMapping("/list")
    public R<List<SysMenu>> list() {
        return R.ok(menuService.listMenuTree());
    }

    @GetMapping("/{id}")
    public R<SysMenu> getById(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody SysMenu menu) {
        menuService.createMenu(menu);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody SysMenu menu) {
        menuService.updateMenu(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return R.ok();
    }
}
