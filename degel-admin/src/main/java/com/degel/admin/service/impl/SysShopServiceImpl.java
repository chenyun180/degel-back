package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysRole;
import com.degel.admin.entity.SysShop;
import com.degel.admin.entity.SysUser;
import com.degel.admin.mapper.SysShopMapper;
import com.degel.admin.mapper.SysUserRoleMapper;
import com.degel.admin.service.ISysRoleService;
import com.degel.admin.service.ISysShopService;
import com.degel.admin.service.ISysUserService;
import com.degel.common.core.Constants;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SysShopServiceImpl extends ServiceImpl<SysShopMapper, SysShop> implements ISysShopService {

    private final ISysRoleService roleService;
    private final ISysUserService userService;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<SysShop> pageShops(IPage<SysShop> page, SysShop query) {
        LambdaQueryWrapper<SysShop> wrapper = new LambdaQueryWrapper<SysShop>()
                .eq(SysShop::getDelFlag, 0)
                .like(StringUtils.hasText(query.getShopName()), SysShop::getShopName, query.getShopName())
                .eq(query.getStatus() != null, SysShop::getStatus, query.getStatus())
                .orderByDesc(SysShop::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> createShop(SysShop shop) {
        this.save(shop);
        Long shopId = shop.getId();

        // 查询全局预设的店铺角色（shopId=0，所有店铺账号共用）
        SysRole shopRole = roleService.getOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, Constants.ROLE_KEY_SHOP)
                .eq(SysRole::getShopId, 0L)
                .eq(SysRole::getDelFlag, 0));
        if (shopRole == null) {
            throw new BusinessException("店铺角色不存在，请联系管理员");
        }

        // 创建店铺账号
        String username = shop.getContactPhone();
        if (!StringUtils.hasText(username)) {
            username = "shop_" + shopId;
        }

        long existCount = userService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDelFlag, 0));
        if (existCount > 0) {
            throw new BusinessException("用户名 " + username + " 已存在，请更换联系电话");
        }

        String rawPassword = Constants.DEFAULT_PASSWORD;

        SysUser owner = new SysUser();
        owner.setUsername(username);
        owner.setPassword(passwordEncoder.encode(rawPassword));
        owner.setNickname(shop.getContactName());
        owner.setPhone(shop.getContactPhone());
        owner.setStatus(0);
        owner.setShopId(shopId);
        userService.save(owner);
        userRoleMapper.insertBatch(owner.getId(), Arrays.asList(shopRole.getId()));

        Map<String, String> result = new HashMap<>(4);
        result.put("username", username);
        result.put("password", rawPassword);
        return result;
    }

    @Override
    public void updateShop(SysShop shop) {
        this.updateById(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long shopId, Integer status) {
        SysShop shop = new SysShop();
        shop.setId(shopId);
        shop.setStatus(status);
        this.updateById(shop);

        // 停用店铺时同步禁用该店铺下所有用户，启用时同步恢复
        userService.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getShopId, shopId)
                .eq(SysUser::getDelFlag, 0)
                .set(SysUser::getStatus, status));
    }

}
