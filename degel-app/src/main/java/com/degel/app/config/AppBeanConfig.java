package com.degel.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 通用 Bean 配置
 */
@Configuration
public class AppBeanConfig {

    /**
     * RestTemplate（用于调用微信 jscode2session 等外部 HTTP 接口）
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
