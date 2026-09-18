package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.common.core.R;
import com.degel.order.entity.Notification;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.service.IOrderInfoService;
import com.degel.order.vo.AfterSaleInfoVo;
import com.degel.order.vo.OrderInfoVo;
import com.degel.order.vo.inner.AfterSaleCreateInnerVo;
import com.degel.order.vo.inner.OrderCreateInnerVo;
import com.degel.order.vo.inner.OrderStatusUpdateInnerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端内部接口（degel-app 专用，经 Feign 直连；网关侧 /order/inner/ 已列入 internal-urls 禁止外部访问）
 */
@RestController
@RequestMapping("/inner/order")
@RequiredArgsConstructor
public class InnerOrderController {

    private final IOrderInfoService orderInfoService;
    private final IOrderAfterSaleService orderAfterSaleService;
    private final com.degel.order.service.NotificationService notificationService;

    /**
     * 创建订单（主表 + 明细快照，事务）
     */
    @PostMapping("/create")
    public R<Long> createOrder(@RequestBody OrderCreateInnerVo reqVO) {
        return R.ok(orderInfoService.createInnerOrder(reqVO));
    }

    /**
     * 查询订单详情（含明细）
     */
    @GetMapping("/{orderId}")
    public R<OrderInfoVo> getOrder(@PathVariable("orderId") Long orderId) {
        return R.ok(orderInfoService.getInnerOrder(orderId));
    }

    /**
     * 按订单号精确查询订单（含明细；不存在返回 null data）
     */
    @GetMapping("/no/{orderNo}")
    public R<OrderInfoVo> getOrderByNo(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderInfoService.getInnerOrderByOrderNo(orderNo));
    }

    /**
     * 按 userId 分页查询订单列表
     */
    @GetMapping("/page")
    public R<IPage<OrderInfoVo>> pageOrders(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(orderInfoService.pageInnerOrders(userId, status, page, pageSize));
    }

    /**
     * 更新订单状态（支付成功/取消/确认收货，非空字段更新）
     */
    @PutMapping("/{orderId}/status")
    public R<Void> updateOrderStatus(
            @PathVariable("orderId") Long orderId,
            @RequestBody OrderStatusUpdateInnerVo updateVO) {
        orderInfoService.updateInnerStatus(orderId, updateVO);
        return R.ok();
    }

    /**
     * 创建售后单
     */
    @PostMapping("/aftersale")
    public R<Long> createAfterSale(@RequestBody AfterSaleCreateInnerVo reqVO) {
        return R.ok(orderAfterSaleService.createInnerAfterSale(reqVO));
    }

    /**
     * 批量取消超时未支付订单（定时任务专用）：返回实际取消成功的订单（含明细，供恢复库存）
     */
    @PutMapping("/timeout-cancel")
    public R<List<OrderInfoVo>> cancelTimeoutOrders() {
        return R.ok(orderInfoService.cancelTimeoutOrders());
    }

    /**
     * 取消已付款未发货订单（用户主动取消+全额退款）：原子 CAS status=1→4，
     * 与商家并发发货互斥；返回含明细 VO 供调用方善后（库存/券/积分/退款流水）
     */
    @PutMapping("/{orderId}/cancel-paid")
    public R<OrderInfoVo> cancelPaidOrder(@PathVariable("orderId") Long orderId) {
        return R.ok(orderInfoService.cancelPaidOrder(orderId));
    }

    /**
     * 批量自动确认收货（定时任务专用）：status=2 且发货超过 shipDays 天 → 已完成。
     * 返回实际收货成功的订单 id
     */
    @PutMapping("/auto-receive")
    public R<List<OrderInfoVo>> autoConfirmReceipts(@RequestParam(value = "shipDays", defaultValue = "7") int shipDays) {
        return R.ok(orderInfoService.autoConfirmReceipts(shipDays));
    }

    /**
     * 回写获得积分数（app 确认收货发分后调用）
     */
    @PutMapping("/points-earned")
    public R<Void> updatePointsEarned(@RequestParam("orderNo") String orderNo,
                                      @RequestParam("points") int points) {
        orderInfoService.updatePointsEarned(orderNo, points);
        return R.ok();
    }

    /**
     * 按 userId 分页查询售后单
     */
    @GetMapping("/aftersale/page")
    public R<IPage<AfterSaleInfoVo>> pageAfterSales(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(orderAfterSaleService.pageInnerAfterSales(userId, status, page, pageSize));
    }

    /**
     * 精确查重：指定订单是否存在进行中的售后单（status IN 0,1）
     */
    @GetMapping("/aftersale/check")
    public R<Boolean> existsActiveAfterSale(
            @RequestParam("orderId") Long orderId,
            @RequestParam("userId") Long userId) {
        return R.ok(orderAfterSaleService.existsActiveAfterSale(orderId, userId));
    }

    /**
     * 精确查询售后单详情
     */
    @GetMapping("/aftersale/{id}")
    public R<AfterSaleInfoVo> getAfterSaleById(@PathVariable("id") Long id) {
        return R.ok(orderAfterSaleService.getInnerAfterSale(id));
    }

    /**
     * 用户申请平台介入（仅 status=5 已拒绝可申请，CAS 5→6）
     */
    @PutMapping("/aftersale/{id}/arbitrate")
    public R<Void> applyArbitrate(@PathVariable("id") Long id, @RequestParam("userId") Long userId) {
        orderAfterSaleService.applyArbitrateInner(id, userId);
        return R.ok();
    }

    // ==================== C 端站内信（degel-app Feign 转发） ====================

    /**
     * 近 N 天各 SKU 销量聚合（已支付口径 status IN 1,2,3,5；product 滞销预警用）。
     * 直接返回 Map（key=skuId 字符串化），与 Feign 消费端 R<Map<String,Long>> 对齐——勿再包 VO
     */
    @GetMapping("/stats/sku-sales")
    public R<java.util.Map<String, Long>> skuSales(@RequestParam(defaultValue = "30") Integer days) {
        return R.ok(orderInfoService.sumSkuSalesRecent(days));
    }


    @GetMapping("/notification/page")
    public R<IPage<Notification>> notificationPage(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(notificationService.pageForUser(userId, page, pageSize));
    }

    @GetMapping("/notification/unread-count")
    public R<Long> notificationUnreadCount(@RequestParam("userId") Long userId) {
        return R.ok(notificationService.unreadCount(userId));
    }

    @PutMapping("/notification/{id}/read")
    public R<Void> notificationRead(@PathVariable("id") Long id, @RequestParam("userId") Long userId) {
        notificationService.markRead(id, userId);
        return R.ok();
    }

    @PutMapping("/notification/read-all")
    public R<Void> notificationReadAll(@RequestParam("userId") Long userId) {
        notificationService.markAllRead(userId);
        return R.ok();
    }
}
