package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
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
import com.degel.order.vo.OrderInfoVo;
import com.degel.order.vo.OrderListVo;
import com.degel.order.vo.inner.OrderCreateInnerVo;
import com.degel.order.vo.inner.OrderStatusUpdateInnerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // ==================== C 端内部接口实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInnerOrder(OrderCreateInnerVo vo) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(vo.getOrderNo());
        order.setUserId(vo.getUserId());
        order.setShopId(vo.getShopId());
        order.setTotalAmount(vo.getTotalAmount());
        order.setFreightAmount(vo.getFreightAmount());
        order.setDiscountAmount(vo.getDiscountAmount());
        order.setPayAmount(vo.getPayAmount());
        order.setCouponId(vo.getCouponId());
        order.setPlatformSubsidy(vo.getPlatformSubsidy() != null ? vo.getPlatformSubsidy() : BigDecimal.ZERO);
        order.setShopSubsidy(vo.getShopSubsidy() != null ? vo.getShopSubsidy() : BigDecimal.ZERO);
        order.setStatus(0);
        order.setReceiverName(vo.getReceiverName());
        order.setReceiverPhone(vo.getReceiverPhone());
        order.setReceiverAddress(vo.getReceiverAddress());
        order.setRemark(vo.getRemark());
        order.setAutoCancelTime(vo.getAutoCancelTime());
        save(order);

        if (vo.getItems() != null && !vo.getItems().isEmpty()) {
            Long orderId = order.getId();
            List<OrderItem> items = vo.getItems().stream().map(item -> {
                OrderItem entity = new OrderItem();
                entity.setOrderId(orderId);
                entity.setSpuId(item.getSpuId());
                entity.setSkuId(item.getSkuId());
                entity.setSpuName(item.getSpuName());
                entity.setSkuSpec(item.getSkuSpec());
                entity.setSkuImage(item.getSkuImage());
                entity.setPrice(item.getPrice());
                entity.setQuantity(item.getQuantity());
                entity.setTotalAmount(item.getTotalAmount());
                entity.setCouponDiscount(item.getCouponDiscount() != null ? item.getCouponDiscount() : BigDecimal.ZERO);
                return entity;
            }).collect(Collectors.toList());
            orderItemService.saveBatch(items);
        }
        return order.getId();
    }

    @Override
    public OrderInfoVo getInnerOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            return null;
        }
        return toInnerVo(order, orderItemService.listByOrderId(orderId));
    }

    @Override
    public IPage<OrderInfoVo> pageInnerOrders(Long userId, Integer status, Integer page, Integer pageSize) {
        IPage<OrderInfo> orderPage = page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getUserId, userId)
                        .eq(status != null, OrderInfo::getStatus, status)
                        .orderByDesc(OrderInfo::getCreateTime));

        List<OrderInfo> orders = orderPage.getRecords();
        Page<OrderInfoVo> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        if (orders.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        Map<Long, List<OrderItem>> itemMap = orderItemService
                .listByOrderIds(orders.stream().map(OrderInfo::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        result.setRecords(orders.stream()
                .map(order -> toInnerVo(order, itemMap.getOrDefault(order.getId(), Collections.emptyList())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public void updateInnerStatus(Long orderId, OrderStatusUpdateInnerVo vo) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LambdaUpdateWrapper<OrderInfo> wrapper = new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId);
        if (vo.getStatus() != null) {
            wrapper.set(OrderInfo::getStatus, vo.getStatus());
        }
        if (vo.getPayTime() != null) {
            wrapper.set(OrderInfo::getPayTime, vo.getPayTime());
        }
        if (vo.getReceiveTime() != null) {
            wrapper.set(OrderInfo::getReceiveTime, vo.getReceiveTime());
        }
        if (vo.getCancelTime() != null) {
            wrapper.set(OrderInfo::getCancelTime, vo.getCancelTime());
        }
        if (vo.getPayLogId() != null) {
            wrapper.set(OrderInfo::getPayLogId, vo.getPayLogId());
        }
        if (vo.getCancelReason() != null) {
            wrapper.set(OrderInfo::getCancelReason, vo.getCancelReason());
        }
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderInfoVo> cancelTimeoutOrders() {
        // 1. 查出超时待付款订单
        List<OrderInfo> timeoutOrders = list(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStatus, 0)
                .le(OrderInfo::getAutoCancelTime, LocalDateTime.now()));
        if (timeoutOrders.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 逐个原子取消（WHERE status=0 与并发支付互斥：支付先改 1 则此处 0 行，反之支付侧校验失败）
        List<OrderInfo> cancelled = new ArrayList<>();
        for (OrderInfo order : timeoutOrders) {
            boolean ok = update(new LambdaUpdateWrapper<OrderInfo>()
                    .eq(OrderInfo::getId, order.getId())
                    .eq(OrderInfo::getStatus, 0)
                    .set(OrderInfo::getStatus, 4)
                    .set(OrderInfo::getCancelTime, LocalDateTime.now()));
            if (ok) {
                cancelled.add(order);
            }
        }
        if (cancelled.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 组装 VO（含明细，供调用方恢复库存）
        List<Long> orderIds = cancelled.stream().map(OrderInfo::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemMap = orderItemService.listByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return cancelled.stream()
                .map(order -> toInnerVo(order, itemMap.getOrDefault(order.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private OrderInfoVo toInnerVo(OrderInfo order, List<OrderItem> items) {
        OrderInfoVo vo = new OrderInfoVo();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setShopId(order.getShopId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCouponId(order.getCouponId());
        vo.setPlatformSubsidy(order.getPlatformSubsidy());
        vo.setShopSubsidy(order.getShopSubsidy());
        vo.setStatus(order.getStatus());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setExpressCompany(order.getExpressCompany());
        vo.setExpressNo(order.getExpressNo());
        vo.setPayLogId(order.getPayLogId());
        vo.setCancelReason(order.getCancelReason());
        vo.setAutoCancelTime(order.getAutoCancelTime());
        vo.setCreateTime(order.getCreateTime());
        if (items != null && !items.isEmpty()) {
            vo.setItems(items.stream().map(item -> {
                OrderInfoVo.OrderItemVo itemVo = new OrderInfoVo.OrderItemVo();
                itemVo.setSpuId(item.getSpuId());
                itemVo.setSkuId(item.getSkuId());
                itemVo.setSpuName(item.getSpuName());
                itemVo.setSkuSpec(item.getSkuSpec());
                itemVo.setSkuImage(item.getSkuImage());
                itemVo.setPrice(item.getPrice());
                itemVo.setQuantity(item.getQuantity());
                itemVo.setTotalAmount(item.getTotalAmount());
                return itemVo;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
