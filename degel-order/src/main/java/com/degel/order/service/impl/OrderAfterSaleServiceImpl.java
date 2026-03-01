package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.vo.AfterSaleHandleVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleServiceImpl extends ServiceImpl<OrderAfterSaleMapper, OrderAfterSale> implements IOrderAfterSaleService {

    @Override
    public IPage<OrderAfterSale> pageAfterSales(IPage<OrderAfterSale> page, Long shopId, Integer status, Integer type) {
        LambdaQueryWrapper<OrderAfterSale> wrapper = new LambdaQueryWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getShopId, shopId)
                .eq(status != null, OrderAfterSale::getStatus, status)
                .eq(type != null, OrderAfterSale::getType, type)
                .orderByDesc(OrderAfterSale::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(AfterSaleHandleVo vo, Long shopId) {
        OrderAfterSale afterSale = getById(vo.getAfterSaleId());
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 0) {
            throw new BusinessException("售后单状态不允许操作");
        }

        LambdaUpdateWrapper<OrderAfterSale> updateWrapper = new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, vo.getAfterSaleId());

        if ("agree".equals(vo.getAction())) {
            if (afterSale.getType() == 1) {
                updateWrapper.set(OrderAfterSale::getStatus, 3);
            } else {
                updateWrapper.set(OrderAfterSale::getStatus, 1);
            }
        } else if ("reject".equals(vo.getAction())) {
            updateWrapper.set(OrderAfterSale::getStatus, 5);
        } else {
            throw new BusinessException("无效的操作类型");
        }

        if (vo.getMerchantRemark() != null) {
            updateWrapper.set(OrderAfterSale::getMerchantRemark, vo.getMerchantRemark());
        }
        update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long afterSaleId, Long shopId) {
        OrderAfterSale afterSale = getById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 2) {
            throw new BusinessException("售后单状态不允许确认收货");
        }

        update(new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, afterSaleId)
                .set(OrderAfterSale::getStatus, 3));
    }
}
