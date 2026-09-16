package com.degel.marketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 秒杀 Redis 余量/配置同步（marketing 管理端写操作 → app 预热数据的增量对齐）。
 *
 * <p>⚠️ 跨服务 key 约定：这些 key 由 degel-app 的 SeckillWarmupTask/reserve 写入（DB1），
 * 本类只做"编辑后增量修正"，key 布局必须与 app 的 SeckillServiceImpl 保持一致：
 * <ul>
 *   <li>seckill:cfg:{sessionId}:{skuId}    HASH  startMs/endMs/limit</li>
 *   <li>seckill:stock:{sessionId}:{skuId}  STRING 剩余秒杀库存</li>
 *   <li>seckill:bought:{sessionId}:{skuId} HASH  userId -> 已购数量</li>
 *   <li>seckill:warmed:{sessionId}         STRING 预热幂等标记</li>
 * </ul>
 *
 * <p>核心原则：**只增量、不重置**。活动进行中重置 stock 会把已售量重新放出（超卖），
 * 因此库存调整按 delta INCRBY、减超钳 0；未预热（key 不存在）时跳过——
 * 首次预热会按 DB 最新配置写入。全部 best-effort：Redis 失败仅记日志不阻断管理端操作，
 * 补救手段是"重新预热"（仅未开场次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRedisSync {

    private static final String CFG_PREFIX = "seckill:cfg:";
    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String BOUGHT_PREFIX = "seckill:bought:";
    private static final String WARMED_PREFIX = "seckill:warmed:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 库存编辑后的余量增量同步：delta > 0 加余量、delta < 0 减余量。
     * key 不存在（未预热/已过期）→ 跳过；减后 < 0 → 钳 0（减超的部分视为已被售出占用）。
     */
    public void syncStockDelta(Long sessionId, Long skuId, int delta) {
        if (delta == 0) {
            return;
        }
        String key = STOCK_PREFIX + sessionId + ":" + skuId;
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                log.info("[SeckillRedisSync] 余量未预热，跳过增量同步 sessionId={} skuId={} delta={}",
                        sessionId, skuId, delta);
                return;
            }
            Long remaining = stringRedisTemplate.opsForValue().increment(key, delta);
            if (remaining != null && remaining < 0) {
                stringRedisTemplate.opsForValue().set(key, "0");
                log.warn("[SeckillRedisSync] 库存调减超过当前余量，钳制为 0 sessionId={} skuId={} delta={}",
                        sessionId, skuId, delta);
            }
            log.info("[SeckillRedisSync] 余量增量同步完成 sessionId={} skuId={} delta={} remaining={}",
                    sessionId, skuId, delta, remaining == null ? "?" : Math.max(remaining, 0));
        } catch (Exception e) {
            log.error("[SeckillRedisSync] 余量增量同步失败 sessionId={} skuId={} delta={}（best-effort，不阻断）",
                    sessionId, skuId, delta, e);
        }
    }

    /** 限购数编辑后同步 cfg.limit（key 不存在跳过） */
    public void syncLimit(Long sessionId, Long skuId, Integer perLimit) {
        if (perLimit == null) {
            return;
        }
        String key = CFG_PREFIX + sessionId + ":" + skuId;
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.opsForHash().put(key, "limit", String.valueOf(perLimit));
                log.info("[SeckillRedisSync] 限购同步完成 sessionId={} skuId={} limit={}", sessionId, skuId, perLimit);
            }
        } catch (Exception e) {
            log.error("[SeckillRedisSync] 限购同步失败 sessionId={} skuId={}（best-effort，不阻断）",
                    sessionId, skuId, e);
        }
    }

    /** 场次起止时间编辑后同步全部商品的 cfg.startMs/endMs（key 不存在的商品跳过） */
    public void syncSessionTime(Long sessionId, Iterable<Long> skuIds, LocalDateTime start, LocalDateTime end) {
        long startMs = toEpochMs(start);
        long endMs = toEpochMs(end);
        try {
            for (Long skuId : skuIds) {
                String key = CFG_PREFIX + sessionId + ":" + skuId;
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    stringRedisTemplate.opsForHash().put(key, "startMs", String.valueOf(startMs));
                    stringRedisTemplate.opsForHash().put(key, "endMs", String.valueOf(endMs));
                }
            }
            log.info("[SeckillRedisSync] 场次时间同步完成 sessionId={} start={} end={}", sessionId, start, end);
        } catch (Exception e) {
            log.error("[SeckillRedisSync] 场次时间同步失败 sessionId={}（best-effort，不阻断）", sessionId, e);
        }
    }

    /**
     * 重新预热（仅未开场次安全）：删除 warmed 标记与该场次全部数据 key，
     * 下个预热周期（app WarmupTask 每分钟/开卖前 10min，或 reserve 懒预热）按 DB 最新配置重建。
     * 已开场次禁止调用——重置 stock 会把已售量重新放出（超卖）。
     */
    public void clearWarmup(Long sessionId, Iterable<Long> skuIds) {
        try {
            for (Long skuId : skuIds) {
                stringRedisTemplate.delete(CFG_PREFIX + sessionId + ":" + skuId);
                stringRedisTemplate.delete(STOCK_PREFIX + sessionId + ":" + skuId);
                stringRedisTemplate.delete(BOUGHT_PREFIX + sessionId + ":" + skuId);
            }
            stringRedisTemplate.delete(WARMED_PREFIX + sessionId);
            log.info("[SeckillRedisSync] 场次预热数据已清除，等待重预热 sessionId={}", sessionId);
        } catch (Exception e) {
            throw new IllegalStateException("清除预热数据失败，请稍后重试", e);
        }
    }

    /** 与 app 同口径：服务器本地时区 → epoch ms */
    private long toEpochMs(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
