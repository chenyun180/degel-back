package com.degel.order.feign;

import com.degel.common.core.R;
import com.degel.order.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 积分服务 Feign 客户端（退款回收用；degel-marketing /inner/points/**）。
 * 不配 fallback：调用方 try-catch best-effort（与退券/退款流水同语义）。
 */
@FeignClient(name = "degel-marketing", contextId = "orderPointsFeignClient",
        path = "/inner/points", configuration = FeignConfig.class)
public interface PointsFeignClient {

    /** 退款退回该单抵扣积分（幂等） */
    @PostMapping("/return-redeem")
    R<Boolean> returnRedeem(@RequestParam("userId") Long userId, @RequestParam("orderNo") String orderNo);

    /** 退款回收该单已发积分（幂等；余额不足扣至 0，返回实际回收数） */
    @PostMapping("/reclaim-earn")
    R<Integer> reclaimEarn(@RequestBody Map<String, Object> req);
}
