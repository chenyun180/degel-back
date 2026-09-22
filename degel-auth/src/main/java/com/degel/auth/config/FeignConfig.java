package com.degel.auth.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 出站拦截器（2026-09-22 M2 修复配套）：给 Feign 请求加 X-Inner-Token。
 * degel-admin 的 InnerTokenFilter 已上线（校验 /user/find/**），auth 的
 * RemoteUserService 调用必须携带本 token，否则登录链路 403。
 * 与 degel-app/order/product FeignConfig 同款。
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
