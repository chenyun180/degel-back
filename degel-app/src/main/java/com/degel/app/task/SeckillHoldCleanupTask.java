package com.degel.app.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 秒杀资格（hold）过期兜底清理任务：每分钟扫 seckill:hold:zset 中已到期的 member，
 * 回滚其预扣（bought-1 / stock+1）并删除残余 key。
 *
 * <p>zset member 为复合值 token:userId:sessionId:skuId（回滚上下文冗余进 member）：
 * hold key 被 TTL 自动删除后值即丢失，回滚所需信息全部从 member 还原，
 * 不再依赖 hold key 是否存在——见到过期 member 一律回滚。
 *
 * <p>正常路径不需要本任务——hold SET EX 90s 自动过期；但 hold 过期时预扣的
 * 库存/已购计数不会自动回滚，本任务是唯一的兜底回收路径，必须运行。
 * 全程 best-effort：单条失败只 log.warn，不中断批次（下轮重扫，ZREM 抢占前失败会重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillHoldCleanupTask {

    private static final String HOLD_PREFIX = "seckill:hold:";
    private static final String HOLD_ZSET_KEY = "seckill:hold:zset";
    private static final String BOUGHT_PREFIX = "seckill:bought:";
    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillRollbackScript;

    @Scheduled(cron = "15 * * * * ?")
    public void cleanupExpiredHolds() {
        try {
            String now = String.valueOf(System.currentTimeMillis());
            while (true) {
                // score ∈ [-inf, now] 即已到期的 member（score=过期时刻 ms）
                Set<String> members = stringRedisTemplate.opsForZSet()
                        .rangeByScore(HOLD_ZSET_KEY, 0, Long.parseLong(now), 0, BATCH_SIZE);
                if (members == null || members.isEmpty()) {
                    break;
                }
                for (String member : members) {
                    try {
                        cleanupOne(member);
                    } catch (Exception e) {
                        log.warn("[SeckillHoldCleanupTask] 单条资格清理失败 member={}", member, e);
                    }
                }
                if (members.size() < BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("[SeckillHoldCleanupTask] 清理任务执行失败", e);
        }
    }

    /**
     * 单个过期 member（token:userId:sessionId:skuId）的兜底回滚：
     * DEL hold（清悬挂 key）→ ZREM member 抢占回滚权 → 仅抢占成功才回滚。
     *
     * <p>防双回滚裁决（ZREM-claim，顺序有意为之）：
     * <ul>
     *   <li>consume Lua（GET hold → 校验 → DEL hold → ZREM member）是原子脚本，
     *       其成功的前提是 GET 到 hold；</li>
     *   <li>本方法先 DEL hold——掐断在途 consume 的 GET（若 consume 恰在此后执行，
     *       GET 落空返回 1，用户侧按"已过期"处理，回滚权归本方法）；
     *       若 consume 已先行完成，则 hold 已删、member 已出，本方法 ZREM 返回 0，放弃
     *       （consume 方的 createOrder/cancelReserve 自行处理回滚）。</li>
     * </ul>
     * 双方对同一 member 的 ZREM 最多一方成功，bought-1/stock+1 只发生一次。
     * 注意必须先 DEL 后 ZREM：若先 ZREM 再 DEL，ZREM 与 DEL 之间在途 consume 可整体
     * 插入成功（GET 到未删的 hold）→ 用户成单 + 本方法又回滚 → 超卖。
     *
     * <p>不再依赖 hold key 存在与否判断是否回滚——回滚信息在 member 里，
     * hold key 早已被 TTL 自动删除（正是本修复堵的洞）同样回滚。
     *
     * <p>best-effort 窗口：ZREM 抢占成功后、rollback 执行前进程崩溃会丢一次回滚
     * （member 已出队，不会重试）；窗口极小，与下单补偿路径同一口径。
     */
    private void cleanupOne(String member) {
        // member = token:userId:sessionId:skuId（token 是 UUID 含 - 不含 :，split 安全）
        String[] parts = member.split(":");
        if (parts.length != 4) {
            // 旧格式存量 member（纯 token，无冗余回滚信息）——无法回滚，仅出队清理；
            // 部署后 90s 内存量即消化完（sweep 每分钟跑）
            log.warn("[SeckillHoldCleanupTask] 无法解析的 zset member（疑为旧格式纯 token），跳过回滚 member={}", member);
            stringRedisTemplate.delete(HOLD_PREFIX + member);
            stringRedisTemplate.opsForZSet().remove(HOLD_ZSET_KEY, member);
            return;
        }
        String token = parts[0];
        String userId = parts[1];
        // 1. DEL hold：清理悬挂 key（score 已过期但 TTL 尚未物理删除的边界；已被 TTL 删则 DEL 返回 false，
        //    回滚信息在 member 里，不影响后续）。同时掐断在途 consume 的 GET（见方法 javadoc）
        stringRedisTemplate.delete(HOLD_PREFIX + token);
        // 2. ZREM 抢占回滚权（裁决者）：0 = member 已被 consume/他轮清理出队，放弃
        Long claimed = stringRedisTemplate.opsForZSet().remove(HOLD_ZSET_KEY, member);
        if (claimed == null || claimed == 0) {
            return;
        }
        // 3. 回滚预扣（bought-1 / stock+1，rollback Lua 幂等）；key 按 member 里的 sessionId/skuId 拼装
        try {
            stringRedisTemplate.execute(seckillRollbackScript,
                    Arrays.asList(BOUGHT_PREFIX + parts[2] + ":" + parts[3],
                            STOCK_PREFIX + parts[2] + ":" + parts[3]),
                    userId);
        } catch (Exception e) {
            // member 已出队，本次回滚丢失，不重试——与补偿路径同口径 best-effort
            log.error("[SeckillHoldCleanupTask] 过期资格回滚失败 member={}", member, e);
        }
    }
}
