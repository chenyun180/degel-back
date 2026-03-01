package com.degel.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderItem;

import java.util.List;

public interface IOrderItemService extends IService<OrderItem> {

    List<OrderItem> listByOrderId(Long orderId);

    List<OrderItem> listByOrderIds(List<Long> orderIds);
}
