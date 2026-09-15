package com.degel.app.service.impl;

import com.degel.app.feign.BannerFeignClient;
import com.degel.app.service.BannerService;
import com.degel.app.vo.BannerVO;
import com.degel.common.core.R;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轮播图 ServiceImpl
 * 缓存策略：Redis 5 分钟 TTL；空结果不写缓存（同商品分类树/推荐列表约定），
 * 避免营销服务降级时的空数据被缓存住。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private static final String CACHE_BANNER_LIST = "banner:list";
    private static final long CACHE_TTL_MINUTES = 5;

    /** Redis JSON 反序列化回来是 List<LinkedHashMap>，需 convertValue 还原（同 ProductServiceImpl） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BannerFeignClient bannerFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 文件访问基地址（网关）。库里存 objectKey（不含 host），C 端小程序无法用相对路径，
     * 需要拼成绝对 URL；生产通过配置指向公网网关/CDN 域名。
     */
    @Value("${degel.app.file-base-url:http://localhost:9999}")
    private String fileBaseUrl;

    @Override
    public List<BannerVO> list() {
        // 1. 先查 Redis 缓存
        Object cached = redisTemplate.opsForValue().get(CACHE_BANNER_LIST);
        if (cached != null) {
            return OBJECT_MAPPER.convertValue(cached, new TypeReference<List<BannerVO>>() {});
        }

        // 2. Cache miss：Feign 调用营销服务；banner 挂了不能影响首页，全链路吞异常降级空列表
        List<BannerVO> banners;
        try {
            R<List<BannerVO>> result = bannerFeignClient.list();
            if (result == null || result.getCode() != 200 || result.getData() == null
                    || result.getData().isEmpty()) {
                // fail / null / 空数据不写缓存，避免降级空数据被缓存 5 分钟
                return Collections.emptyList();
            }
            banners = result.getData();
        } catch (Exception e) {
            log.error("[BannerServiceImpl] Feign 调用轮播图失败", e);
            return Collections.emptyList();
        }

        // 3. image 拼完整 URL 后写缓存，TTL=5min
        banners.forEach(vo -> vo.setImage(fileUrl(vo.getImage())));
        redisTemplate.opsForValue().set(CACHE_BANNER_LIST, banners, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return banners;
    }

    /** objectKey → 绝对 URL；历史存量数据里的完整 URL 原样放行 */
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
