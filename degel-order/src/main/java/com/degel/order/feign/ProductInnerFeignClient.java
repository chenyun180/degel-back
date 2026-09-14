package com.degel.order.feign;

import com.degel.common.core.R;
import com.degel.order.config.FeignConfig;
import com.degel.order.vo.InnerRatingVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * degel-product 内部接口（评分冗余回写）。评分失败只影响展示指标，
 * 调用方 catch 后记日志即可，不阻断评价主流程。
 */
@FeignClient(name = "degel-product", contextId = "orderProductInnerFeignClient",
        path = "/inner/spu", configuration = FeignConfig.class)
public interface ProductInnerFeignClient {

    @PutMapping("/rating")
    R<Boolean> updateRating(@RequestBody InnerRatingVo vo);
}
