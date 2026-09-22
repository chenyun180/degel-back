package com.degel.admin.filter;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部服务调用鉴权过滤器（2026-09-22 M2 修复补齐，与 product/order/marketing/app 同款）。
 *
 * degel-admin 此前是唯一没有 InnerTokenFilter 的服务：{@code GET /user/find/{username}}
 * 会返回含 BCrypt 密码哈希的 UserInfo（degel-auth Feign 校验凭据用的内部接口），
 * 防护只剩"网关 internal-urls + 仅监听 127.0.0.1"两层——生产改网络隔离部署时同网段
 * 任意机器直连 9201 即可拖走全部用户哈希。本过滤器补上服务侧第三层：
 *
 *   X-Inner-Token == degel.inner.token → 通过，否则 403
 *
 * 覆盖路径：/inner/**（预留）+ /user/find/**（auth 的 Feign 入口，出站拦截器在
 * degel-auth 的 FeignConfig，两处必须同时部署）。
 */
@Slf4j
@Component
public class InnerTokenFilter extends OncePerRequestFilter {

    private static final String INNER_TOKEN_HEADER = "X-Inner-Token";

    @Value("${degel.inner.token}")
    private String innerToken;

    @PostConstruct
    public void init() {
        Assert.hasText(innerToken, "degel.inner.token 配置项不能为空");
    }

    @Override
    public void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (isInternalPath(uri)) {
            String tokenHeader = request.getHeader(INNER_TOKEN_HEADER);
            if (!StringUtils.hasText(tokenHeader)
                    || !MessageDigest.isEqual(tokenHeader.getBytes(StandardCharsets.UTF_8),
                            innerToken.getBytes(StandardCharsets.UTF_8))) {
                // 注意：token 原值不打日志（若为真实泄漏的内网令牌，日志会成为二次泄漏点）
                log.warn("[InnerTokenFilter] 内部接口非法访问 uri={} remote={}", uri, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"内部服务鉴权失败\",\"data\":null}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(String uri) {
        return uri.startsWith("/inner/") || uri.startsWith("/user/find/");
    }
}
