package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysRole;
import com.degel.admin.service.ISysRoleService;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final ISysRoleService roleService;

    @GetMapping("/list")
    public R<IPage<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            SysRole query) {
        return R.ok(roleService.pageRoles(new Page<>(current, size), query));
    }

    /** 获取全部角色（下拉框使用） */
    @GetMapping("/all")
    public R<List<SysRole>> all() {
        return R.ok(roleService.list(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDelFlag, 0)
                .eq(SysRole::getStatus, 0)
                .orderByAsc(SysRole::getSort)));
    }

    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody SysRole role) {
        roleService.createRole(role);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody SysRole role) {
        roleService.updateRole(role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    /** 分配菜单权限 */
    @PutMapping("/assignMenus")
    public R<Void> assignMenus(@RequestBody Map<String, Object> params) {
        Long roleId = Long.valueOf(params.get("roleId").toString());
        @SuppressWarnings("unchecked")
        List<Long> menuIds = (List<Long>) params.get("menuIds");
        roleService.assignMenus(roleId, menuIds);
        return R.ok();
    }

    /** 获取角色已分配的菜单ID列表 */
    @GetMapping("/menuIds/{roleId}")
    public R<List<Long>> getMenuIds(@PathVariable Long roleId) {
        return R.ok(roleService.getMenuIdsByRoleId(roleId));
    }
}
