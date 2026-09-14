package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderReview;
import com.degel.order.vo.ReviewCreateInnerVo;
import com.degel.order.vo.ReviewReplyVo;
import com.degel.order.vo.ReviewVo;

import java.util.List;

public interface IOrderReviewService extends IService<OrderReview> {

    /**
     * 创建评价：校验订单归属/已完成/明细归属，uk_order_item 防重；
     * 成功后重算该 SPU 评分并回写 degel-product（失败仅记日志，不影响评价）
     */
    Long createReview(ReviewCreateInnerVo vo);

    /** 商品维度评价分页（C 端商品详情；仅已发布 status=0） */
    IPage<ReviewVo> pageBySpu(Long spuId, int page, int pageSize);

    /** 我的评价分页（C 端） */
    IPage<ReviewVo> pageMine(Long userId, int page, int pageSize);

    /** 店铺维度评价分页（店铺工作台） */
    IPage<ReviewVo> pageByShop(Long shopId, int page, int pageSize);

    /** 商家回复（仅本店评价；一条评价仅回复一次） */
    void reply(Long shopId, ReviewReplyVo vo);

    /** 订单内已评价的明细 id（订单详情页展示"已评价"标记） */
    List<Long> reviewedItemIds(Long orderId, Long userId);

    /**
     * 待评价订单分页（status=3 且存在未评明细；items 仅含未评明细）。
     * 用户已完成订单量级有限，查全量后内存过滤分页，保证 total 精确。
     */
    IPage<com.degel.order.vo.PendingOrderVo> pagePendingOrders(Long userId, int page, int pageSize);
}
