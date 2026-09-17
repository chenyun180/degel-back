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
     * 默认值覆盖本地开发（管理台 8000、taro H5 10087）+ 局域网 IP 访问
     * （192.168.1.*——真机/局域网浏览器访问 dev server 的常见形态，
     * 浏览器对 POST 会带 Origin 头，不在名单内的 origin 会被 403 空 body 拒绝，
     * 曾导致 taro H5 经 LAN 访问时登录 403 + 前端空响应体崩溃）。
     * 含 * 的条目走 addAllowedOriginPattern（Spring 支持 pattern + credentials）。
     * 生产必须用 DEGEL_CORS_ORIGINS 显式配置真实域名，勿依赖含通配的默认值。
     */
    @Value("${degel.cors.origins:http://localhost:8000,http://localhost:10087,http://192.168.1.*:8000,http://192.168.1.*:10087}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins.split(",")) {
            if (StringUtils.hasText(origin)) {
                String trimmed = origin.trim();
                if (trimmed.contains("*")) {
                    config.addAllowedOriginPattern(trimmed);
                } else {
                    config.addAllowedOrigin(trimmed);
                }
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
