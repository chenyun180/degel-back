package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.AppSpuDetailVO;
import com.degel.app.vo.AppSpuListVO;
import com.degel.app.vo.CategoryTreeVO;

import java.util.List;

/**
 * 商品浏览 Service
 */
public interface ProductService {

    /**
     * 获取商品分类树（有 Redis 缓存，TTL=30min）
     */
    List<CategoryTreeVO> getCategoryTree();

    /**
     * 分页查询商品列表
     *
     * @param categoryId 分类 ID（可选）
     * @param keyword    关键词（可选）
     * @param page       页码
     * @param pageSize   每页大小
     */
    IPage<AppSpuListVO> getProductList(Long categoryId, String keyword, Integer page, Integer pageSize);

    /**
     * 获取商品详情（CompletableFuture 并发调用，SPU 有缓存 TTL=5min）
     *
     * @param spuId SPU ID
     */
    AppSpuDetailVO getProductDetail(Long spuId);
}
