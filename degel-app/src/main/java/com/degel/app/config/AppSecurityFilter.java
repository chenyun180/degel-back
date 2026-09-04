package com.degel.app.config;

import com.degel.app.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * C端安全过滤器
 * 职责：解析 C 端 JWT（Authorization: Bearer xxx）写入 UserContext；
 *       校验 jti 是否在 Redis 黑名单（登出/封禁的令牌立即失效）；
 *       公开接口免登录放行，受保护接口无有效 token 时返回 401；
 *       finally 块强制清理，防止线程复用时脏数据残留。
 *
 * 注意：C 端为独立密钥体系。网关 AuthFilter 会识别 c_end 类型令牌做统一校验后
 *       注入 X-User-Id；本过滤器保留完整本地验签兜底（防绕过网关直连 9205）。
 *       网关已在入口清除外部伪造的 X-User-Id header。
 */
@Slf4j
@RequiredArgsConstructor
public class AppSecurityFilter extends OncePerRequestFilter {

    private final AppJwtConfig appJwtConfig;
    private final StringRedisTemplate redisTemplate;

    /** 与网关 AuthFilter 一致的 C 端黑名单前缀 */
    public static final String BLACKLIST_PREFIX = "app:blacklist:";

    /**
     * 无需登录的公开路径前缀。白名单面一致性说明（与网关 ignore-urls 对照）：
     * - 网关 /app/ 前缀整段放行（含 c_end 令牌统一校验入口），鉴权责任在本过滤器；
     * - 本清单仅放行「登录本身」与「商品匿名浏览」（商城 C 端预期行为），
     *   购物车/订单/地址/支付/售后/用户信息均在鉴权范围内。
     * ⚠️ 新增公开路径必须同时评估网关 ignore-urls 与本清单两层，避免意外扩大匿名面。
     */
    private static final List<String> PUBLIC_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "/app/auth/",
            "/app/product/"
    ));

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            String token = extractBearerToken(request);
            if (StringUtils.hasText(token)) {
                Claims claims;
                try {
                    claims = parseClaims(token);
                } catch (ExpiredJwtException e) {
                    writeUnauthorized(response, "登录已过期，请重新登录");
                    return;
                } catch (Exception e) {
                    log.error("[AppSecurityFilter] JWT 解析异常 path={}", path, e);
                    writeUnauthorized(response, "无效的访问令牌");
                    return;
                }
                if (claims == null) {
                    writeUnauthorized(response, "无效的访问令牌");
                    return;
                }

                // jti 黑名单：登出/封禁的令牌立即失效（无 jti 的存量 token 无法拉黑，自然过期）
                String jti = claims.getId();
                if (jti != null && Boolean.TRUE.equals(
                        redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                    writeUnauthorized(response, "令牌已失效，请重新登录");
                    return;
                }

                UserContext.setUserId(Long.valueOf(claims.getSubject()));
            } else if (!isPublic(path)) {
                writeUnauthorized(response, "缺少访问令牌");
                return;
            }
            // 下游异常不在本过滤器捕获，保持向上传播（由 GlobalExceptionHandler 处理）
            filterChain.doFilter(request, response);
        } finally {
            // 强制清理 ThreadLocal，防止内存泄漏 & 线程池复用时的脏数据
            UserContext.clear();
        }
    }

    /**
     * 解析并校验 C 端 JWT；非 c_end 类型返回 null
     */
    private Claims parseClaims(String token) {
        byte[] keyBytes = appJwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (!"c_end".equals(claims.get("type"))) {
            return null;
        }
        return claims;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 将指定 jti 拉黑至令牌剩余有效时长（供登出/封禁调用）
     */
    public void blacklist(String jti, long remainingMs) {
        if (jti != null && remainingMs > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remainingMs, TimeUnit.MILLISECONDS);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}");
    }
}
