package com.degel.app.task;

import com.degel.app.feign.SeckillFeignClient;
import com.degel.app.service.SeckillService;
import com.degel.app.vo.dto.SeckillSessionDTO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 秒杀场次预热任务：每分钟把「启用且 10 分钟内开卖」的场次数据
 * （cfg/stock）提前写入 Redis，避免开卖瞬间懒预热竞争。
 *
 * <p>幂等：warmSession 内部 SETNX seckill:warmed:{sessionId}，重复触发直接返回；
 * 失败只记日志（reserve 路径仍有懒预热兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillWarmupTask {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 开卖前多久预热 */
    private static final long WARM_AHEAD_MS = 10 * 60 * 1000;

    private final SeckillFeignClient seckillFeignClient;
    private final SeckillService seckillService;

    @Scheduled(cron = "5 * * * * ?")
    public void warmupSessions() {
        try {
            R<List<SeckillSessionDTO>> resp = seckillFeignClient.current();
            if (resp == null || resp.getCode() != 200 || resp.getData() == null || resp.getData().isEmpty()) {
                return;
            }
            long warmLine = System.currentTimeMillis() + WARM_AHEAD_MS;
            for (SeckillSessionDTO session : resp.getData()) {
                // 启用（status=1）且 start<=now+10min 的场次才需要预热
                if (!Integer.valueOf(1).equals(session.getStatus())
                        || session.getStartTime() == null
                        || toEpochMs(session.getStartTime()) > warmLine) {
                    continue;
                }
                try {
                    seckillService.warmSession(session.getId());
                } catch (Exception e) {
                    log.error("[SeckillWarmupTask] 场次预热失败 sessionId={}", session.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("[SeckillWarmupTask] 预热任务执行失败", e);
        }
    }

    private long toEpochMs(String time) {
        return LocalDateTime.parse(time, TS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
