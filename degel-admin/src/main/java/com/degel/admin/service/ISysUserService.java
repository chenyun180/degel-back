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

    void evictUserCacheByShopId(Long shopId);

    String resetPassword(Long userId, Long shopId);

    /**
     * INCR auth:tokenver:{userId}：该用户全部已签发 token 立即失效（网关比对 token_version claim）。
     * 用于改密/禁用/删除用户等需要全量吊销的场景（jti 黑名单只能吊销单个 token）。
     */
    void bumpTokenVersion(Long userId);

    /** 店铺维度全量吊销：对该店铺下所有用户 bumpTokenVersion（停用店铺时用） */
    void bumpTokenVersionByShopId(Long shopId);
}
