package com.degel.app.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 配置：透传 X-Inner-Token 请求头，用于内部服务鉴权
 */
@Configuration
public class FeignConfig {

    @Value("${degel.inner.token}")
    private String innerToken;

    @Bean
    public RequestInterceptor innerTokenInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                template.header("X-Inner-Token", innerToken);
            }
        };
    }
}
