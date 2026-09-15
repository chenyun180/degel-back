package com.degel.app.service;

import com.degel.app.vo.BannerVO;

import java.util.List;

/**
 * 轮播图 Service
 */
public interface BannerService {

    /**
     * C 端生效轮播图列表（Redis 缓存 5 分钟，空结果不写缓存）
     */
    List<BannerVO> list();
}
