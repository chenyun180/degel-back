package com.degel.auth.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 管理端登录限流（POST /oauth/token，password 模式）。
 *
 * <p>client_secret 随前端 bundle 公开，密码模式端点等于公开的暴力破解面，必须限流兜底：
 * 用户名维度只计 <b>失败</b> 次数（5 次/分钟），IP 维度同样只计失败（20 次/分钟）——
 * 防爆破本意是拦失败尝试，正常用户与 e2e 的成功登录永远不占配额。
 *
 * <p>语义对齐 C 端 degel-app 的 RedisRateLimiter：Redis 固定窗口（INCR + 首次 EXPIRE），
 * Redis 故障时 fail-open（仅记日志）。
 *
 * <p>失败判定 = 响应状态 >= 400（Spring OAuth2 凭据错误返回 400 invalid_grant），
 * 用 response wrapper 在 doFilter 之后计数；成功/限流自身的 429 不再累计。
 * 用户名从 getParameter 读取（password 模式是表单体），不碰输入流，
 * 不影响下游 OAuth2 端点的参数解析；非表单请求退化为仅 IP 维度限流。
 * 超限返回 429 + OAuth 错误体（error/error_description），登录页直接展示 error_description。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String KEY_USER = "rl:admin:login:user:";
    private static final String KEY_IP = "rl:admin:login:ip:";
    private static final int USER_FAILURE_LIMIT = 5;
    private static final int IP_LIMIT = 20;
    private static final int WINDOW_SECONDS = 60;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !TOKEN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 两个维度都只计失败次数：防爆破要拦的就是失败尝试，
        // 正常用户与 e2e 的成功登录永远不占配额（IP 维度计全部请求会误伤高频成功登录的测试/脚本）
        String ipKey = KEY_IP + clientIp(request);
        // 仅表单参数里有 username 时才做账号维度限流（getParameter 不消费输入流）
        String username = request.getParameter("username");
        String userKey = StringUtils.hasText(username) ? KEY_USER + username : null;

        if (!allow(ipKey, IP_LIMIT)) {
            reject(response, "登录失败次数过多，请稍后再试");
            return;
        }
        if (userKey != null && !allow(userKey, USER_FAILURE_LIMIT)) {
            reject(response, "该账号登录失败次数过多，请稍后再试");
            return;
        }

        // doFilter 后按实际结果计数：失败(>=400)才累计，成功不占配额
        chain.doFilter(request, response);
        if (response.getStatus() >= 400) {
            countFailure(ipKey);
            if (userKey != null) {
                countFailure(userKey);
            }
        }
    }

    /** 读取计数窗口并判断是否放行（仅判不增，增量在结果确定后由 countFailure 写入） */
    private boolean allow(String key, int limit) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            long cnt = value != null ? parseLongSafe(value) : 0L;
            return cnt < limit;
        } catch (Exception e) {
            log.warn("[LoginRateLimitFilter] 限流读取失败（fail-open）key={}", key, e);
            return true;
        }
    }

    private void countFailure(String key) {
        try {
            Long cnt = stringRedisTemplate.opsForValue().increment(key);
            if (cnt != null && cnt == 1L) {
                stringRedisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("[LoginRateLimitFilter] 失败计数写入失败（fail-open）key={}", key, e);
        }
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
        response.getWriter().write(
                "{\"error\":\"rate_limited\",\"error_description\":\"" + message + "\"}");
        response.getWriter().flush();
    }

    /** 客户端真实 IP：网关转发取 X-Forwarded-For 首段，直连取 remoteAddr（与 C 端 RedisRateLimiter 一致） */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String ip = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
