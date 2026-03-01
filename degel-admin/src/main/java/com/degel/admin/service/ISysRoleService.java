package com.degel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysRole;

import java.util.List;

public interface ISysRoleService extends IService<SysRole> {

    IPage<SysRole> pageRoles(IPage<SysRole> page, SysRole query, Long shopId);

    void createRole(SysRole role, Long shopId);

    void updateRole(SysRole role, Long shopId);

    void deleteRole(Long roleId, Long shopId);

    void assignMenus(Long roleId, List<Long> menuIds, Long shopId);

    List<Long> getMenuIdsByRoleId(Long roleId);
}
