package com.degel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysUser;
import com.degel.common.core.dto.UserInfo;

import java.util.List;

public interface ISysUserService extends IService<SysUser> {

    UserInfo getUserInfoByUsername(String username);

    IPage<SysUser> pageUsers(IPage<SysUser> page, SysUser query, Long shopId);

    void createUser(SysUser user, List<Long> roleIds, Long shopId);

    void updateUser(SysUser user, List<Long> roleIds, Long shopId);

    void deleteUser(Long userId, Long shopId);

    List<String> getRoleKeysByUserId(Long userId);

    String resetPassword(Long userId, Long shopId);
}
