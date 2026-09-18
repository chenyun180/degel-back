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
// 网关 /order/** 路由配置了 StripPrefix=1（剥掉第一段），服务侧不能再带 /order 前缀，
// 否则前端 /order/list 到这里变成 /order/list + /list 双重前缀而 404
@RequestMapping("")
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

    /**
     * 配货单导出（CSV 带 BOM，Excel 可直接打开）。
     * 前端 Ship 页的「导出配货单」按钮此前调用本端点但后端从未实现——点击必 404，2026-09-18 补齐。
     * 默认导出待发货（status=1）；每订单每商品一行平铺，配货员按订单号归组。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestHeader("X-Shop-Id") Long shopId,
            @RequestParam(defaultValue = "1") Integer status) throws java.io.UnsupportedEncodingException {
        byte[] csv = orderInfoService.exportPickingList(shopId, status);
        String fileName = java.net.URLEncoder.encode(
                "配货单_" + java.time.LocalDate.now() + ".csv", java.nio.charset.StandardCharsets.UTF_8.name());
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + fileName)
                .body(csv);
    }
}
