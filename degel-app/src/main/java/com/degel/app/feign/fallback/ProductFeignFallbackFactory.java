package com.degel.app.feign.fallback;

import com.degel.app.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * ProductFeignClient 降级工厂：记录降级根因（HTTP 错误 / 反序列化失败 / 熔断开启）
 */
@Slf4j
@Component
public class ProductFeignFallbackFactory implements FallbackFactory<ProductFeignClient> {

    private final ProductFeignFallback delegate;

    public ProductFeignFallbackFactory(ProductFeignFallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProductFeignClient create(Throwable cause) {
        // CallNotPermittedException = 熔断开启；其余为真实调用失败
        log.error("[ProductFeignFallback] 降级，根因: {}", cause == null ? "unknown" : cause.toString(), cause);
        return delegate;
    }
}
