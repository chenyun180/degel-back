package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysShop;
import com.degel.admin.mapper.SysShopMapper;
import com.degel.admin.service.ISysShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SysShopServiceImpl extends ServiceImpl<SysShopMapper, SysShop> implements ISysShopService {

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
    public void createShop(SysShop shop) {
        this.save(shop);
    }

    @Override
    public void updateShop(SysShop shop) {
        this.updateById(shop);
    }

    @Override
    public void toggleStatus(Long shopId, Integer status) {
        SysShop shop = new SysShop();
        shop.setId(shopId);
        shop.setStatus(status);
        this.updateById(shop);
    }
}
