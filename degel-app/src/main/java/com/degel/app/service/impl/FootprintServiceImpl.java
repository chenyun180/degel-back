package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.service.FootprintService;
import com.degel.app.vo.FootprintVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.Constants;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 浏览足迹 ServiceImpl（Redis ZSET：member=spuId score=浏览时间戳；
 * 去重置顶、上限 100、30 天滑动 TTL——存储 30 天，展示由前端按 7 天分组）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootprintServiceImpl implements FootprintService {

    private static final String KEY_PREFIX = "footprint:";
    private static final long MAX_ENTRIES = 100;
    private static final long TTL_DAYS = 30;

    private final ProductFeignClient productFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${degel.app.file-base-url:http://localhost:9999}")
    private String fileBaseUrl;

    @Override
    public void record(Long userId, Long spuId) {
        if (userId == null || spuId == null) {
            return;
        }
        // 异步 + 全流程吞异常：足迹是纯展示增强，绝不能影响商品详情主链路
        final String key = KEY_PREFIX + userId;
        CompletableFuture.runAsync(() -> {
            try {
                // 先删后加 = 同商品重复浏览去重置顶
                redisTemplate.opsForZSet().remove(key, spuId);
                redisTemplate.opsForZSet().add(key, spuId, System.currentTimeMillis());
                Long size = redisTemplate.opsForZSet().zCard(key);
                if (size != null && size > MAX_ENTRIES) {
                    redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_ENTRIES - 1);
                }
                redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("[FootprintService] 足迹记录失败 userId={} spuId={}", userId, spuId, e);
            }
        });
    }

    @Override
    public IPage<FootprintVO> list(Long userId, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 50);
        String key = KEY_PREFIX + userId;

        Long total;
        Set<ZSetOperations.TypedTuple<Object>> tuples;
        try {
            total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return new Page<>(p, size, 0);
            }
            // 按浏览时间倒序分页（ZSET 下标即排名）
            tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(key, (long) (p - 1) * size, (long) p * size - 1);
        } catch (Exception e) {
            log.warn("[FootprintService] 足迹读取失败 userId={}", userId, e);
            return new Page<>(p, size, 0);
        }
        if (tuples == null || tuples.isEmpty()) {
            return new Page<>(p, size, total);
        }

        // 批量实时回查商品信息（失败降级为"已失效"展示，足迹记录不丢）
        List<Long> spuIds = tuples.stream()
                .map(t -> Long.valueOf(String.valueOf(t.getValue())))
                .collect(Collectors.toList());
        Map<Long, ProductSpuVO> spuMap = batchGetSpuMap(spuIds);

        List<FootprintVO> records = tuples.stream().map(t -> {
            FootprintVO vo = new FootprintVO();
            Long spuId = Long.valueOf(String.valueOf(t.getValue()));
            vo.setSpuId(spuId);
            vo.setViewTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(t.getScore().longValue()), ZoneId.systemDefault()));
            ProductSpuVO spu = spuMap.get(spuId);
            if (spu != null) {
                vo.setSpuName(spu.getName());
                vo.setMainImage(fileUrl(spu.getMainImage()));
                vo.setMinPrice(spu.getMinPrice());
                vo.setSaleCount(spu.getSaleCount());
                vo.setInvalid(!Integer.valueOf(1).equals(spu.getStatus())
                        || !Integer.valueOf(Constants.AUDIT_APPROVED).equals(spu.getAuditStatus()));
            } else {
                vo.setInvalid(true);
            }
            return vo;
        }).collect(Collectors.toList());

        Page<FootprintVO> result = new Page<>(p, size, total);
        result.setRecords(records);
        return result;
    }

    @Override
    public void remove(Long userId, Long spuId) {
        try {
            redisTemplate.opsForZSet().remove(KEY_PREFIX + userId, spuId);
        } catch (Exception e) {
            log.warn("[FootprintService] 足迹删除失败 userId={} spuId={}", userId, spuId, e);
        }
    }

    @Override
    public void clear(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[FootprintService] 足迹清空失败 userId={}", userId, e);
        }
    }

    /** 批量回查 SPU → id 索引 Map；失败返回空 Map（调用方按已失效处理） */
    private Map<Long, ProductSpuVO> batchGetSpuMap(List<Long> spuIds) {
        try {
            R<List<ProductSpuVO>> resp = productFeignClient.batchGetSpu(spuIds);
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().stream()
                        .filter(spu -> spu.getId() != null)
                        .collect(Collectors.toMap(ProductSpuVO::getId, s -> s, (a, b) -> a));
            }
            log.warn("[FootprintService] SPU 批量回查失败: {}", resp == null ? "null" : resp.getMsg());
        } catch (Exception e) {
            log.warn("[FootprintService] SPU 批量回查异常", e);
        }
        return Collections.emptyMap();
    }

    /** 图片相对路径拼完整 URL（存储为裸 key，展示时补 file-base-url 前缀，同 CartServiceImpl） */
    private String fileUrl(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return fileBaseUrl + "/file/view/" + key;
    }
}
