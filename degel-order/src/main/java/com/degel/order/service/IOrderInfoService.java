package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderInfo;
import com.degel.order.vo.DeliverVo;
import com.degel.order.vo.OrderDetailVo;
import com.degel.order.vo.OrderListVo;

public interface IOrderInfoService extends IService<OrderInfo> {

    IPage<OrderListVo> pageOrders(IPage<OrderInfo> page, Long shopId, Integer status, String orderNo);

    OrderDetailVo getOrderDetail(Long id, Long shopId);

    void deliver(DeliverVo vo, Long shopId);
}
