package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.FavoriteVO;

/**
 * 商品收藏 Service
 */
public interface FavoriteService {

    /**
     * 收藏商品（幂等：重复收藏视为成功）；商品不存在/不可见抛业务异常
     */
    void add(Long userId, Long spuId);

    /**
     * 取消收藏（物理删除；未收藏也视为成功）
     */
    void remove(Long userId, Long spuId);

    /**
     * 收藏列表（按收藏时间倒序，商品信息实时回查，下架商品 invalid=true）
     */
    IPage<FavoriteVO> list(Long userId, Integer page, Integer pageSize);

    /**
     * 是否已收藏（详情页按钮状态用）
     */
    boolean isFavorite(Long userId, Long spuId);
}
