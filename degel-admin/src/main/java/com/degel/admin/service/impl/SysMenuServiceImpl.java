package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysMenu;
import com.degel.admin.mapper.SysMenuMapper;
import com.degel.admin.service.ISysMenuService;
import com.degel.admin.vo.MetaVo;
import com.degel.admin.vo.RouterVo;
import com.degel.common.core.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    @Override
    public List<SysMenu> listMenuTree() {
        List<SysMenu> allMenus = this.list(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDelFlag, 0)
                .eq(SysMenu::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysMenu::getSort));
        return buildTree(allMenus, 0L);
    }

    @Override
    public List<RouterVo> getRoutersByUserId(Long userId) {
        List<SysMenu> menus = baseMapper.selectMenusByUserId(userId);
        List<SysMenu> menuTree = buildTree(menus, 0L);
        return buildRouters(menuTree);
    }

    @Override
    public List<String> getPermsByUserId(Long userId) {
        return baseMapper.selectPermsByUserId(userId);
    }

    @Override
    public void createMenu(SysMenu menu) {
        this.save(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        this.updateById(menu);
    }

    @Override
    public void deleteMenu(Long menuId) {
        long childCount = this.count(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, menuId)
                .eq(SysMenu::getDelFlag, 0));
        if (childCount > 0) {
            throw new com.degel.common.core.exception.BusinessException("存在子菜单，不允许删除");
        }
        this.removeById(menuId);
    }

    private List<SysMenu> buildTree(List<SysMenu> menus, Long parentId) {
        Map<Long, List<SysMenu>> grouped = menus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        List<SysMenu> roots = grouped.getOrDefault(parentId, new ArrayList<>());
        for (SysMenu root : roots) {
            root.setChildren(buildTree(menus, grouped, root.getId()));
        }
        return roots;
    }

    private List<SysMenu> buildTree(List<SysMenu> allMenus, Map<Long, List<SysMenu>> grouped, Long parentId) {
        List<SysMenu> children = grouped.getOrDefault(parentId, new ArrayList<>());
        for (SysMenu child : children) {
            child.setChildren(buildTree(allMenus, grouped, child.getId()));
        }
        return children;
    }

    private List<RouterVo> buildRouters(List<SysMenu> menus) {
        return buildRouters(menus, "");
    }

    private List<RouterVo> buildRouters(List<SysMenu> menus, String parentPath) {
        List<RouterVo> routers = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (Constants.MENU_TYPE_BUTTON.equals(menu.getMenuType())) {
                continue;
            }
            RouterVo router = new RouterVo();
            String fullPath = parentPath.isEmpty()
                    ? "/" + menu.getPath()
                    : parentPath + "/" + menu.getPath();
            router.setName(capitalize(menu.getPath()));
            router.setPath(fullPath);
            router.setComponent(Constants.MENU_TYPE_DIR.equals(menu.getMenuType()) ? "Layout" : menu.getComponent());
            router.setHidden(menu.getVisible() == 1);
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon()));

            List<SysMenu> childMenus = menu.getChildren();
            if (childMenus != null && !childMenus.isEmpty()) {
                router.setRedirect("noRedirect");
                router.setChildren(buildRouters(childMenus, fullPath));
            }
            routers.add(router);
        }
        return routers;
    }

    private String capitalize(String str) {
        if (!StringUtils.hasText(str)) {
            return "";
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
