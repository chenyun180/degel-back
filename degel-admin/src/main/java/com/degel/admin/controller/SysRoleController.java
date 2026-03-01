package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysRole;
import com.degel.admin.service.ISysRoleService;
import com.degel.admin.vo.RoleAssignMenuVo;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final ISysRoleService roleService;

    @GetMapping("/list")
    public R<IPage<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            SysRole query) {
        return R.ok(roleService.pageRoles(new Page<>(current, size), query, shopId));
    }

    @GetMapping("/all")
    public R<List<SysRole>> all(
            @RequestParam(value = "shopId", required = false) Long shopId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDelFlag, 0)
                .eq(SysRole::getStatus, 0)
                .eq(shopId != null, SysRole::getShopId, shopId)
                .orderByAsc(SysRole::getSort);
        return R.ok(roleService.list(wrapper));
    }

    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @PostMapping
    public R<Void> create(
            @RequestBody SysRole role,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        roleService.createRole(role, shopId);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(
            @RequestBody SysRole role,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        roleService.updateRole(role, shopId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        roleService.deleteRole(id, shopId);
        return R.ok();
    }

    @PutMapping("/assignMenus")
    public R<Void> assignMenus(
            @Valid @RequestBody RoleAssignMenuVo vo,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        roleService.assignMenus(vo.getRoleId(), vo.getMenuIds(), shopId);
        return R.ok();
    }

    @GetMapping("/menuIds/{roleId}")
    public R<List<Long>> getMenuIds(@PathVariable Long roleId) {
        return R.ok(roleService.getMenuIdsByRoleId(roleId));
    }
}
