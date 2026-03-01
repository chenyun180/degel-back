package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.vo.AfterSaleHandleVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/order/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final IOrderAfterSaleService orderAfterSaleService;

    @GetMapping("/list")
    public R<IPage<OrderAfterSale>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader("X-Shop-Id") Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type) {
        return R.ok(orderAfterSaleService.pageAfterSales(new Page<>(current, size), shopId, status, type));
    }

    @PutMapping("/handle")
    public R<Void> handle(
            @Valid @RequestBody AfterSaleHandleVo vo,
            @RequestHeader("X-Shop-Id") Long shopId) {
        orderAfterSaleService.handle(vo, shopId);
        return R.ok();
    }

    @PutMapping("/confirm-receive")
    public R<Void> confirmReceive(
            @RequestParam Long afterSaleId,
            @RequestHeader("X-Shop-Id") Long shopId) {
        orderAfterSaleService.confirmReceive(afterSaleId, shopId);
        return R.ok();
    }
}
