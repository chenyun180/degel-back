package com.degel.app.feign.fallback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.feign.ReviewFeignClient;
import com.degel.app.vo.ReviewCreateInnerReqVO;
import com.degel.app.vo.ReviewVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReviewFeignFallback implements ReviewFeignClient {

    @Override
    public R<Long> create(ReviewCreateInnerReqVO body) {
        log.error("[ReviewFeignFallback] create 降级");
        return R.fail(50001, "评价服务暂不可用，请稍后重试");
    }

    @Override
    public R<Page<ReviewVO>> pageBySpu(Long spuId, Integer page, Integer pageSize) {
        log.error("[ReviewFeignFallback] pageBySpu 降级");
        return R.fail(50001, "评价服务暂不可用");
    }

    @Override
    public R<Page<ReviewVO>> pageMine(Long userId, Integer page, Integer pageSize) {
        log.error("[ReviewFeignFallback] pageMine 降级");
        return R.fail(50001, "评价服务暂不可用");
    }

    @Override
    public R<Page<com.degel.app.vo.PendingOrderVO>> pendingOrders(Long userId, Integer page, Integer pageSize) {
        log.error("[ReviewFeignFallback] pendingOrders 降级");
        return R.fail(50001, "评价服务暂不可用");
    }

    @Override
    public R<List<Long>> reviewedItemIds(Long orderId, Long userId) {
        log.error("[ReviewFeignFallback] reviewedItemIds 降级");
        return R.fail(50001, "评价服务暂不可用");
    }
}
