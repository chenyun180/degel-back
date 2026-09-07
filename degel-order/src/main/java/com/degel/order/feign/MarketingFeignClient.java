package com.degel.order.feign;

import com.degel.order.config.FeignConfig;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 营销服务 Feign 客户端（整单退回券）。
 * 直连 lb://degel-marketing，不带网关 /marketing 前缀。
 * 不配 fallback（degel-order 无 circuitbreaker 依赖）：调用方 try-catch，退券失败仅记日志可人工补偿。
 */
@FeignClient(name = "degel-marketing", contextId = "orderMarketingFeignClient",
        path = "/inner/coupon", configuration = FeignConfig.class)
public interface MarketingFeignClient {

    /** 整单退回（2→未过期?4:5），幂等 */
    @PostMapping("/return")
    R<Void> returnCoupon(@RequestBody Map<String, Long> req);
}
