package com.degel.gateway.filter;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final DegelSecurityProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

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

        Claims claims;
        try {
            claims = parseToken(token);
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return unauthorized(exchange, "无效的访问令牌");
        }

        String jti = claims.getId();
        if (jti == null) {
            return unauthorized(exchange, "无效的访问令牌");
        }

        // 检查 Redis 黑名单
        ServerWebExchange finalExchange = exchange;
        return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(finalExchange, "令牌已失效，请重新登录");
                    }

                    Object userId = claims.get("user_id");
                    Object userName = claims.get("user_name");
                    Object shopId = claims.get("shop_id");

                    ServerHttpRequest mutatedRequest = finalExchange.getRequest().mutate()
                            .header("X-User-Id", userId != null ? userId.toString() : "")
                            .header("X-User-Name", userName != null ? userName.toString() : "")
                            .header("X-Shop-Id", shopId != null ? shopId.toString() : "0")
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
