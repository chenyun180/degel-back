package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.FootprintVO;

/**
 * 浏览足迹 Service（Redis ZSET，存 30 天滑动 TTL、上限 100、去重置顶）
 */
public interface FootprintService {

    /**
     * 记录足迹（异步 + 吞异常，绝不影响详情主链路；由商品详情成功路径调用）
     */
    void record(Long userId, Long spuId);

    /**
     * 足迹列表（按浏览时间倒序，商品信息实时回查，下架商品 invalid=true）
     */
    IPage<FootprintVO> list(Long userId, Integer page, Integer pageSize);

    /**
     * 删除单条足迹
     */
    void remove(Long userId, Long spuId);

    /**
     * 清空足迹
     */
    void clear(Long userId);
}
