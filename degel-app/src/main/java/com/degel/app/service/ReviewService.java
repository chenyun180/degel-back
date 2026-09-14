package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.ReviewCreateReqVO;
import com.degel.app.vo.ReviewVO;

public interface ReviewService {

    /** 创建评价（订单完成后，一明细一评） */
    void createReview(Long userId, ReviewCreateReqVO reqVO);

    /** 商品评价分页（商品详情页，匿名可访问） */
    IPage<ReviewVO> pageBySpu(Long spuId, int page, int pageSize);

    /** 我的评价分页 */
    IPage<ReviewVO> pageMine(Long userId, int page, int pageSize);

    /** 订单内已评价的明细 id（详情页"已评价"标记） */
    java.util.List<Long> reviewedItemIds(Long orderId, Long userId);

    /** 待评价订单分页（订单列表"待评价"Tab） */
    IPage<com.degel.app.vo.PendingOrderVO> pagePendingOrders(Long userId, int page, int pageSize);
}
