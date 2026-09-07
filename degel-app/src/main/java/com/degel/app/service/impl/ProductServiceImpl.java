package com.degel.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.service.ProductService;
import com.degel.app.vo.*;
import com.degel.common.core.Constants;
import com.degel.common.core.R;
import com.degel.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品浏览 ServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_CATEGORY_TREE = "product:category:tree";
    private static final String CACHE_SPU_PREFIX = "product:spu:";
    private static final String CACHE_RECOMMEND_PREFIX = "product:recommend:page:";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductFeignClient productFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 文件访问基地址（网关）。库里存 objectKey（不含 host），C 端小程序无法用相对路径，
     * 需要拼成绝对 URL；生产通过配置指向公网网关/CDN 域名。
     */
    @org.springframework.beans.factory.annotation.Value("${degel.app.file-base-url:http://localhost:9999}")
    private String fileBaseUrl;

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

    // ==================== B-02: 分类树 ====================

    @Override
    public List<CategoryTreeVO> getCategoryTree() {
        // 1. 先查 Redis 缓存
        Object cached = redisTemplate.opsForValue().get(CACHE_CATEGORY_TREE);
        if (cached != null) {
            return OBJECT_MAPPER.convertValue(cached, new TypeReference<List<CategoryTreeVO>>() {});
        }

        // 2. Cache miss：Feign 调用商品服务；降级（null / fail / data=null）直接报错，
        //    不返回空列表——否则空结果会被当成本次请求的"正常答案"缓存住
        R<List<CategoryTreeVO>> result = productFeignClient.getCategoryTree();
        if (result == null || result.getCode() != 200) {
            throw new BusinessException(50001, "商品分类服务暂不可用，请稍后重试");
        }
        List<CategoryTreeVO> treeList = result.getData();
        if (treeList == null || treeList.isEmpty()) {
            // 3. 空结果不写缓存（同推荐列表的约定），避免降级/异常空数据被缓存 30 分钟
            return Collections.emptyList();
        }

        // 4. 写入 Redis，TTL=30min
        redisTemplate.opsForValue().set(CACHE_CATEGORY_TREE, treeList, 30, TimeUnit.MINUTES);

        return treeList;
    }

    // ==================== B-03: 商品列表 ====================

    @Override
    public IPage<AppSpuListVO> getProductList(Long categoryId, String keyword,
                                               Integer page, Integer pageSize) {
        // 固定传 status=1（上架）& auditStatus=已通过（前端不可覆盖），不指定排序（默认按创建时间倒序）
        R<Page<ProductSpuVO>> result = productFeignClient.getSpuPage(
                categoryId, keyword, page, pageSize, 1, Constants.AUDIT_APPROVED, null
        );

        if (result == null || result.getData() == null) {
            return new Page<>(page, pageSize);
        }

        return convertPage(result.getData());
    }

    // ==================== 推荐（按销量） ====================

    @Override
    public IPage<AppSpuListVO> getRecommendList(Integer page, Integer pageSize) {
        // 入参归一化：page 封顶防匿名遍历堆缓存键/穿透 DB；pageSize 下限防 LIMIT<=0 直打 DB
        if (page == null || page < 1) {
            page = 1;
        }
        if (page > 100) {
            page = 100;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        String cacheKey = CACHE_RECOMMEND_PREFIX + page + ":size:" + pageSize;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return OBJECT_MAPPER.convertValue(cached, new TypeReference<Page<AppSpuListVO>>() {});
        }
        R<Page<ProductSpuVO>> result = productFeignClient.getSpuPage(
                null, null, page, pageSize, 1, Constants.AUDIT_APPROVED, "sale");
        if (result == null || result.getData() == null) {
            return new Page<>(page, pageSize);
        }
        IPage<AppSpuListVO> converted = convertPage(result.getData());
        // 空结果页不写缓存，避免无商品可推荐时长期缓存空页
        if (!CollectionUtils.isEmpty(converted.getRecords())) {
            redisTemplate.opsForValue().set(cacheKey, converted, 5, TimeUnit.MINUTES);
        }
        return converted;
    }

    /** Feign SPU 分页结果 → AppSpuListVO 分页（商品列表与推荐共用） */
    private IPage<AppSpuListVO> convertPage(IPage<ProductSpuVO> sourcePage) {
        List<AppSpuListVO> voList = sourcePage.getRecords().stream()
                .map(this::toAppSpuListVO)
                .collect(Collectors.toList());

        Page<AppSpuListVO> resultPage = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    private AppSpuListVO toAppSpuListVO(ProductSpuVO spu) {
        AppSpuListVO vo = new AppSpuListVO();
        vo.setSpuId(spu.getId());
        vo.setName(spu.getName());
        vo.setMainImage(fileUrl(spu.getMainImage()));
        vo.setMinPrice(spu.getMinPrice());
        vo.setSaleCount(spu.getSaleCount());
        return vo;
    }

    // ==================== B-04: 商品详情 ====================

    @Override
    public AppSpuDetailVO getProductDetail(Long spuId) {
        // 1. 尝试从 Redis 获取 SPU 基本信息缓存
        String spuCacheKey = CACHE_SPU_PREFIX + spuId;
        Object cachedSpu = redisTemplate.opsForValue().get(spuCacheKey);
        ProductSpuVO spuVO = null;
        if (cachedSpu != null) {
            spuVO = OBJECT_MAPPER.convertValue(cachedSpu, ProductSpuVO.class);
        }

        // 2. CompletableFuture 并发调用（SPU 命中缓存时仍需实时获取 SKU）
        final ProductSpuVO finalSpuVO = spuVO;

        CompletableFuture<ProductSpuVO> spuFuture = (finalSpuVO != null)
                ? CompletableFuture.completedFuture(finalSpuVO)
                : CompletableFuture.supplyAsync(() -> {
            R<ProductSpuVO> r = productFeignClient.getSpuDetail(spuId);
            return (r != null) ? r.getData() : null;
        });

        CompletableFuture<List<ProductSkuVO>> skuFuture = CompletableFuture.supplyAsync(() -> {
            R<List<ProductSkuVO>> r = productFeignClient.getSkuList(spuId);
            return (r != null && r.getData() != null) ? r.getData() : Collections.emptyList();
        });

        // 3. 等待并发任务，超时 3 秒降级
        ProductSpuVO spu;
        List<ProductSkuVO> skuList;
        try {
            CompletableFuture.allOf(spuFuture, skuFuture).get(3, TimeUnit.SECONDS);
            spu = spuFuture.get();
            skuList = skuFuture.get();
        } catch (Exception e) {
            log.error("[ProductServiceImpl] getProductDetail 并发调用超时或失败，spuId={}", spuId, e);
            throw new BusinessException(50001, "商品服务暂不可用，请稍后重试");
        }

        // 4. 校验商品状态：下架或未审核通过则 404
        if (spu == null) {
            throw new BusinessException(40400, "商品不存在");
        }
        if (!Integer.valueOf(1).equals(spu.getStatus())
                || !Integer.valueOf(Constants.AUDIT_APPROVED).equals(spu.getAuditStatus())) {
            throw new BusinessException(40400, "商品不存在或已下架");
        }

        // 5. SPU 基本信息写缓存，TTL=5min（仅 SPU，SKU 实时获取）
        if (finalSpuVO == null) {
            redisTemplate.opsForValue().set(spuCacheKey, spu, 5, TimeUnit.MINUTES);
        }

        // 6. 组装 AppSpuDetailVO
        AppSpuDetailVO detail = new AppSpuDetailVO();
        detail.setSpuId(spu.getId());
        detail.setShopId(spu.getShopId());
        detail.setName(spu.getName());
        detail.setSubtitle(spu.getSubtitle());
        detail.setMainImage(fileUrl(spu.getMainImage()));
        detail.setDetailContent(spu.getDetailContent());
        detail.setSaleCount(spu.getSaleCount());
        detail.setViewCount(spu.getViewCount());

        // 解析图片 JSON
        if (spu.getImages() != null && !spu.getImages().isEmpty()) {
            try {
                detail.setImages(OBJECT_MAPPER.readValue(spu.getImages(), new TypeReference<List<String>>() {}).stream()
                        .map(this::fileUrl).collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("[ProductServiceImpl] 解析商品图片 JSON 失败，spuId={}", spuId);
                detail.setImages(Collections.emptyList());
            }
        } else {
            detail.setImages(Collections.emptyList());
        }

        // 组装 SKU 列表
        List<AppSkuVO> appSkuVOList = skuList.stream()
                .map(sku -> {
                    AppSkuVO skuVO = new AppSkuVO();
                    skuVO.setSkuId(sku.getId());
                    skuVO.setSkuCode(sku.getSkuCode());
                    skuVO.setPrice(sku.getPrice());
                    skuVO.setOriginalPrice(sku.getOriginalPrice());
                    skuVO.setStock(sku.getStock());
                    skuVO.setImage(fileUrl(sku.getImage()));
                    skuVO.setSoldOut(sku.getStock() == null || sku.getStock() <= 0);
                    // 解析规格 JSON
                    if (sku.getSpecData() != null && !sku.getSpecData().isEmpty()) {
                        try {
                            skuVO.setSpecData(OBJECT_MAPPER.readValue(sku.getSpecData(),
                                    new TypeReference<Map<String, String>>() {}));
                        } catch (Exception e) {
                            log.warn("[ProductServiceImpl] 解析 SKU 规格 JSON 失败，skuId={}", sku.getId());
                        }
                    }
                    return skuVO;
                })
                .collect(Collectors.toList());

        detail.setSkuList(appSkuVOList);
        return detail;
    }
}
