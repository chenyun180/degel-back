package com.degel.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.service.ProductService;
import com.degel.app.vo.*;
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

    private final ProductFeignClient productFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== B-02: 分类树 ====================

    @Override
    public List<CategoryTreeVO> getCategoryTree() {
        // 1. 先查 Redis 缓存
        Object cached = redisTemplate.opsForValue().get(CACHE_CATEGORY_TREE);
        if (cached != null) {
            return JSON.parseArray(JSON.toJSONString(cached), CategoryTreeVO.class);
        }

        // 2. Cache miss：Feign 调用商品服务
        R<List<CategoryTreeVO>> result = productFeignClient.getCategoryTree();
        List<CategoryTreeVO> treeList = (result != null && result.getData() != null)
                ? result.getData()
                : Collections.emptyList();

        // 3. 写入 Redis，TTL=30min
        redisTemplate.opsForValue().set(CACHE_CATEGORY_TREE, treeList, 30, TimeUnit.MINUTES);

        return treeList;
    }

    // ==================== B-03: 商品列表 ====================

    @Override
    public IPage<AppSpuListVO> getProductList(Long categoryId, String keyword,
                                               Integer page, Integer pageSize) {
        // 固定传 status=1 & auditStatus=1（前端不可覆盖）
        R<IPage<ProductSpuVO>> result = productFeignClient.getSpuPage(
                categoryId, keyword, page, pageSize, 1, 1
        );

        if (result == null || result.getData() == null) {
            return new Page<>(page, pageSize);
        }

        IPage<ProductSpuVO> sourcePage = result.getData();

        // 转换为 AppSpuListVO
        List<AppSpuListVO> voList = sourcePage.getRecords().stream()
                .map(spu -> {
                    AppSpuListVO vo = new AppSpuListVO();
                    vo.setSpuId(spu.getId());
                    vo.setName(spu.getName());
                    vo.setMainImage(spu.getMainImage());
                    vo.setMinPrice(spu.getMinPrice());
                    vo.setSaleCount(spu.getSaleCount());
                    return vo;
                })
                .collect(Collectors.toList());

        Page<AppSpuListVO> resultPage = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    // ==================== B-04: 商品详情 ====================

    @Override
    public AppSpuDetailVO getProductDetail(Long spuId) {
        // 1. 尝试从 Redis 获取 SPU 基本信息缓存
        String spuCacheKey = CACHE_SPU_PREFIX + spuId;
        Object cachedSpu = redisTemplate.opsForValue().get(spuCacheKey);
        ProductSpuVO spuVO = null;
        if (cachedSpu != null) {
            spuVO = JSON.parseObject(JSON.toJSONString(cachedSpu), ProductSpuVO.class);
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

        // 4. 校验商品状态：下架或未审核则 404
        if (spu == null) {
            throw new BusinessException(40400, "商品不存在");
        }
        if (!Integer.valueOf(1).equals(spu.getStatus()) || !Integer.valueOf(1).equals(spu.getAuditStatus())) {
            throw new BusinessException(40400, "商品不存在或已下架");
        }

        // 5. SPU 基本信息写缓存，TTL=5min（仅 SPU，SKU 实时获取）
        if (finalSpuVO == null) {
            redisTemplate.opsForValue().set(spuCacheKey, spu, 5, TimeUnit.MINUTES);
        }

        // 6. 组装 AppSpuDetailVO
        AppSpuDetailVO detail = new AppSpuDetailVO();
        detail.setSpuId(spu.getId());
        detail.setName(spu.getName());
        detail.setSubtitle(spu.getSubtitle());
        detail.setMainImage(spu.getMainImage());
        detail.setDetailContent(spu.getDetailContent());
        detail.setSaleCount(spu.getSaleCount());
        detail.setViewCount(spu.getViewCount());

        // 解析图片 JSON
        if (spu.getImages() != null && !spu.getImages().isEmpty()) {
            try {
                detail.setImages(JSON.parseArray(spu.getImages(), String.class));
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
                    skuVO.setImage(sku.getImage());
                    skuVO.setSoldOut(sku.getStock() == null || sku.getStock() <= 0);
                    // 解析规格 JSON
                    if (sku.getSpecData() != null && !sku.getSpecData().isEmpty()) {
                        try {
                            skuVO.setSpecData(JSON.parseObject(sku.getSpecData(),
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
