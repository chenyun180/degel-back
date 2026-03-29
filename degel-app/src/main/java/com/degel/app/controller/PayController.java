package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.context.UserContext;
import com.degel.app.service.PayService;
import com.degel.app.vo.PayLogVO;
import com.degel.app.vo.PayResultVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器
 * C-07: POST /app/pay/{orderId}  发起支付
 * C-08: GET  /app/pay/log        支付流水列表
 */
@RestController
@RequestMapping("/app/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * C-07: 发起模拟支付
     */
    @PostMapping("/{orderId}")
    public R<PayResultVO> pay(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        PayResultVO result = payService.pay(orderId, userId);
        return R.ok(result);
    }

    /**
     * C-08: 我的支付流水列表（分页）
     */
    @GetMapping("/log")
    public R<IPage<PayLogVO>> listPayLog(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "direction", required = false) String direction
    ) {
        Long userId = UserContext.getUserId();
        IPage<PayLogVO> result = payService.listPayLog(userId, direction, page, size);
        return R.ok(result);
    }
}
