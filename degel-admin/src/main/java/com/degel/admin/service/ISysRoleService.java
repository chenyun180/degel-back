package com.degel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysRole;

import java.util.List;

public interface ISysRoleService extends IService<SysRole> {

    IPage<SysRole> pageRoles(IPage<SysRole> page, SysRole query);

    void createRole(SysRole role);

    void updateRole(SysRole role);

    void deleteRole(Long roleId);

    void assignMenus(Long roleId, List<Long> menuIds);

    List<Long> getMenuIdsByRoleId(Long roleId);
}
