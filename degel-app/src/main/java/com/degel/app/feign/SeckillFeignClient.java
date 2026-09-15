package com.degel.app.feign;

import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.SeckillFeignFallback;
import com.degel.app.vo.dto.SeckillProductDTO;
import com.degel.app.vo.dto.SeckillSessionDTO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 秒杀营销服务 Feign 客户端（/inner/seckill/**，InnerTokenFilter 已保护，
 * FeignConfig 统一注入 X-Inner-Token）。
 *
 * 降级语义：查询类降级返回 fail 由调用方处理——场次列表降级返回空列表（秒杀位
 * 不阻塞首页），reserve/createOrder 前置的 detail 校验降级必须阻断（fail → 40020）。
 */
@FeignClient(name = "degel-marketing", path = "/inner/seckill",
        contextId = "appSeckillFeignClient",
        configuration = FeignConfig.class,
        fallback = SeckillFeignFallback.class)
public interface SeckillFeignClient {

    /** 当前/即将开卖场次列表（含商品；启用且 end_time>now 且 start_time<now+24h） */
    @GetMapping("/current")
    R<List<SeckillSessionDTO>> current();

    /** 场次商品单条详情；场次不存在/停用返回 data=null，调用方判空 */
    @GetMapping("/detail")
    R<SeckillProductDTO> detail(@RequestParam("sessionId") Long sessionId,
                                @RequestParam("skuId") Long skuId);
}
