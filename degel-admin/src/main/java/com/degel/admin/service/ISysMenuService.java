package com.degel.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysMenu;
import com.degel.admin.vo.RouterVo;

import java.util.List;

public interface ISysMenuService extends IService<SysMenu> {

    List<SysMenu> listMenuTree();

    List<RouterVo> getRoutersByUserId(Long userId);

    List<String> getPermsByUserId(Long userId);

    void createMenu(SysMenu menu);

    void updateMenu(SysMenu menu);

    void deleteMenu(Long menuId);
}
