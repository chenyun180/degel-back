package com.degel.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀 Lua 脚本装配。
 *
 * <p>脚本用 {@link DefaultRedisScript} 文本方式加载（不用 SHA 缓存预加载）：
 * Spring Data Redis 首次执行自动 EVALSHA、 miss 回退 EVAL，语义等价且免运维预加载。
 * 必须配 {@link StringRedisTemplate} 使用（KEYS/ARGV 全字符串，序列化器一致），
 * 结果类型统一 Long（Lua number 返回整型）。
 */
@Configuration
public class SeckillScriptConfig {

    /** 秒杀预扣（返回 0/-1/1/2/3） */
    @Bean
    public DefaultRedisScript<Long> seckillReserveScript() {
        return build("script/seckill_reserve.lua");
    }

    /** 抢购资格核销（返回 0/1/2） */
    @Bean
    public DefaultRedisScript<Long> seckillConsumeHoldScript() {
        return build("script/seckill_consume_hold.lua");
    }

    /** 预扣回滚（幂等，返回 0） */
    @Bean
    public DefaultRedisScript<Long> seckillRollbackScript() {
        return build("script/seckill_rollback.lua");
    }

    private static DefaultRedisScript<Long> build(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(read(path));
        script.setResultType(Long.class);
        return script;
    }

    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("秒杀 Lua 脚本加载失败: " + path, e);
        }
    }
}
