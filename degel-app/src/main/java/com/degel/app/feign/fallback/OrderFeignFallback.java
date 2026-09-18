package com.degel.app.feign.fallback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.vo.AfterSaleInfoVO;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.vo.dto.AfterSaleCreateInnerReqVO;
import com.degel.app.vo.dto.OrderCreateInnerReqVO;
import com.degel.app.vo.dto.OrderStatusUpdateVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OrderFeignClient 降级实现
 */
@Slf4j
@Component
public class OrderFeignFallback implements OrderFeignClient {

    @Override
    public R<Long> createOrder(OrderCreateInnerReqVO reqVO) {
        log.error("[OrderFeignFallback] createOrder 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<OrderInfoVO> getOrder(Long orderId) {
        log.error("[OrderFeignFallback] getOrder orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<OrderInfoVO> getOrderByNo(String orderNo) {
        log.error("[OrderFeignFallback] getOrderByNo orderNo={} 降级", orderNo);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Page<OrderInfoVO>> pageOrders(Long userId, Integer status, Integer page, Integer pageSize) {
        log.error("[OrderFeignFallback] pageOrders 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Void> updateOrderStatus(Long orderId, OrderStatusUpdateVO updateVO) {
        log.error("[OrderFeignFallback] updateOrderStatus orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<List<OrderInfoVO>> cancelTimeoutOrders() {
        log.error("[OrderFeignFallback] cancelTimeoutOrders 降级");
        return R.fail(50001, "订单服务暂不可用");
    }

    @Override
    public R<OrderInfoVO> cancelPaidOrder(Long orderId) {
        // 取消已付款订单是用户主流程：降级直接报错，不能静默（未取消成功不得走善后）
        log.error("[OrderFeignFallback] cancelPaidOrder 降级 orderId={}", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<List<com.degel.app.vo.OrderInfoVO>> autoConfirmReceipts(Integer shipDays) {
        log.error("[OrderFeignFallback] autoConfirmReceipts 降级");
        return R.fail(50001, "订单服务暂不可用");
    }

    @Override
    public R<Void> updatePointsEarned(String orderNo, int points) {
        log.error("[OrderFeignFallback] updatePointsEarned 降级 orderNo={}", orderNo);
        return R.fail(50001, "订单服务暂不可用");
    }

    @Override
    public R<Long> createAfterSale(AfterSaleCreateInnerReqVO reqVO) {
        log.error("[OrderFeignFallback] createAfterSale 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Page<AfterSaleInfoVO>> pageAfterSales(Long userId, Integer status, Integer page, Integer pageSize) {
        log.error("[OrderFeignFallback] pageAfterSales 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Boolean> existsActiveAfterSale(Long orderId, Long userId) {
        log.error("[OrderFeignFallback] existsActiveAfterSale orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<AfterSaleInfoVO> getAfterSaleById(Long id) {
        log.error("[OrderFeignFallback] getAfterSaleById id={} 降级", id);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Void> applyArbitrate(Long id, Long userId) {
        log.error("[OrderFeignFallback] applyArbitrate id={} 降级", id);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Page<com.degel.app.vo.NotificationVO>> notificationPage(Long userId, Integer page, Integer pageSize) {
        log.error("[OrderFeignFallback] notificationPage 降级 userId={}", userId);
        return R.fail(50001, "消息服务暂不可用");
    }

    @Override
    public R<Long> notificationUnreadCount(Long userId) {
        log.error("[OrderFeignFallback] notificationUnreadCount 降级 userId={}", userId);
        return R.ok(0L);
    }

    @Override
    public R<Void> notificationRead(Long id, Long userId) {
        log.error("[OrderFeignFallback] notificationRead 降级 id={}", id);
        return R.fail(50001, "操作失败，请稍后重试");
    }

    @Override
    public R<Void> notificationReadAll(Long userId) {
        log.error("[OrderFeignFallback] notificationReadAll 降级 userId={}", userId);
        return R.fail(50001, "操作失败，请稍后重试");
    }
}
