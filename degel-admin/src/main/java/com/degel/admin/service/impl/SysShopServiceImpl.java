package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysMenu;
import com.degel.admin.entity.SysRole;
import com.degel.admin.entity.SysShop;
import com.degel.admin.entity.SysUser;
import com.degel.admin.mapper.SysRoleMenuMapper;
import com.degel.admin.mapper.SysShopMapper;
import com.degel.admin.mapper.SysUserRoleMapper;
import com.degel.admin.service.ISysMenuService;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysShopServiceImpl extends ServiceImpl<SysShopMapper, SysShop> implements ISysShopService {

    private final ISysRoleService roleService;
    private final ISysMenuService menuService;
    private final ISysUserService userService;
    private final SysRoleMenuMapper roleMenuMapper;
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

        List<SysMenu> allShopMenus = menuService.list(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDelFlag, 0)
                .likeRight(SysMenu::getPerms, "shop:"));

        Set<Long> allShopMenuIds = allShopMenus.stream()
                .map(SysMenu::getId)
                .collect(Collectors.toSet());

        // 创建店长角色
        SysRole adminRole = new SysRole();
        adminRole.setRoleName("店长");
        adminRole.setRoleKey(Constants.ROLE_KEY_SHOP_ADMIN);
        adminRole.setRoleType(Constants.ROLE_TYPE_SHOP);
        adminRole.setShopId(shopId);
        adminRole.setSort(1);
        adminRole.setStatus(0);
        adminRole.setRemark("店铺管理员，拥有店铺全部权限");
        roleService.save(adminRole);

        if (!allShopMenuIds.isEmpty()) {
            roleMenuMapper.insertBatch(adminRole.getId(), new ArrayList<>(allShopMenuIds));
        }

        // 创建店员角色（只读基础权限）
        SysRole staffRole = new SysRole();
        staffRole.setRoleName("店员");
        staffRole.setRoleKey(Constants.ROLE_KEY_SHOP_STAFF);
        staffRole.setRoleType(Constants.ROLE_TYPE_SHOP);
        staffRole.setShopId(shopId);
        staffRole.setSort(2);
        staffRole.setStatus(0);
        staffRole.setRemark("基础权限");
        roleService.save(staffRole);

        Set<String> staffPerms = new HashSet<>(Arrays.asList(
                "shop:dir:workspace", "shop:dashboard",
                "shop:dir:product", "shop:product:list", "shop:category:list",
                "shop:dir:order", "shop:order:list", "shop:order:ship",
                "shop:dir:setting", "shop:setting:info"
        ));
        List<Long> staffMenuIds = allShopMenus.stream()
                .filter(m -> staffPerms.contains(m.getPerms()))
                .map(SysMenu::getId)
                .collect(Collectors.toList());
        if (!staffMenuIds.isEmpty()) {
            roleMenuMapper.insertBatch(staffRole.getId(), staffMenuIds);
        }

        // 创建店铺主账号
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
        userRoleMapper.insertBatch(owner.getId(), Arrays.asList(adminRole.getId()));

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
