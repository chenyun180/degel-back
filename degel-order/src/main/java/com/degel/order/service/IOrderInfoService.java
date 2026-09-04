package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderInfo;
import com.degel.order.vo.DeliverVo;
import com.degel.order.vo.OrderDetailVo;
import com.degel.order.vo.OrderInfoVo;
import com.degel.order.vo.OrderListVo;
import com.degel.order.vo.inner.OrderCreateInnerVo;

import java.util.List;
import com.degel.order.vo.inner.OrderStatusUpdateInnerVo;

public interface IOrderInfoService extends IService<OrderInfo> {

    IPage<OrderListVo> pageOrders(IPage<OrderInfo> page, Long shopId, Integer status, String orderNo);

    OrderDetailVo getOrderDetail(Long id, Long shopId);

    void deliver(DeliverVo vo, Long shopId);

    // ==================== C 端内部接口（degel-app 经 Feign 调用） ====================

    /**
     * 创建订单（主表 + 明细快照，事务）
     */
    Long createInnerOrder(OrderCreateInnerVo vo);

    /**
     * 按 ID 查订单（C 端，含明细）
     */
    OrderInfoVo getInnerOrder(Long orderId);

    /**
     * 按 userId 分页查订单列表（含明细）
     */
    IPage<OrderInfoVo> pageInnerOrders(Long userId, Integer status, Integer page, Integer pageSize);

    /**
     * 更新订单状态及对应时间字段（仅更新非空字段）
     */
    void updateInnerStatus(Long orderId, OrderStatusUpdateInnerVo vo);

    /**
     * 批量取消超时未支付订单（status=0 且 autoCancelTime <= now）。
     * UPDATE 带 status=0 条件，与并发支付天然互斥；返回实际取消成功的订单（含明细，供调用方恢复库存）
     */
    List<OrderInfoVo> cancelTimeoutOrders();
}
