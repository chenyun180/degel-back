package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysRole;
import com.degel.admin.entity.SysUser;
import com.degel.admin.mapper.*;
import com.degel.admin.service.ISysUserService;
import com.degel.common.core.Constants;
import com.degel.common.core.dto.UserInfo;
import com.degel.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_CACHE_PREFIX = "admin:user:info:";
    private static final long USER_CACHE_TTL = 1800L;

    @Override
    public UserInfo getUserInfoByUsername(String username) {
        String cacheKey = USER_CACHE_PREFIX + username;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, UserInfo.class);
            }
        } catch (Exception e) {
            log.warn("Read user cache failed: {}", e.getMessage());
        }

        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDelFlag, 0));
        if (user == null) {
            return null;
        }

        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setPassword(user.getPassword());
        info.setNickname(user.getNickname());
        info.setStatus(user.getStatus());
        info.setShopId(user.getShopId());

        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            info.setRoles(roles.stream().map(SysRole::getRoleKey).collect(Collectors.toList()));

            List<String> perms = menuMapper.selectPermsByUserId(user.getId());
            info.setPermissions(perms != null ? perms : new ArrayList<>());
        } else {
            info.setRoles(Collections.emptyList());
            info.setPermissions(Collections.emptyList());
        }

        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(info), USER_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Write user cache failed: {}", e.getMessage());
        }
        return info;
    }

    @Override
    public IPage<SysUser> pageUsers(IPage<SysUser> page, SysUser query, Long shopId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDelFlag, 0)
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUser::getNickname, query.getNickname())
                .like(StringUtils.hasText(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        if (shopId != null && shopId != 0L) {
            wrapper.eq(SysUser::getShopId, shopId);
        } else {
            wrapper.eq(query.getShopId() != null, SysUser::getShopId, query.getShopId());
        }

        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(SysUser user, List<Long> roleIds, Long shopId) {
        if (shopId != null && shopId != 0L) {
            user.setShopId(shopId);
        }

        long count = this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername())
                .eq(SysUser::getDelFlag, 0));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        this.save(user);

        if (roleIds != null && !roleIds.isEmpty()) {
            validateRoleScope(user.getShopId(), roleIds);
            userRoleMapper.insertBatch(user.getId(), roleIds);
        }
    }

    private void validateRoleScope(Long userShopId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        long expectedShopId = userShopId != null ? userShopId : 0L;
        for (SysRole role : roles) {
            long roleShopId = role.getShopId() != null ? role.getShopId() : 0L;
            if (expectedShopId == 0L) {
                // 平台用户只能绑定平台角色
                if (!Constants.ROLE_TYPE_PLATFORM.equals(role.getRoleType())) {
                    throw new BusinessException("平台用户不能分配店铺角色: " + role.getRoleName());
                }
            } else {
                // 店铺用户可绑定：全局预设的店铺角色（shopId=0, roleType=shop）
                boolean isGlobalShopRole = Constants.ROLE_TYPE_SHOP.equals(role.getRoleType()) && roleShopId == 0L;
                if (!isGlobalShopRole) {
                    throw new BusinessException("只能分配店铺角色: " + role.getRoleName());
                }
            }
        }
    }

    private void checkShopOwnership(SysUser existing, Long shopId) {
        if (shopId != null && shopId != 0L) {
            if (existing == null) {
                throw new BusinessException("用户不存在");
            }
            Long userShopId = existing.getShopId() != null ? existing.getShopId() : 0L;
            if (!shopId.equals(userShopId)) {
                throw new BusinessException("无权操作其他店铺的用户");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(SysUser user, List<Long> roleIds, Long shopId) {
        SysUser existing = this.getById(user.getId());
        checkShopOwnership(existing, shopId);

        user.setPassword(null);
        this.updateById(user);

        if (roleIds != null) {
            userRoleMapper.deleteByUserId(user.getId());
            if (!roleIds.isEmpty()) {
                userRoleMapper.insertBatch(user.getId(), roleIds);
            }
        }

        if (existing != null) {
            redisTemplate.delete(USER_CACHE_PREFIX + existing.getUsername());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long shopId) {
        SysUser existing = this.getById(userId);
        checkShopOwnership(existing, shopId);

        this.removeById(userId);
        userRoleMapper.deleteByUserId(userId);

        if (existing != null) {
            redisTemplate.delete(USER_CACHE_PREFIX + existing.getUsername());
        }
    }

    @Override
    public String resetPassword(Long userId, Long shopId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        checkShopOwnership(user, shopId);

        String newPassword = Constants.DEFAULT_PASSWORD;
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(update);

        redisTemplate.delete(USER_CACHE_PREFIX + user.getUsername());
        return newPassword;
    }

    @Override
    public List<String> getRoleKeysByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleKey)
                .collect(Collectors.toList());
    }
}
