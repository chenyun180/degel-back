package com.degel.app.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.ReviewFeignFallback;
import com.degel.app.vo.ReviewCreateInnerReqVO;
import com.degel.app.vo.ReviewVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * degel-order 评价内部接口（创建时 userId 由 BFF 注入登录态，服务端再校验订单归属）
 */
@FeignClient(name = "degel-order", path = "/inner/review", contextId = "appReviewInnerFeignClient",
        configuration = FeignConfig.class, fallback = ReviewFeignFallback.class)
public interface ReviewFeignClient {

    @PostMapping
    R<Long> create(@RequestBody ReviewCreateInnerReqVO body);

    @GetMapping("/spu/{spuId}")
    R<Page<ReviewVO>> pageBySpu(@PathVariable("spuId") Long spuId,
                                 @RequestParam("page") Integer page,
                                 @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/mine")
    R<Page<ReviewVO>> pageMine(@RequestParam("userId") Long userId,
                                @RequestParam("page") Integer page,
                                @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/pending-orders")
    R<Page<com.degel.app.vo.PendingOrderVO>> pendingOrders(@RequestParam("userId") Long userId,
                                                           @RequestParam("page") Integer page,
                                                           @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/reviewed-item-ids")
    R<List<Long>> reviewedItemIds(@RequestParam("orderId") Long orderId,
                                  @RequestParam("userId") Long userId);
}
