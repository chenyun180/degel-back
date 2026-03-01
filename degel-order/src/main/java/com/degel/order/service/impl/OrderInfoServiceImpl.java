package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.OrderInfo;
import com.degel.order.entity.OrderItem;
import com.degel.order.mapper.OrderInfoMapper;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.service.IOrderInfoService;
import com.degel.order.service.IOrderItemService;
import com.degel.order.vo.DeliverVo;
import com.degel.order.vo.OrderDetailVo;
import com.degel.order.vo.OrderListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {

    private final IOrderItemService orderItemService;
    private final IOrderAfterSaleService orderAfterSaleService;

    @Override
    public IPage<OrderListVo> pageOrders(IPage<OrderInfo> page, Long shopId, Integer status, String orderNo) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getShopId, shopId)
                .eq(status != null, OrderInfo::getStatus, status)
                .like(orderNo != null && !orderNo.isEmpty(), OrderInfo::getOrderNo, orderNo)
                .orderByDesc(OrderInfo::getCreateTime);

        IPage<OrderInfo> orderPage = page(page, wrapper);

        List<OrderInfo> orders = orderPage.getRecords();
        if (orders.isEmpty()) {
            Page<OrderListVo> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<Long> orderIds = orders.stream().map(OrderInfo::getId).collect(Collectors.toList());
        List<OrderItem> allItems = orderItemService.listByOrderIds(orderIds);
        Map<Long, List<OrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<OrderListVo> voList = orders.stream().map(order -> {
            OrderListVo vo = new OrderListVo();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setShopId(order.getShopId());
            vo.setPayAmount(order.getPayAmount());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());

            List<OrderItem> items = itemMap.getOrDefault(order.getId(), Collections.emptyList());
            vo.setItemCount(items.size());
            if (!items.isEmpty()) {
                OrderItem first = items.get(0);
                vo.setFirstItemName(first.getSpuName());
                vo.setFirstItemImage(first.getSkuImage());
            }
            return vo;
        }).collect(Collectors.toList());

        Page<OrderListVo> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public OrderDetailVo getOrderDetail(Long id, Long shopId) {
        OrderInfo order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getShopId().equals(shopId)) {
            throw new BusinessException("无权查看该订单");
        }

        OrderDetailVo vo = new OrderDetailVo();
        vo.setOrder(order);
        vo.setItems(orderItemService.listByOrderId(id));
        vo.setAfterSales(orderAfterSaleService.list(
                new LambdaQueryWrapper<OrderAfterSale>().eq(OrderAfterSale::getOrderId, id)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(DeliverVo vo, Long shopId) {
        OrderInfo order = getById(vo.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("订单状态不允许发货");
        }

        update(new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, vo.getOrderId())
                .set(OrderInfo::getStatus, 2)
                .set(OrderInfo::getShipTime, LocalDateTime.now())
                .set(OrderInfo::getExpressCompany, vo.getExpressCompany())
                .set(OrderInfo::getExpressNo, vo.getExpressNo()));
    }
}
