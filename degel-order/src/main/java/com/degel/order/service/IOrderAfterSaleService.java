package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.vo.AfterSaleHandleVo;

public interface IOrderAfterSaleService extends IService<OrderAfterSale> {

    IPage<OrderAfterSale> pageAfterSales(IPage<OrderAfterSale> page, Long shopId, Integer status, Integer type);

    void handle(AfterSaleHandleVo vo, Long shopId);

    void confirmReceive(Long afterSaleId, Long shopId);
}
