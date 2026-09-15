package com.degel.app.feign.fallback;

import com.degel.app.feign.SeckillFeignClient;
import com.degel.app.vo.dto.SeckillProductDTO;
import com.degel.app.vo.dto.SeckillSessionDTO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SeckillFeignClient 降级实现。
 * current 降级返回 fail（调用方转空列表，秒杀位不阻塞首页）；
 * detail 降级返回 fail（调用方判 code!=200 统一按"活动不存在或已停用"阻断抢购/下单）。
 */
@Slf4j
@Component
public class SeckillFeignFallback implements SeckillFeignClient {

    @Override
    public R<List<SeckillSessionDTO>> current() {
        log.error("[SeckillFeignFallback] current 降级");
        return R.fail(50001, "秒杀服务暂不可用");
    }

    @Override
    public R<SeckillProductDTO> detail(Long sessionId, Long skuId) {
        log.error("[SeckillFeignFallback] detail sessionId={} skuId={} 降级", sessionId, skuId);
        return R.fail(50001, "秒杀服务暂不可用");
    }
}
