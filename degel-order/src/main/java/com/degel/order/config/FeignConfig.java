package com.degel.order.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 出站拦截器：给所有 Feign 请求加 X-Inner-Token（degel-marketing InnerTokenFilter 校验）。
 * 与 degel-app FeignConfig 同款。
 */
@Configuration
public class FeignConfig {

    @Value("${degel.inner.token}")
    private String innerToken;

    @Bean
    public RequestInterceptor innerTokenInterceptor() {
        return template -> template.header("X-Inner-Token", innerToken);
    }
}
