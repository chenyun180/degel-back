package com.degel.gateway.filter;

import com.degel.common.core.Constants;
import com.degel.gateway.config.DegelSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final DegelSecurityProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    /** C 端令牌黑名单前缀（与 degel-app AppSecurityFilter 一致） */
    private static final String APP_BLACKLIST_PREFIX = "app:blacklist:";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 内部接口禁止外部访问
        if (isInternal(path)) {
            return forbidden(exchange, "禁止访问内部接口");
        }

        // 所有请求先清除内部 Header，防止外部伪造
        ServerHttpRequest cleanedRequest = request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Name");
                    h.remove("X-Shop-Id");
                    h.remove("X-User-Roles");
                })
                .build();
        exchange = exchange.mutate().request(cleanedRequest).build();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = getToken(cleanedRequest);
        if (token == null || token.isEmpty()) {
            return unauthorized(exchange, "缺少访问令牌");
        }

        // 先按管理端密钥解析；解析失败且配置了 C 端密钥时，再按 c_end 令牌尝试。
        // 双变量 final 以便 lambda 捕获：adminOk/cEndOk 互斥，claims 二选一非空
        Claims parsedClaims = null;
        boolean isAdminToken = false;
        boolean isCEndToken = false;
        try {
            parsedClaims = parseToken(token);
            isAdminToken = true;
        } catch (Exception adminErr) {
            if (properties.getAppJwtSecret() != null && !properties.getAppJwtSecret().isEmpty()) {
                try {
                    parsedClaims = parseAppToken(token);
                    isCEndToken = true;
                } catch (Exception appErr) {
                    log.warn("Token validation failed (admin & c_end): {}", appErr.getMessage());
                }
            } else {
                log.warn("Token validation failed: {}", adminErr.getMessage());
            }
        }

        if (!isAdminToken && !isCEndToken) {
            return unauthorized(exchange, "无效的访问令牌");
        }

        final Claims claims = parsedClaims;
        final boolean cEndToken = isCEndToken;

        String jti = claims.getId();
        // C 端令牌：无 jti 的存量 token 直接拒绝（新签发的都有），强制重新登录获取可吊销的新令牌
        if (jti == null) {
            return unauthorized(exchange, cEndToken ? "登录已过期，请重新登录" : "无效的访问令牌");
        }

        // 检查 Redis 黑名单（管理端 auth:blacklist: / C 端 app:blacklist: 前缀不同）
        String blacklistPrefix = cEndToken ? APP_BLACKLIST_PREFIX : BLACKLIST_PREFIX;
        ServerWebExchange finalExchange = exchange;
        return redisTemplate.hasKey(blacklistPrefix + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(finalExchange, "令牌已失效，请重新登录");
                    }

                    if (cEndToken) {
                        // C 端：sub 即 mall_user.userId，注入 X-User-Id 供下游使用；
                        // C 端请求不做管理端 admin-urls 角色校验
                        ServerHttpRequest mutatedRequest = finalExchange.getRequest().mutate()
                                .header("X-User-Id", claims.getSubject())
                                .header("X-Shop-Id", "0")
                                .build();
                        return chain.filter(finalExchange.mutate().request(mutatedRequest).build());
                    }

                    Object userId = claims.get("user_id");
                    Object userName = claims.get("user_name");
                    Object shopId = claims.get("shop_id");

                    Object roleKeysObj = claims.get("role_keys");
                    List<String> roleKeys = new ArrayList<>();
                    if (roleKeysObj instanceof Collection) {
                        for (Object r : (Collection<?>) roleKeysObj) {
                            roleKeys.add(String.valueOf(r));
                        }
                    }
                    // 存量 token 无 role_keys 时 roleKeys 为空列表，访问 admin-urls 会被拒绝。
                    // 这是有意设计：强制旧 token 重新登录以获取带角色信息的新 token，不放宽。
                    if (isAdminOnly(cleanedRequest) && !roleKeys.contains(Constants.ROLE_KEY_ADMIN)) {
                        return forbidden(finalExchange, "无权限执行此操作");
                    }

                    ServerHttpRequest mutatedRequest = finalExchange.getRequest().mutate()
                            .header("X-User-Id", userId != null ? userId.toString() : "")
                            .header("X-User-Name", userName != null ? userName.toString() : "")
                            .header("X-Shop-Id", shopId != null ? shopId.toString() : "0")
                            .header("X-User-Roles", String.join(",", roleKeys))
                            .build();

                    return chain.filter(finalExchange.mutate().request(mutatedRequest).build());
                });
    }

    private Claims parseToken(String token) {
        byte[] keyBytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        Key key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 按 C 端密钥解析 c_end 令牌（网关统一校验：非 c_end 类型视为无效）
     */
    private Claims parseAppToken(String token) {
        byte[] keyBytes = properties.getAppJwtSecret().getBytes(StandardCharsets.UTF_8);
        Key key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (!"c_end".equals(claims.get("type"))) {
            throw new IllegalArgumentException("not a c_end token");
        }
        return claims;
    }

    private String getToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isWhitelisted(String path) {
        return properties.getIgnoreUrls().stream().anyMatch(path::startsWith);
    }

    private boolean isInternal(String path) {
        return properties.getInternalUrls().stream().anyMatch(path::startsWith);
    }

    /**
     * 判断请求是否命中 admin-urls 规则。
     * 注意：路径为前缀匹配（startsWith），如 "*:/admin/user" 也会命中 /admin/users/export 等未来新增路径。
     * 格式错误的规则（无冒号或冒号在首尾）记录警告后忽略，保持不拦截语义。
     * admin-url-excludes 例外优先：命中则直接返回 false，不做 admin 角色校验（自服务接口）。
     */
    private boolean isAdminOnly(ServerHttpRequest request) {
        String path = request.getPath().value();
        String method = request.getMethodValue();
        if (matchesRule(properties.getAdminUrlExcludes(), method, path)) {
            return false;
        }
        return properties.getAdminUrls().stream().anyMatch(rule -> matchesRule(rule, method, path));
    }

    private boolean matchesRule(List<String> rules, String method, String path) {
        return rules != null && rules.stream().anyMatch(rule -> matchesRule(rule, method, path));
    }

    private boolean matchesRule(String rule, String method, String path) {
        int idx = rule.indexOf(':');
        if (idx <= 0 || idx == rule.length() - 1) {
            log.warn("忽略格式错误的 admin-urls 规则: {}", rule);
            return false;
        }
        String ruleMethod = rule.substring(0, idx);
        String rulePath = rule.substring(idx + 1);
        return ("*".equals(ruleMethod) || ruleMethod.equalsIgnoreCase(method))
                && path.startsWith(rulePath);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return errorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return errorResponse(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    private Mono<Void> errorResponse(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"msg\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
