package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysMenu;
import com.degel.admin.entity.SysRole;
import com.degel.admin.mapper.SysMenuMapper;
import com.degel.admin.mapper.SysRoleMenuMapper;
import com.degel.admin.mapper.SysRoleMapper;
import com.degel.admin.service.ISysRoleService;
import com.degel.common.core.Constants;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public IPage<SysRole> pageRoles(IPage<SysRole> page, SysRole query, Long shopId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDelFlag, 0)
                .like(StringUtils.hasText(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .like(StringUtils.hasText(query.getRoleKey()), SysRole::getRoleKey, query.getRoleKey())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByAsc(SysRole::getSort);

        if (shopId != null && shopId != 0L) {
            wrapper.eq(SysRole::getRoleType, Constants.ROLE_TYPE_SHOP)
                    .eq(SysRole::getShopId, shopId);
        } else {
            wrapper.eq(StringUtils.hasText(query.getRoleType()), SysRole::getRoleType, query.getRoleType())
                    .eq(query.getShopId() != null, SysRole::getShopId, query.getShopId());
        }

        return this.page(page, wrapper);
    }

    @Override
    public void createRole(SysRole role, Long shopId) {
        if (shopId != null && shopId != 0L) {
            role.setShopId(shopId);
            role.setRoleType(Constants.ROLE_TYPE_SHOP);
        }

        Long effectiveShopId = role.getShopId() != null ? role.getShopId() : 0L;
        long count = this.count(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, role.getRoleKey())
                .eq(SysRole::getShopId, effectiveShopId)
                .eq(SysRole::getDelFlag, 0));
        if (count > 0) {
            throw new BusinessException("角色标识已存在");
        }
        this.save(role);
    }

    @Override
    public void updateRole(SysRole role, Long shopId) {
        if (shopId != null && shopId != 0L) {
            SysRole existing = this.getById(role.getId());
            if (existing == null) {
                throw new BusinessException("角色不存在");
            }
            if (!shopId.equals(existing.getShopId())) {
                throw new BusinessException("无权修改其他店铺的角色");
            }
        }
        this.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId, Long shopId) {
        SysRole existing = this.getById(roleId);
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }

        if (shopId != null && shopId != 0L) {
            if (!shopId.equals(existing.getShopId())) {
                throw new BusinessException("无权删除其他店铺的角色");
            }
        }

        String roleKey = existing.getRoleKey();
        if (Constants.ROLE_KEY_SHOP_ADMIN.equals(roleKey) || Constants.ROLE_KEY_SHOP_STAFF.equals(roleKey)) {
            throw new BusinessException("内置角色不允许删除");
        }

        this.removeById(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds, Long shopId) {
        if (shopId != null && shopId != 0L) {
            SysRole existing = this.getById(roleId);
            if (existing == null || !shopId.equals(existing.getShopId())) {
                throw new BusinessException("无权操作其他店铺的角色");
            }

            if (menuIds != null && !menuIds.isEmpty()) {
                List<SysMenu> menus = menuMapper.selectBatchIds(menuIds);
                for (SysMenu menu : menus) {
                    if (StringUtils.hasText(menu.getPerms()) && !menu.getPerms().startsWith("shop:")) {
                        throw new BusinessException("店铺角色只能分配 shop: 开头的权限: " + menu.getPerms());
                    }
                }
            }
        }

        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, menuIds);
        }
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }
}
