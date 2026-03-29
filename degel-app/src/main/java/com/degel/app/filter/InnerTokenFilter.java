package com.degel.app.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 内部服务调用鉴权过滤器
 *
 * 保护 /app/inner/** 接口，校验 X-Inner-Token header。
 * 该路径不在网关路由中暴露，Feign 通过 Nacos 直连 degel-app。
 *
 * 校验逻辑：
 *   X-Inner-Token == degel.inner.token → 通过
 *   否则 → 403
 */
@Slf4j
@Component
public class InnerTokenFilter extends OncePerRequestFilter {

    private static final String INNER_PATH_PREFIX = "/app/inner/";
    private static final String INNER_TOKEN_HEADER = "X-Inner-Token";

    @Value("${degel.inner.token}")
    private String innerToken;

    @PostConstruct
    public void init() {
        Assert.hasText(innerToken, "degel.inner.token 配置项不能为空");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 只拦截内部路径
        if (uri.startsWith(INNER_PATH_PREFIX)) {
            String tokenHeader = request.getHeader(INNER_TOKEN_HEADER);
            if (!StringUtils.hasText(tokenHeader) || !tokenHeader.equals(innerToken)) {
                log.warn("[InnerTokenFilter] 内部接口非法访问 uri={} token={}", uri, tokenHeader);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"内部服务鉴权失败\",\"data\":null}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
