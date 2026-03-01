package com.degel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysShop;

import java.util.Map;

public interface ISysShopService extends IService<SysShop> {

    IPage<SysShop> pageShops(IPage<SysShop> page, SysShop query);

    Map<String, String> createShop(SysShop shop);

    void updateShop(SysShop shop);

    void toggleStatus(Long shopId, Integer status);
}
