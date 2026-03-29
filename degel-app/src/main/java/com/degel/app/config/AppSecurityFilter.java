package com.degel.app.config;

import com.degel.app.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * C端安全过滤器
 * 职责：读取网关透传的 X-User-Id header，写入 UserContext；
 *       finally 块强制清理，防止线程复用时脏数据残留。
 *
 * 注意：JWT 解析 & 鉴权由网关的 AppJwtFilter 完成，
 *       本 Filter 只负责 userId 传递。
 */
@Slf4j
public class AppSecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            if (StringUtils.hasText(userIdHeader)) {
                try {
                    long userId = Long.parseLong(userIdHeader);
                    UserContext.setUserId(userId);
                } catch (NumberFormatException e) {
                    log.warn("X-User-Id header 格式非法: {}", userIdHeader);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 强制清理 ThreadLocal，防止内存泄漏 & 线程池复用时的脏数据
            UserContext.clear();
        }
    }
}
