package com.degel.app.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Redis 固定窗口限流器（INCR + 首次 EXPIRE）。
 *
 * <p>用途：reserve 防刷（userId 维度）、登录防刷（手机号/IP 维度）、公开端点保护（IP 维度）。
 * Redis 异常时 fail-open（限流器自身故障不应阻断业务主链路，仅记日志）。
 *
 * <p>key 命名沿用各业务前缀约定：seckill:rl:* / rl:login:* / rl:pub:*，窗口即 TTL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否放行。windowSeconds 窗口内超过 limit 次则拒绝（固定窗口，边界突刺可接受——防刷场景够用）。
     */
    public boolean allow(String key, int limit, int windowSeconds) {
        try {
            Long cnt = stringRedisTemplate.opsForValue().increment(key);
            if (cnt != null && cnt == 1L) {
                stringRedisTemplate.expire(key, windowSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }
            return cnt == null || cnt <= limit;
        } catch (Exception e) {
            log.warn("[RedisRateLimiter] 限流计数失败（fail-open）key={}", key, e);
            return true;
        }
    }

    /** 客户端真实 IP：网关转发取 X-Forwarded-For 首段，直连取 remoteAddr */
    public static String clientIp(HttpServletRequest request) {
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
