package com.degel.app.feign;

import com.degel.app.feign.fallback.StockFeignFallback;
import com.degel.app.vo.dto.StockDeductVO;
import com.degel.app.vo.dto.StockRestoreVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务 Feign 客户端
 * 对应 degel-product 内部库存接口
 */
@FeignClient(name = "degel-product", path = "/inner", fallback = StockFeignFallback.class)
public interface StockFeignClient {

    /**
     * 扣减库存
     * SQL: UPDATE product_sku SET stock=stock-? WHERE id=? AND stock>=? AND status=1
     * 影响行数=0 → 返回库存不足
     */
    @PutMapping("/sku/stock/deduct")
    R<Boolean> deductStock(@RequestBody StockDeductVO deductVO);

    /**
     * 恢复库存（取消订单 / 退款时调用）
     * SQL: UPDATE product_sku SET stock=stock+? WHERE id=?
     */
    @PutMapping("/sku/stock/restore")
    R<Boolean> restoreStock(@RequestBody StockRestoreVO restoreVO);
}
