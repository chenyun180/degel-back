package com.degel.order.feign;

import com.degel.common.core.R;
import com.degel.order.config.FeignConfig;
import com.degel.order.vo.inner.PayRefundInnerVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付服务 Feign 客户端（售后退款流水落库）。
 * 直连 lb://degel-app，/app/inner/pay/** 由 app 的 InnerTokenFilter 校验 X-Inner-Token。
 * 不配 fallback：调用方 try-catch，流水写入失败仅记日志可人工补偿（与整单退券同语义）。
 */
@FeignClient(name = "degel-app", contextId = "orderPayFeignClient",
        path = "/app/inner/pay", configuration = FeignConfig.class)
public interface PayFeignClient {

    /** C-09: 写退款流水（direction=refund），返回退款流水 ID */
    @PostMapping("/refund")
    R<Long> refund(@RequestBody PayRefundInnerVo vo);

    /** 订单是否已有退款流水（对账补偿判断用） */
    @GetMapping("/refund/exists")
    R<Boolean> refundExists(@RequestParam("orderId") Long orderId);

    /** 平台资金汇总：data = [累计支付总额, 累计退款总额]（平台资金总览用） */
    @GetMapping("/summary")
    R<java.math.BigDecimal[]> summary();
}
