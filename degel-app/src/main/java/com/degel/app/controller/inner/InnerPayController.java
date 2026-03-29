package com.degel.app.controller.inner;

import com.degel.app.service.PayService;
import com.degel.app.vo.dto.InnerRefundReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
