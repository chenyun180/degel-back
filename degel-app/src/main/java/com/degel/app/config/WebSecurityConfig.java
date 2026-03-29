package com.degel.app.config;

import com.degel.app.filter.InnerTokenFilter;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Web 安全配置
 * - 关闭 Spring Security 默认的登录页 & CSRF（由网关统一鉴权）
 * - 注册 AppSecurityFilter（UserId 透传）
 * - 提供 BCryptPasswordEncoder Bean（H5登录密码校验）
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（前后端分离 + JWT 鉴权）
            .csrf().disable()
            // 无状态 Session
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            // 所有请求放行（鉴权由网关 AppJwtFilter 处理）
            .authorizeRequests().anyRequest().permitAll();
    }

    /**
     * 注册 AppSecurityFilter，优先级最高
     */
    @Bean
    public FilterRegistrationBean<AppSecurityFilter> appSecurityFilterRegistration() {
        FilterRegistrationBean<AppSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AppSecurityFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("appSecurityFilter");
        return registration;
    }

    /**
     * 注册 InnerTokenFilter，拦截 /app/inner/** 内部接口
     * 优先级在 appSecurityFilter 之后（ORDER+1）
     */
    @Bean
    public FilterRegistrationBean<InnerTokenFilter> innerTokenFilterRegistration(InnerTokenFilter innerTokenFilter) {
        FilterRegistrationBean<InnerTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(innerTokenFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("innerTokenFilter");
        return registration;
    }

    /**
     * BCrypt 密码编码器（H5登录密码校验）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
