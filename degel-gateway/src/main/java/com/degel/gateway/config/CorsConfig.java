package com.degel.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    /**
     * CORS 白名单：逗号分隔 origin 列表，经 DEGEL_CORS_ORIGINS 环境变量覆盖。
     * 默认值只覆盖本地开发（管理台 8000、taro H5 10087）；生产必须显式配置真实域名。
     * 注意 allowCredentials(true) 与通配 origin 不能同时生效（浏览器规范禁止），
     * 旧版 addAllowedOriginPattern("*") + credentials 属于放宽规避，此处改回白名单。
     */
    @Value("${degel.cors.origins:http://localhost:8000,http://localhost:10087}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins.split(",")) {
            if (StringUtils.hasText(origin)) {
                config.addAllowedOrigin(origin.trim());
            }
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
