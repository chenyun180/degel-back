package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.order.entity.OrderInfo;
import com.degel.order.service.IOrderInfoService;
import com.degel.order.vo.DeliverVo;
import com.degel.order.vo.OrderDetailVo;
import com.degel.order.vo.OrderListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderInfoService orderInfoService;

    @GetMapping("/list")
    public R<IPage<OrderListVo>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader("X-Shop-Id") Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo) {
        return R.ok(orderInfoService.pageOrders(new Page<>(current, size), shopId, status, orderNo));
    }

    @GetMapping("/{id}")
    public R<OrderDetailVo> getById(
            @PathVariable Long id,
            @RequestHeader("X-Shop-Id") Long shopId) {
        return R.ok(orderInfoService.getOrderDetail(id, shopId));
    }

    @PutMapping("/deliver")
    public R<Void> deliver(
            @Valid @RequestBody DeliverVo vo,
            @RequestHeader("X-Shop-Id") Long shopId) {
        orderInfoService.deliver(vo, shopId);
        return R.ok();
    }
}
