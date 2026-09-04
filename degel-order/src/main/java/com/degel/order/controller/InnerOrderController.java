package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.common.core.R;
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
}
