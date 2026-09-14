package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderInfo;
import com.degel.order.entity.OrderItem;
import com.degel.order.entity.OrderReview;
import com.degel.order.feign.ProductInnerFeignClient;
import com.degel.order.mapper.OrderReviewMapper;
import com.degel.order.service.IOrderItemService;
import com.degel.order.service.IOrderInfoService;
import com.degel.order.service.IOrderReviewService;
import com.degel.order.vo.ReviewCreateInnerVo;
import com.degel.order.vo.ReviewReplyVo;
import com.degel.order.vo.ReviewVo;
import com.degel.order.vo.InnerRatingVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewServiceImpl extends ServiceImpl<OrderReviewMapper, OrderReview>
        implements IOrderReviewService {

    private final IOrderInfoService orderInfoService;
    private final IOrderItemService orderItemService;
    private final ProductInnerFeignClient productInnerFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReview(ReviewCreateInnerVo vo) {
        OrderInfo order = orderInfoService.getById(vo.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(vo.getUserId())) {
            throw new BusinessException("无权评价该订单");
        }
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new BusinessException("订单确认收货完成后才可评价");
        }
        OrderItem item = orderItemService.getById(vo.getOrderItemId());
        if (item == null || !item.getOrderId().equals(vo.getOrderId())) {
            throw new BusinessException("订单明细不存在");
        }
        // 预查给友好提示；并发窗口由 uk_order_item 唯一约束兜底
        long dup = count(new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getOrderItemId, vo.getOrderItemId()));
        if (dup > 0) {
            throw new BusinessException("该商品已评价过");
        }

        OrderReview review = new OrderReview();
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setUserId(vo.getUserId());
        review.setShopId(order.getShopId());
        review.setSpuId(item.getSpuId());
        review.setSkuId(item.getSkuId());
        review.setSpuName(item.getSpuName());
        review.setSkuSpec(item.getSkuSpec());
        review.setStar(vo.getStar());
        review.setContent(vo.getContent() == null ? "" : vo.getContent());
        review.setStatus(0);
        save(review);

        // 评分冗余回写：失败不回滚评价（主数据已落库），可由下次评价重算覆盖
        refreshSpuRating(item.getSpuId());
        return review.getId();
    }

    @Override
    public IPage<ReviewVo> pageBySpu(Long spuId, int page, int pageSize) {
        return pageReviews(new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getSpuId, spuId)
                .eq(OrderReview::getStatus, 0)
                .orderByDesc(OrderReview::getCreateTime), page, pageSize);
    }

    @Override
    public IPage<ReviewVo> pageMine(Long userId, int page, int pageSize) {
        return pageReviews(new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getUserId, userId)
                .orderByDesc(OrderReview::getCreateTime), page, pageSize);
    }

    @Override
    public IPage<ReviewVo> pageByShop(Long shopId, int page, int pageSize) {
        return pageReviews(new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getShopId, shopId)
                .orderByDesc(OrderReview::getCreateTime), page, pageSize);
    }

    @Override
    public void reply(Long shopId, ReviewReplyVo vo) {
        OrderReview review = getById(vo.getReviewId());
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!shopId.equals(review.getShopId())) {
            throw new BusinessException("无权回复其他店铺的评价");
        }
        if (review.getReply() != null && !review.getReply().isEmpty()) {
            throw new BusinessException("该评价已回复过");
        }
        OrderReview update = new OrderReview();
        update.setId(review.getId());
        update.setReply(vo.getReply());
        update.setReplyTime(LocalDateTime.now());
        updateById(update);
    }

    @Override
    public List<Long> reviewedItemIds(Long orderId, Long userId) {
        List<OrderReview> reviews = list(new LambdaQueryWrapper<OrderReview>()
                .select(OrderReview::getOrderItemId)
                .eq(OrderReview::getOrderId, orderId)
                .eq(OrderReview::getUserId, userId));
        return reviews.stream().map(OrderReview::getOrderItemId).collect(Collectors.toList());
    }

    @Override
    public IPage<com.degel.order.vo.PendingOrderVo> pagePendingOrders(Long userId, int page, int pageSize) {
        // 已完成订单查全量 → 内存过滤出有未评明细的 → 内存分页（用户维度量级有限，total 精确）
        List<OrderInfo> doneOrders = orderInfoService.list(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(OrderInfo::getStatus, 3)
                .orderByDesc(OrderInfo::getReceiveTime));
        if (doneOrders.isEmpty()) {
            return new Page<>(page, pageSize);
        }

        List<Long> orderIds = doneOrders.stream().map(OrderInfo::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemMap = orderItemService.listByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, List<OrderReview>> reviewMap = list(new LambdaQueryWrapper<OrderReview>()
                        .select(OrderReview::getOrderId, OrderReview::getOrderItemId)
                        .eq(OrderReview::getUserId, userId)
                        .in(OrderReview::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderReview::getOrderId));

        List<com.degel.order.vo.PendingOrderVo> pending = new ArrayList<>();
        for (OrderInfo order : doneOrders) {
            Set<Long> reviewed = reviewMap.getOrDefault(order.getId(), Collections.emptyList())
                    .stream().map(OrderReview::getOrderItemId).collect(Collectors.toSet());
            List<OrderItem> unreviewed = itemMap.getOrDefault(order.getId(), Collections.emptyList())
                    .stream().filter(it -> !reviewed.contains(it.getId()))
                    .collect(Collectors.toList());
            if (unreviewed.isEmpty()) {
                continue;
            }
            com.degel.order.vo.PendingOrderVo vo = new com.degel.order.vo.PendingOrderVo();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setPayAmount(order.getPayAmount());
            vo.setCreateTime(order.getCreateTime());
            vo.setPendingCount(unreviewed.size());
            vo.setItems(unreviewed.stream().map(it -> {
                com.degel.order.vo.PendingOrderVo.PendingItemVo itemVo = new com.degel.order.vo.PendingOrderVo.PendingItemVo();
                itemVo.setOrderItemId(it.getId());
                itemVo.setSpuId(it.getSpuId());
                itemVo.setSpuName(it.getSpuName());
                itemVo.setSkuSpec(it.getSkuSpec());
                itemVo.setSkuImage(it.getSkuImage());
                return itemVo;
            }).collect(Collectors.toList()));
            pending.add(vo);
        }

        int from = Math.min((page - 1) * pageSize, pending.size());
        int to = Math.min(from + pageSize, pending.size());
        Page<com.degel.order.vo.PendingOrderVo> result = new Page<>(page, pageSize, pending.size());
        result.setRecords(new ArrayList<>(pending.subList(from, to)));
        return result;
    }

    private IPage<ReviewVo> pageReviews(LambdaQueryWrapper<OrderReview> wrapper, int page, int pageSize) {
        Page<OrderReview> p = new Page<>(page, Math.min(pageSize, 50));
        IPage<OrderReview> result = page(p, wrapper);
        Page<ReviewVo> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return vo;
    }

    private ReviewVo toVo(OrderReview r) {
        ReviewVo vo = new ReviewVo();
        vo.setId(r.getId());
        vo.setOrderId(r.getOrderId());
        vo.setOrderItemId(r.getOrderItemId());
        vo.setSpuId(r.getSpuId());
        vo.setSkuId(r.getSkuId());
        vo.setSpuName(r.getSpuName());
        vo.setSkuSpec(r.getSkuSpec());
        vo.setStar(r.getStar());
        vo.setContent(r.getContent());
        vo.setReply(r.getReply());
        vo.setShopId(r.getShopId());
        vo.setReplyTime(r.getReplyTime());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    /**
     * 重算 SPU 评分并回写商品服务。评价是低频写，全量重算比增量维护更不易漂移；
     * Feign 失败只记日志：下次评价会再次触发重算覆盖，无需补偿任务。
     */
    private void refreshSpuRating(Long spuId) {
        try {
            List<OrderReview> reviews = list(new LambdaQueryWrapper<OrderReview>()
                    .select(OrderReview::getStar)
                    .eq(OrderReview::getSpuId, spuId)
                    .eq(OrderReview::getStatus, 0));
            if (reviews.isEmpty()) {
                return;
            }
            double avg = reviews.stream().mapToInt(OrderReview::getStar).average().orElse(0);
            InnerRatingVo vo = new InnerRatingVo();
            vo.setSpuId(spuId);
            vo.setRatingAvg(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
            vo.setRatingCount(reviews.size());
            productInnerFeignClient.updateRating(vo);
        } catch (Exception e) {
            log.error("[OrderReview] SPU 评分回写失败 spuId={}", spuId, e);
        }
    }
}
