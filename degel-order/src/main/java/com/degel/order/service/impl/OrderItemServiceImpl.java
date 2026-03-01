package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.order.entity.OrderItem;
import com.degel.order.mapper.OrderItemMapper;
import com.degel.order.service.IOrderItemService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements IOrderItemService {

    @Override
    public List<OrderItem> listByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    @Override
    public List<OrderItem> listByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
    }
}
