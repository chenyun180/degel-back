package com.degel.product.feign;

import com.degel.common.core.R;
import com.degel.product.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 订单统计 Feign（滞销预警的销量数据源）。
 * 直连 lb://degel-order 的 /inner/order/**（InnerTokenFilter 鉴权）。
 * 不配 fallback：调用失败由 DashboardService 兜底（视为无销量数据，预警页提示稍后再试）。
 */
@FeignClient(name = "degel-order", contextId = "productOrderStatsFeignClient",
        path = "/inner/order", configuration = FeignConfig.class)
public interface OrderStatsFeignClient {

    /** 近 N 天各 SKU 销量（key=skuId 字符串） */
    @GetMapping("/stats/sku-sales")
    R<Map<String, Long>> skuSales(@RequestParam("days") Integer days);
}
