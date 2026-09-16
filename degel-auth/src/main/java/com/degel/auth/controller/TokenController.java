package com.degel.auth.controller;

import com.degel.common.core.Constants;
import com.degel.common.core.R;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class TokenController {

    private final StringRedisTemplate redisTemplate;

    @Value("${degel.security.jwt-secret}")
    private String jwtSecret;

    private static final String BLACKLIST_PREFIX = Constants.AUTH_BLACKLIST_PREFIX;

    /**
     * 登出：把 access token 的 jti 写入 Redis 黑名单（TTL=剩余有效期），网关每请求校验。
     * 注意 JwtTokenStore 无法删除 token，黑名单是唯一吊销手段。
     * 已过期的 token 无事可做，返回 ok；解析失败（伪造/损坏）返回失败，让前端能感知
     * "注销可能未生效"，而不是一律 ok 的尽力而为语义。
     */
    @DeleteMapping
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.ok();
        }
        String tokenValue = authHeader.substring(7);
        Claims claims;
        try {
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            Key key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(tokenValue)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // token 已过期：本来就用不了，无黑名单可写，视为已登出
            return R.ok();
        } catch (Exception e) {
            log.warn("Logout token parse failed: {}", e.getMessage());
            return R.fail(401, "令牌无效，无法完成登出");
        }

        String jti = claims.getId();
        Date expiration = claims.getExpiration();

        if (jti != null && expiration != null) {
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
            }
        }
        return R.ok();
    }
}
