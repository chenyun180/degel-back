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

import java.util.*;
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

        List<SysMenu> platformMenus = initPlatformMenus();
        List<SysMenu> shopMenus = initShopMenus();
        initRolesAndAdmin(platformMenus, shopMenus);

        log.info("默认数据初始化完成！");
    }

    // ==================== 平台端菜单 ====================

    private List<SysMenu> initPlatformMenus() {
        List<SysMenu> allMenus = new ArrayList<>();

        // 系统管理目录
        SysMenu systemDir = createMenu(0L, "系统管理", "system", "", "", "SettingOutlined", Constants.MENU_TYPE_DIR, 1);
        menuService.save(systemDir);
        allMenus.add(systemDir);

        SysMenu userMenu = createMenu(systemDir.getId(), "用户管理", "user", "./System/User", "system:user:list", "UserOutlined", Constants.MENU_TYPE_MENU, 1);
        menuService.save(userMenu);
        allMenus.add(userMenu);
        allMenus.addAll(saveButtons(userMenu.getId(), "system:user"));

        SysMenu roleMenu = createMenu(systemDir.getId(), "角色管理", "role", "./System/Role", "system:role:list", "TeamOutlined", Constants.MENU_TYPE_MENU, 2);
        menuService.save(roleMenu);
        allMenus.add(roleMenu);
        allMenus.addAll(saveButtons(roleMenu.getId(), "system:role"));

        SysMenu menuMenu = createMenu(systemDir.getId(), "菜单管理", "menu", "./System/Menu", "system:menu:list", "MenuOutlined", Constants.MENU_TYPE_MENU, 3);
        menuService.save(menuMenu);
        allMenus.add(menuMenu);
        allMenus.addAll(saveButtons(menuMenu.getId(), "system:menu"));

        SysMenu shopMenu = createMenu(systemDir.getId(), "店铺管理", "shop", "./System/Shop", "system:shop:list", "ShopOutlined", Constants.MENU_TYPE_MENU, 4);
        menuService.save(shopMenu);
        allMenus.add(shopMenu);
        allMenus.addAll(saveButtons(shopMenu.getId(), "system:shop"));

        // 商品管理目录（平台端：审核+类目）
        SysMenu productDir = createMenu(0L, "商品管理", "product", "", "", "ShoppingOutlined", Constants.MENU_TYPE_DIR, 2);
        menuService.save(productDir);
        allMenus.add(productDir);

        SysMenu auditMenu = createMenu(productDir.getId(), "商品审核", "audit", "./Product/Audit", "product:spu:audit", "AuditOutlined", Constants.MENU_TYPE_MENU, 1);
        menuService.save(auditMenu);
        allMenus.add(auditMenu);

        SysMenu categoryMenu = createMenu(productDir.getId(), "类目管理", "category", "./Product/Category", "product:category:list", "AppstoreOutlined", Constants.MENU_TYPE_MENU, 2);
        menuService.save(categoryMenu);
        allMenus.add(categoryMenu);
        allMenus.addAll(saveButtons(categoryMenu.getId(), "product:category"));

        return allMenus;
    }

    // ==================== 店铺端菜单 ====================

    private List<SysMenu> initShopMenus() {
        List<SysMenu> allMenus = new ArrayList<>();

        // 店铺工作台（顶级目录）
        SysMenu shopWorkspace = createMenu(0L, "店铺工作台", "shop-workspace", "", "shop:dir:workspace", "HomeOutlined", Constants.MENU_TYPE_DIR, 10);
        menuService.save(shopWorkspace);
        allMenus.add(shopWorkspace);

        // -- 工作台
        SysMenu dashboard = createMenu(shopWorkspace.getId(), "工作台", "shop-dashboard", "./Shop/Dashboard", "shop:dashboard", "DashboardOutlined", Constants.MENU_TYPE_MENU, 0);
        menuService.save(dashboard);
        allMenus.add(dashboard);

        // -- 商品管理目录
        SysMenu productDir = createMenu(shopWorkspace.getId(), "商品管理", "shop-product-dir", "", "shop:dir:product", "ShoppingOutlined", Constants.MENU_TYPE_DIR, 1);
        menuService.save(productDir);
        allMenus.add(productDir);

        SysMenu productList = createMenu(productDir.getId(), "商品列表", "shop-product-list", "./Shop/Product/List", "shop:product:list", "", Constants.MENU_TYPE_MENU, 1);
        menuService.save(productList);
        allMenus.add(productList);
        allMenus.addAll(saveButtons(productList.getId(), "shop:product"));
        SysMenu submitBtn = createMenu(productList.getId(), "提交审核", "", "", "shop:product:submit", "", Constants.MENU_TYPE_BUTTON, 4);
        menuService.save(submitBtn);
        allMenus.add(submitBtn);
        SysMenu onoffBtn = createMenu(productList.getId(), "上下架", "", "", "shop:product:onoff", "", Constants.MENU_TYPE_BUTTON, 5);
        menuService.save(onoffBtn);
        allMenus.add(onoffBtn);

        SysMenu productCreate = createMenu(productDir.getId(), "发布商品", "shop-product-create", "./Shop/Product/Create", "shop:product:add", "", Constants.MENU_TYPE_MENU, 2);
        productCreate.setVisible(1);
        menuService.save(productCreate);
        allMenus.add(productCreate);

        SysMenu categoryMenu = createMenu(productDir.getId(), "商品分类", "shop-category", "./Shop/Category", "shop:category:list", "", Constants.MENU_TYPE_MENU, 3);
        menuService.save(categoryMenu);
        allMenus.add(categoryMenu);

        // -- 订单管理目录
        SysMenu orderDir = createMenu(shopWorkspace.getId(), "订单管理", "shop-order-dir", "", "shop:dir:order", "OrderedListOutlined", Constants.MENU_TYPE_DIR, 2);
        menuService.save(orderDir);
        allMenus.add(orderDir);

        SysMenu orderList = createMenu(orderDir.getId(), "全部订单", "shop-order-list", "./Shop/Order/List", "shop:order:list", "", Constants.MENU_TYPE_MENU, 1);
        menuService.save(orderList);
        allMenus.add(orderList);
        SysMenu orderDetailBtn = createMenu(orderList.getId(), "详情", "", "", "shop:order:detail", "", Constants.MENU_TYPE_BUTTON, 1);
        menuService.save(orderDetailBtn);
        allMenus.add(orderDetailBtn);
        SysMenu orderDeliverBtn = createMenu(orderList.getId(), "发货", "", "", "shop:order:deliver", "", Constants.MENU_TYPE_BUTTON, 2);
        menuService.save(orderDeliverBtn);
        allMenus.add(orderDeliverBtn);
        SysMenu orderExportBtn = createMenu(orderList.getId(), "导出", "", "", "shop:order:export", "", Constants.MENU_TYPE_BUTTON, 3);
        menuService.save(orderExportBtn);
        allMenus.add(orderExportBtn);

        SysMenu orderShip = createMenu(orderDir.getId(), "待发货", "shop-order-ship", "./Shop/Order/Ship", "shop:order:ship", "", Constants.MENU_TYPE_MENU, 2);
        menuService.save(orderShip);
        allMenus.add(orderShip);

        SysMenu afterSale = createMenu(orderDir.getId(), "售后管理", "shop-aftersale", "./Shop/AfterSale", "shop:aftersale:list", "", Constants.MENU_TYPE_MENU, 3);
        menuService.save(afterSale);
        allMenus.add(afterSale);
        SysMenu afterSaleHandleBtn = createMenu(afterSale.getId(), "处理", "", "", "shop:aftersale:handle", "", Constants.MENU_TYPE_BUTTON, 1);
        menuService.save(afterSaleHandleBtn);
        allMenus.add(afterSaleHandleBtn);

        // -- 数据统计目录
        SysMenu statsDir = createMenu(shopWorkspace.getId(), "数据统计", "shop-stats-dir", "", "shop:dir:stats", "BarChartOutlined", Constants.MENU_TYPE_DIR, 3);
        menuService.save(statsDir);
        allMenus.add(statsDir);

        SysMenu statsHot = createMenu(statsDir.getId(), "热销榜", "shop-stats-hot", "./Shop/Stats/Hot", "shop:stats:hot", "", Constants.MENU_TYPE_MENU, 1);
        menuService.save(statsHot);
        allMenus.add(statsHot);

        SysMenu statsVisitor = createMenu(statsDir.getId(), "访客榜", "shop-stats-visitor", "./Shop/Stats/Visitor", "shop:stats:visitor", "", Constants.MENU_TYPE_MENU, 2);
        menuService.save(statsVisitor);
        allMenus.add(statsVisitor);

        // -- 店铺设置目录
        SysMenu settingDir = createMenu(shopWorkspace.getId(), "店铺设置", "shop-setting", "", "shop:dir:setting", "SettingOutlined", Constants.MENU_TYPE_DIR, 4);
        menuService.save(settingDir);
        allMenus.add(settingDir);

        SysMenu infoMenu = createMenu(settingDir.getId(), "店铺信息", "shop-info", "./Shop/Info", "shop:setting:info", "", Constants.MENU_TYPE_MENU, 1);
        menuService.save(infoMenu);
        allMenus.add(infoMenu);

        SysMenu staffMenu = createMenu(settingDir.getId(), "员工管理", "shop-staff", "./Shop/Staff", "shop:staff:list", "", Constants.MENU_TYPE_MENU, 2);
        menuService.save(staffMenu);
        allMenus.add(staffMenu);
        allMenus.addAll(saveButtons(staffMenu.getId(), "shop:staff"));

        SysMenu shopRoleMenu = createMenu(settingDir.getId(), "角色管理", "shop-role", "./Shop/Role", "shop:role:list", "", Constants.MENU_TYPE_MENU, 3);
        menuService.save(shopRoleMenu);
        allMenus.add(shopRoleMenu);
        allMenus.addAll(saveButtons(shopRoleMenu.getId(), "shop:role"));

        return allMenus;
    }

    // ==================== 角色 + 管理员 ====================

    private void initRolesAndAdmin(List<SysMenu> platformMenus, List<SysMenu> shopMenus) {
        // 超级管理员 — 拥有全部平台菜单
        SysRole adminRole = createRole("超级管理员", "admin", Constants.ROLE_TYPE_PLATFORM, 0L, 1, "拥有所有权限");
        roleService.save(adminRole);
        List<Long> allMenuIds = platformMenus.stream().map(SysMenu::getId).collect(Collectors.toList());
        roleMenuMapper.insertBatch(adminRole.getId(), allMenuIds);

        // 平台运营 — 拥有商品审核+类目管理菜单
        SysRole operatorRole = createRole("平台运营", "operator", Constants.ROLE_TYPE_PLATFORM, 0L, 2, "商品审核与类目管理");
        roleService.save(operatorRole);
        List<Long> operatorMenuIds = platformMenus.stream()
                .filter(m -> m.getPerms() != null && (m.getPerms().startsWith("product:") || m.getPerms().isEmpty()))
                .filter(m -> {
                    String perms = m.getPerms();
                    return perms.startsWith("product:") || "商品管理".equals(m.getMenuName());
                })
                .map(SysMenu::getId)
                .collect(Collectors.toList());
        if (!operatorMenuIds.isEmpty()) {
            roleMenuMapper.insertBatch(operatorRole.getId(), operatorMenuIds);
        }

        // 普通用户（平台）
        SysRole commonRole = createRole("普通用户", "common", Constants.ROLE_TYPE_PLATFORM, 0L, 3, "基本权限");
        roleService.save(commonRole);

        // 店长角色（模板，shopId=0，创建店铺时会按需复制并绑定具体 shopId）
        SysRole shopAdminRole = createRole("店长", Constants.ROLE_KEY_SHOP_ADMIN, Constants.ROLE_TYPE_SHOP, 0L, 1, "店铺管理员，拥有本店所有权限");
        roleService.save(shopAdminRole);
        List<Long> shopMenuIds = shopMenus.stream().map(SysMenu::getId).collect(Collectors.toList());
        roleMenuMapper.insertBatch(shopAdminRole.getId(), shopMenuIds);

        // 店员角色（模板）
        SysRole shopStaffRole = createRole("店员", Constants.ROLE_KEY_SHOP_STAFF, Constants.ROLE_TYPE_SHOP, 0L, 2, "店铺普通员工");
        roleService.save(shopStaffRole);
        Set<String> staffPerms = new HashSet<>(Arrays.asList(
                "shop:dir:workspace", "shop:dashboard",
                "shop:dir:product", "shop:product:list", "shop:category:list",
                "shop:dir:order", "shop:order:list", "shop:order:ship",
                "shop:dir:setting", "shop:setting:info"
        ));
        List<Long> staffMenuIds = shopMenus.stream()
                .filter(m -> staffPerms.contains(m.getPerms()))
                .map(SysMenu::getId)
                .collect(Collectors.toList());
        if (!staffMenuIds.isEmpty()) {
            roleMenuMapper.insertBatch(shopStaffRole.getId(), staffMenuIds);
        }

        // 超级管理员账号
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setStatus(0);
        admin.setShopId(0L);
        userService.save(admin);
        userRoleMapper.insertBatch(admin.getId(), Arrays.asList(adminRole.getId()));
    }

    // ==================== 工具方法 ====================

    private List<SysMenu> saveButtons(Long parentId, String prefix) {
        List<SysMenu> buttons = new ArrayList<>();
        String[][] ops = {{"新增", "add"}, {"修改", "edit"}, {"删除", "remove"}};
        for (int i = 0; i < ops.length; i++) {
            SysMenu btn = createMenu(parentId, ops[i][0], "", "", prefix + ":" + ops[i][1], "", Constants.MENU_TYPE_BUTTON, i + 1);
            menuService.save(btn);
            buttons.add(btn);
        }
        return buttons;
    }

    private SysRole createRole(String name, String key, String roleType, Long shopId, int sort, String remark) {
        SysRole role = new SysRole();
        role.setRoleName(name);
        role.setRoleKey(key);
        role.setRoleType(roleType);
        role.setShopId(shopId);
        role.setSort(sort);
        role.setStatus(0);
        role.setRemark(remark);
        return role;
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
