package com.degel.app.controller.inner;

import com.degel.app.service.PayService;
import com.degel.app.vo.dto.InnerRefundReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部支付控制器（仅供服务间调用，不在网关暴露）
 *
 * 路径 /app/inner/** 不在网关路由中，由 InnerTokenFilter 鉴权。
 * 由 degel-order 通过 Feign + Nacos 直连调用。
 *
 * C-09: POST /app/inner/pay/refund — 内部退款接口
 */
@RestController
@RequestMapping("/app/inner/pay")
@RequiredArgsConstructor
public class InnerPayController {

    private final PayService payService;

    /**
     * C-09: 内部退款接口（degel-order 审核售后通过后调用）
     *
     * 请求头需携带：X-Inner-Token: {共享密钥}
     *
     * @param reqVO 退款请求体
     * @return 退款流水ID
     */
    @PostMapping("/refund")
    public R<Long> refund(@RequestBody @Validated InnerRefundReqVO reqVO) {
        Long payLogId = payService.refund(reqVO);
        return R.ok(payLogId);
    }

    /** 订单是否已有退款流水（degel-order 对账补偿任务用） */
    @GetMapping("/refund/exists")
    public R<Boolean> refundExists(@RequestParam Long orderId) {
        return R.ok(payService.existsRefund(orderId));
    }

    /**
     * 平台资金汇总（degel-order 平台资金总览用）
     *
     * @return data = [累计支付总额, 累计退款总额]
     */
    @GetMapping("/summary")
    public R<java.math.BigDecimal[]> summary() {
        return R.ok(payService.sumPayAndRefund());
    }
}
