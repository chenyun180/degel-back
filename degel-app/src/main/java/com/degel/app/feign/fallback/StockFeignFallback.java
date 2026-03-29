package com.degel.app.feign.fallback;

import com.degel.app.feign.StockFeignClient;
import com.degel.app.vo.dto.StockDeductVO;
import com.degel.app.vo.dto.StockRestoreVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * StockFeignClient 降级实现
 */
@Slf4j
@Component
public class StockFeignFallback implements StockFeignClient {

    @Override
    public R<Boolean> deductStock(StockDeductVO deductVO) {
        log.error("[StockFeignFallback] deductStock skuId={} 降级", deductVO.getSkuId());
        return R.fail(50001, "商品服务暂不可用，请稍后重试");
    }

    @Override
    public R<Boolean> restoreStock(StockRestoreVO restoreVO) {
        log.error("[StockFeignFallback] restoreStock skuId={} 降级", restoreVO.getSkuId());
        return R.fail(50001, "商品服务暂不可用，请稍后重试");
    }
}
