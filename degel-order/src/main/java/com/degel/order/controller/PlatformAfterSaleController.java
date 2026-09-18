package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.vo.AfterSaleArbitrateVo;
import com.degel.order.vo.AfterSaleInfoVo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台售后仲裁（PRD 6.3：商家拒绝后用户申请介入，平台判定支持用户退款/维持拒绝）
 * 鉴权：网关 degel.security.admin-urls 的 "*:/order/platform/aftersale" 限定平台管理员
 */
@RestController
@RequestMapping("/platform/aftersale")
@RequiredArgsConstructor
public class PlatformAfterSaleController {

    private final IOrderAfterSaleService orderAfterSaleService;

    /** 仲裁列表（status 可筛：6=待仲裁；不传=全部，含历史） */
    @GetMapping("/page")
    public R<IPage<AfterSaleInfoVo>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        return R.ok(orderAfterSaleService.pageArbitrations(new Page<>(current, size), status));
    }

    /** 判定：supportUser=true 执行退款链路；false 维持拒绝。判定人取网关注入的 X-User-Name */
    @PutMapping("/arbitrate")
    public R<Void> arbitrate(@Validated @RequestBody AfterSaleArbitrateVo vo,
                             @RequestHeader(value = "X-User-Name", defaultValue = "platform") String operator) {
        orderAfterSaleService.arbitrate(vo, operator);
        return R.ok();
    }
}
