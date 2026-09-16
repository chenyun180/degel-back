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

    private static final String BLACKLIST_PREFIX = Constants.AUTH_BLACKLIST_PREFIX;

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
                        // C 端令牌仅可访问 C 端路由（/app/**）。管理端/店铺端路由（/admin、/marketing/platform、
                        // /marketing/shop 等）一律 403——根治 known-issues 中"c_end token 可穿透网关 admin-urls"
                        // 与"/marketing/shop 受穿透影响"两条，不再依赖下游 X-Shop-Id==0 弱兜底。
                        // C 端流量只经 /app/ 路由到 degel-app（H5 代理、小程序请求均如此），正常链路不受影响。
                        String cEndPath = finalExchange.getRequest().getPath().value();
                        if (!cEndPath.startsWith("/app/")) {
                            log.warn("c_end token attempted non-app path: {}", cEndPath);
                            return forbidden(finalExchange, "C 端令牌无权访问该接口");
                        }
                        // C 端：sub 即 mall_user.userId，注入 X-User-Id 供下游使用
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
                    final boolean adminDenied =
                            isAdminOnly(cleanedRequest) && !roleKeys.contains(Constants.ROLE_KEY_ADMIN);

                    // 角色校验 + header 注入 + 放行（版本校验通过后执行）
                    java.util.function.Supplier<Mono<Void>> proceed = () -> {
                        if (adminDenied) {
                            return forbidden(finalExchange, "无权限执行此操作");
                        }
                        ServerHttpRequest mutatedRequest = finalExchange.getRequest().mutate()
                                .header("X-User-Id", userId != null ? userId.toString() : "")
                                .header("X-User-Name", userName != null ? userName.toString() : "")
                                .header("X-Shop-Id", shopId != null ? shopId.toString() : "0")
                                .header("X-User-Roles", String.join(",", roleKeys))
                                .build();
                        return chain.filter(finalExchange.mutate().request(mutatedRequest).build());
                    };

                    // 用户级吊销：改密/禁用/删除用户/停店铺时 admin 侧 INCR auth:tokenver:{userId}，
                    // 旧 token 的 token_version claim < 当前值 → 立即失效（jti 黑名单只能吊销单个 token）
                    if (userId != null) {
                        return redisTemplate.opsForValue()
                                .get(Constants.AUTH_TOKEN_VERSION_PREFIX + userId)
                                .defaultIfEmpty("0")
                                .flatMap(currentVersion -> {
                                    if (parseLongSafe(currentVersion) > tokenVersionClaim(claims)) {
                                        return unauthorized(finalExchange, "凭证已变更，请重新登录");
                                    }
                                    return proceed.get();
                                });
                    }
                    return proceed.get();
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
        return properties.getIgnoreUrls().stream().anyMatch(rule -> matchesPathPrefix(rule, path));
    }

    private boolean isInternal(String path) {
        return properties.getInternalUrls().stream().anyMatch(rule -> matchesPathPrefix(rule, path));
    }

    /**
     * 段感知前缀匹配：规则以 / 结尾 → startsWith 前缀；否则精确匹配或 rule+"/" 前缀。
     * 取代原来的纯 startsWith——那会把 /auth/oauth/token-xxx 之类与规则同前缀的未来路径
     * 也顺带放行进白名单/内部拦截。
     */
    private boolean matchesPathPrefix(String rule, String path) {
        if (rule.endsWith("/")) {
            return path.startsWith(rule);
        }
        return path.equals(rule) || path.startsWith(rule + "/");
    }

    /** token 里的 token_version claim（无 claim 的存量 token 视为 0） */
    private long tokenVersionClaim(Claims claims) {
        Object v = claims.get("token_version");
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return parseLongSafe(v != null ? String.valueOf(v) : null);
    }

    private long parseLongSafe(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
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
