package com.degel.admin.config;

import com.degel.admin.entity.SysMenu;
import com.degel.admin.entity.SysRole;
import com.degel.admin.entity.SysUser;
import com.degel.admin.mapper.SysRoleMenuMapper;
import com.degel.admin.mapper.SysUserRoleMapper;
import com.degel.admin.service.ISysMenuService;
import com.degel.admin.service.ISysRoleService;
import com.degel.admin.service.ISysUserService;
import com.degel.common.core.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitRunner implements ApplicationRunner {

    private final ISysUserService userService;
    private final ISysRoleService roleService;
    private final ISysMenuService menuService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (userService.count() > 0) {
            log.info("数据已初始化，跳过");
            return;
        }
        log.info("开始初始化默认数据...");

        List<SysMenu> menus = initMenus();
        SysRole adminRole = initRoles(menus);
        initAdmin(adminRole);

        log.info("默认数据初始化完成！");
    }

    private List<SysMenu> initMenus() {
        List<SysMenu> allMenus = new ArrayList<>();

        // 系统管理目录
        SysMenu systemDir = createMenu(0L, "系统管理", "system", "", "", "SettingOutlined", Constants.MENU_TYPE_DIR, 1);
        menuService.save(systemDir);
        allMenus.add(systemDir);

        // 用户管理
        SysMenu userMenu = createMenu(systemDir.getId(), "用户管理", "user", "./System/User", "system:user:list", "UserOutlined", Constants.MENU_TYPE_MENU, 1);
        menuService.save(userMenu);
        allMenus.add(userMenu);
        allMenus.addAll(saveButtons(userMenu.getId(), "system:user", 1));

        // 角色管理
        SysMenu roleMenu = createMenu(systemDir.getId(), "角色管理", "role", "./System/Role", "system:role:list", "TeamOutlined", Constants.MENU_TYPE_MENU, 2);
        menuService.save(roleMenu);
        allMenus.add(roleMenu);
        allMenus.addAll(saveButtons(roleMenu.getId(), "system:role", 2));

        // 菜单管理
        SysMenu menuMenu = createMenu(systemDir.getId(), "菜单管理", "menu", "./System/Menu", "system:menu:list", "MenuOutlined", Constants.MENU_TYPE_MENU, 3);
        menuService.save(menuMenu);
        allMenus.add(menuMenu);
        allMenus.addAll(saveButtons(menuMenu.getId(), "system:menu", 3));

        // 店铺管理
        SysMenu shopMenu = createMenu(systemDir.getId(), "店铺管理", "shop", "./System/Shop", "system:shop:list", "ShopOutlined", Constants.MENU_TYPE_MENU, 4);
        menuService.save(shopMenu);
        allMenus.add(shopMenu);
        allMenus.addAll(saveButtons(shopMenu.getId(), "system:shop", 4));

        return allMenus;
    }

    private List<SysMenu> saveButtons(Long parentId, String prefix, int sortBase) {
        List<SysMenu> buttons = new ArrayList<>();
        String[][] ops = {{"新增", "add"}, {"修改", "edit"}, {"删除", "remove"}};
        for (int i = 0; i < ops.length; i++) {
            SysMenu btn = createMenu(parentId, ops[i][0], "", "", prefix + ":" + ops[i][1], "", Constants.MENU_TYPE_BUTTON, i + 1);
            menuService.save(btn);
            buttons.add(btn);
        }
        return buttons;
    }

    private SysRole initRoles(List<SysMenu> menus) {
        SysRole adminRole = new SysRole();
        adminRole.setRoleName("超级管理员");
        adminRole.setRoleKey("admin");
        adminRole.setSort(1);
        adminRole.setStatus(0);
        adminRole.setRemark("拥有所有权限");
        roleService.save(adminRole);

        List<Long> menuIds = menus.stream().map(SysMenu::getId).collect(Collectors.toList());
        roleMenuMapper.insertBatch(adminRole.getId(), menuIds);

        SysRole commonRole = new SysRole();
        commonRole.setRoleName("普通用户");
        commonRole.setRoleKey("common");
        commonRole.setSort(2);
        commonRole.setStatus(0);
        commonRole.setRemark("基本权限");
        roleService.save(commonRole);

        return adminRole;
    }

    private void initAdmin(SysRole adminRole) {
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setStatus(0);
        admin.setShopId(0L);
        userService.save(admin);

        userRoleMapper.insertBatch(admin.getId(), Arrays.asList(adminRole.getId()));
    }

    private SysMenu createMenu(Long parentId, String name, String path, String component,
                               String perms, String icon, String menuType, int sort) {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setPerms(perms);
        menu.setIcon(icon);
        menu.setMenuType(menuType);
        menu.setSort(sort);
        menu.setVisible(0);
        menu.setStatus(0);
        return menu;
    }
}
