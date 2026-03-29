package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.context.UserContext;
import com.degel.app.service.OrderService;
import com.degel.app.vo.OrderCreateVO;
import com.degel.app.vo.OrderDetailVO;
import com.degel.app.vo.OrderListVO;
import com.degel.app.vo.dto.OrderCreateReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 * C-02: POST  /app/order          创建订单
 * C-03: GET   /app/order          订单列表
 * C-04: GET   /app/order/{orderId} 订单详情
 * C-05: PUT   /app/order/{orderId}/cancel   取消订单
 * C-06: PUT   /app/order/{orderId}/receive  确认收货
 */
@RestController
@RequestMapping("/app/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * C-02: 创建订单
     */
    @PostMapping
    public R<OrderCreateVO> createOrder(@RequestBody @Validated OrderCreateReqVO reqVO) {
        Long userId = UserContext.getUserId();
        OrderCreateVO result = orderService.createOrder(reqVO, userId);
        return R.ok(result);
    }

    /**
     * C-03: 订单列表（分页）
     */
    @GetMapping
    public R<IPage<OrderListVO>> listOrders(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        Long userId = UserContext.getUserId();
        IPage<OrderListVO> result = orderService.listOrders(userId, status, page, pageSize);
        return R.ok(result);
    }

    /**
     * C-04: 订单详情
     */
    @GetMapping("/{orderId}")
    public R<OrderDetailVO> getOrderDetail(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        OrderDetailVO result = orderService.getOrderDetail(orderId, userId);
        return R.ok(result);
    }

    /**
     * C-05: 取消订单
     */
    @PutMapping("/{orderId}/cancel")
    public R<Void> cancelOrder(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        orderService.cancelOrder(orderId, userId);
        return R.ok();
    }

    /**
     * C-06: 确认收货
     */
    @PutMapping("/{orderId}/receive")
    public R<Void> confirmReceive(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        orderService.confirmReceive(orderId, userId);
        return R.ok();
    }
}
