package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.OrderInfo;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.mapper.OrderInfoMapper;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.vo.AfterSaleArbitrateVo;
import com.degel.order.vo.AfterSaleHandleVo;
import com.degel.order.vo.AfterSaleInfoVo;
import com.degel.order.vo.inner.AfterSaleCreateInnerVo;
import com.degel.order.vo.inner.PayRefundInnerVo;
import com.degel.order.feign.PayFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAfterSaleServiceImpl extends ServiceImpl<OrderAfterSaleMapper, OrderAfterSale> implements IOrderAfterSaleService {

    // 注入 Mapper 而非 Service，避免与 OrderInfoServiceImpl 形成构造器循环依赖
    private final OrderInfoMapper orderInfoMapper;
    private final com.degel.order.feign.MarketingFeignClient marketingFeignClient;
    private final PayFeignClient payFeignClient;
    private final com.degel.order.feign.PointsFeignClient pointsFeignClient;
    /** 结算域独立（settlement_* 表），无循环依赖；钩子 best-effort 见两处调用点 */
    private final com.degel.order.service.ISettlementService settlementService;
    /** 站内信（best-effort，失败不阻断售后主流程） */
    private final com.degel.order.service.NotificationService notificationService;

    @Override
    public IPage<OrderAfterSale> pageAfterSales(IPage<OrderAfterSale> page, Long shopId, Integer status, Integer type) {
        LambdaQueryWrapper<OrderAfterSale> wrapper = new LambdaQueryWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getShopId, shopId)
                .eq(status != null, OrderAfterSale::getStatus, status)
                .eq(type != null, OrderAfterSale::getType, type)
                .orderByDesc(OrderAfterSale::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(AfterSaleHandleVo vo, Long shopId) {
        OrderAfterSale afterSale = getById(vo.getAfterSaleId());
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 0) {
            throw new BusinessException("售后单状态不允许操作");
        }

        LambdaUpdateWrapper<OrderAfterSale> updateWrapper = new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, vo.getAfterSaleId());

        if ("agree".equals(vo.getAction())) {
            if (afterSale.getType() == 1) {
                updateWrapper.set(OrderAfterSale::getStatus, 3);
            } else {
                updateWrapper.set(OrderAfterSale::getStatus, 1);
            }
        } else if ("reject".equals(vo.getAction())) {
            updateWrapper.set(OrderAfterSale::getStatus, 5);
        } else {
            throw new BusinessException("无效的操作类型");
        }

        if (vo.getMerchantRemark() != null) {
            updateWrapper.set(OrderAfterSale::getMerchantRemark, vo.getMerchantRemark());
        }
        update(updateWrapper);

        // 整单退款完成（agree + type=1 仅退款）→ 退回优惠券（2→未过期?4:5，幂等）+ 写退款流水。
        // 补贴冲减口径：报表 SQL 以 after_sale status=3 为准剔除该单补贴（NOT EXISTS，无需冲销列）。
        // ⚠️ Feign 在事务内 best-effort：失败仅记日志（券状态可人工/重试修复），不影响售后主流程
        if ("agree".equals(vo.getAction()) && afterSale.getType() == 1) {
            try {
                java.util.Map<String, Long> req = new java.util.HashMap<>(1);
                req.put("orderId", afterSale.getOrderId());
                marketingFeignClient.returnCoupon(req);
            } catch (Exception ex) {
                log.error("[handle] 整单退回券失败（可人工补偿）afterSaleId={} orderId={}",
                        afterSale.getId(), afterSale.getOrderId(), ex);
            }
            // 仅退款的退款即时到账 → 退款流水落库（degel-app mall_payment_log）
            sendRefundLog(afterSale);
            // 积分结算：退回抵扣 + 回收已发（均幂等 best-effort，与退款流水同语义）
            settlePointsOnRefund(afterSale);
            // 结算联动：未结算明细作废 / 已结算明细扣回余额（幂等 best-effort，失败由结算侧 30min 兜底对账补偿）
            try {
                settlementService.onRefundCompleted(afterSale);
            } catch (Exception ex) {
                log.error("[handle] 结算退款联动失败（可兜底补偿）afterSaleId={} orderId={}",
                        afterSale.getId(), afterSale.getOrderId(), ex);
            }
            notificationService.send(afterSale.getUserId(), "aftersale", "退款已到账",
                    "售后单 " + afterSale.getId() + " 已同意，退款 ¥" + afterSale.getRefundAmount()
                            + " 将原路退回，请注意查收");
        }

        // 售后审核结果通知（拒绝 / 退货退款同意）
        if ("reject".equals(vo.getAction())) {
            notificationService.send(afterSale.getUserId(), "aftersale", "售后申请被拒绝",
                    "很抱歉，售后单 " + afterSale.getId() + " 被商家拒绝"
                            + (vo.getMerchantRemark() != null ? "：" + vo.getMerchantRemark() : "")
                            + "。如有异议可申请平台介入");
        } else if ("agree".equals(vo.getAction()) && afterSale.getType() == 2) {
            notificationService.send(afterSale.getUserId(), "aftersale", "退货申请已同意",
                    "售后单 " + afterSale.getId() + " 已同意退货，请尽快寄回商品并填写物流单号");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long afterSaleId, Long shopId) {
        OrderAfterSale afterSale = getById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 2) {
            throw new BusinessException("售后单状态不允许确认收货");
        }

        update(new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, afterSaleId)
                .set(OrderAfterSale::getStatus, 3));

        // 退货退款走到这里（商家确认收到退货）钱才退 → 写退款流水
        sendRefundLog(afterSale);
        // 积分结算：退回抵扣 + 回收已发
        settlePointsOnRefund(afterSale);
        // 结算联动（同 handle 语义：作废待入账 / 扣回已入账，幂等 best-effort）
        try {
            settlementService.onRefundCompleted(afterSale);
        } catch (Exception ex) {
            log.error("[confirmReceive] 结算退款联动失败（可兜底补偿）afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId(), ex);
        }
        notificationService.send(afterSale.getUserId(), "aftersale", "退货退款已到账",
                "商家已确认收到退回商品，售后单 " + afterSaleId + " 退款 ¥"
                        + afterSale.getRefundAmount() + " 将原路退回");
    }

    /**
     * 退款流水落库（best-effort）：degel-app POST /app/inner/pay/refund。
     * 状态机保证同一售后单只走到"钱退回"一次（agree 仅 type=1 / confirmReceive 仅 status=2→3），
     * Feign 超时重试理论上可能重复插流水——接受此风险（与退券同语义），人工对账可辨。
     */
    private void sendRefundLog(OrderAfterSale afterSale) {
        try {
            OrderInfo order = orderInfoMapper.selectById(afterSale.getOrderId());
            PayRefundInnerVo vo = new PayRefundInnerVo();
            vo.setUserId(afterSale.getUserId());
            vo.setOrderId(afterSale.getOrderId());
            vo.setOrderNo(order != null ? order.getOrderNo() : String.valueOf(afterSale.getOrderId()));
            vo.setAmount(afterSale.getRefundAmount());
            payFeignClient.refund(vo);
        } catch (Exception ex) {
            log.error("[refund-log] 退款流水落库失败（可人工补偿）afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId(), ex);
        }
    }

    /**
     * 退款时的积分结算（幂等 best-effort）：
     * - returnRedeem：该单冻结/抵扣的积分无条件全额退回（freeze 流水取数）；
     * - reclaimEarn：该单已发放的积分回收（earn 流水取数；确认收货前退款则未发放 no-op，
     *   余额不足扣至 0 为止）。失败仅记日志，与退券/退款流水同语义可人工补偿。
     */
    private void settlePointsOnRefund(OrderAfterSale afterSale) {
        try {
            pointsFeignClient.returnRedeem(afterSale.getUserId(), getOrderNo(afterSale.getOrderId()));
        } catch (Exception ex) {
            log.error("[points-refund] 退回抵扣积分失败（可人工补偿）afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId(), ex);
        }
        try {
            java.util.Map<String, Object> req = new java.util.HashMap<>(4);
            req.put("userId", afterSale.getUserId());
            req.put("orderId", afterSale.getOrderId());
            req.put("orderNo", getOrderNo(afterSale.getOrderId()));
            pointsFeignClient.reclaimEarn(req);
        } catch (Exception ex) {
            log.error("[points-refund] 回收已发积分失败（可人工补偿）afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId(), ex);
        }
    }

    private String getOrderNo(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        return order != null ? order.getOrderNo() : String.valueOf(orderId);
    }

    /**
     * 退款流水对账补偿（每 10 分钟）：退款完成（status=3）但 mall_payment_log 无 refund
     * 流水的单子补写——兜住 sendRefundLog best-effort 失败（Feign/Redis 抖动）的漏网。
     * 幂等：先查 exists 再写；只扫近 30 天（窗口外的老单人工处理）；
     * 单轮上限 200 防 Feign 风暴。同一单多条 status=3 历史（拒绝后重申请）按订单去重补一条。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void reconcileRefundLogs() {
        try {
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(30);
            java.util.List<OrderAfterSale> done = this.list(new LambdaQueryWrapper<OrderAfterSale>()
                    .eq(OrderAfterSale::getStatus, 3)
                    .ge(OrderAfterSale::getUpdateTime, since)
                    .orderByAsc(OrderAfterSale::getId)
                    .last("LIMIT 200"));
            java.util.Set<Long> seen = new java.util.HashSet<>();
            int repaired = 0;
            for (OrderAfterSale s : done) {
                if (!seen.add(s.getOrderId())) {
                    continue;
                }
                try {
                    com.degel.common.core.R<Boolean> exists = payFeignClient.refundExists(s.getOrderId());
                    if (exists != null && Boolean.TRUE.equals(exists.getData())) {
                        continue;
                    }
                    sendRefundLog(s);
                    repaired++;
                } catch (Exception ex) {
                    log.warn("[refund-reconcile] 查询/补写失败，下轮重试 orderId={}: {}",
                            s.getOrderId(), ex.getMessage());
                }
            }
            if (repaired > 0) {
                log.info("[refund-reconcile] 本轮补写退款流水 {} 单", repaired);
            }
            reconcileCancelledPaidRefunds(since);
        } catch (Exception ex) {
            log.error("[refund-reconcile] 对账任务异常", ex);
        }
    }

    /**
     * 取消退款对账：付过款又被取消的订单（status=4 且有 payTime）若无退款流水则补写——
     * 兜住 cancelPaidOrder 善后 best-effort 失败的单（与售后对账同轮执行，同 LIMIT 节流）
     */
    private void reconcileCancelledPaidRefunds(java.time.LocalDateTime since) {
        java.util.List<OrderInfo> cancelledPaid = orderInfoMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getStatus, 4)
                        .isNotNull(OrderInfo::getPayTime)
                        .ge(OrderInfo::getCancelTime, since)
                        .orderByAsc(OrderInfo::getId)
                        .last("LIMIT 200"));
        int repaired = 0;
        for (OrderInfo order : cancelledPaid) {
            try {
                com.degel.common.core.R<Boolean> exists = payFeignClient.refundExists(order.getId());
                if (exists != null && Boolean.TRUE.equals(exists.getData())) {
                    continue;
                }
                // 复用退款流水写入（金额=实付全额，备注区分取消退款）
                try {
                    PayRefundInnerVo vo = new PayRefundInnerVo();
                    vo.setUserId(order.getUserId());
                    vo.setOrderId(order.getId());
                    vo.setOrderNo(order.getOrderNo());
                    vo.setAmount(order.getPayAmount());
                    vo.setRemark("取消退款");
                    payFeignClient.refund(vo);
                    repaired++;
                    log.warn("[refund-reconcile] 补写取消退款流水 orderId={} amount={}",
                            order.getId(), order.getPayAmount());
                } catch (Exception ex) {
                    log.warn("[refund-reconcile] 取消退款补写失败，下轮重试 orderId={}: {}",
                            order.getId(), ex.getMessage());
                }
            } catch (Exception ex) {
                log.warn("[refund-reconcile] 取消退款查重失败，下轮重试 orderId={}: {}",
                        order.getId(), ex.getMessage());
            }
        }
        if (repaired > 0) {
            log.info("[refund-reconcile] 本轮补写取消退款流水 {} 单", repaired);
        }
    }

    // ==================== C 端内部接口实现 ====================

    @Override
    public Long createInnerAfterSale(AfterSaleCreateInnerVo vo) {
        // 售后窗口校验（法定七天无理由底线，平台可配 aftersale_days）：
        // 仅 status=3 且确认收货在 N 天内的订单可申请。app 侧已查状态，这里做防御性复核 + 窗口判定，
        // 保证规则单点收敛在订单域（改配置即时生效，存量售后单不受影响）。
        OrderInfo order = orderInfoMapper.selectById(vo.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!Integer.valueOf(3).equals(order.getStatus())) {
            throw new BusinessException("仅已完成订单可申请售后");
        }
        int windowDays = settlementService.readAftersaleDays();
        if (order.getReceiveTime() == null
                || order.getReceiveTime().isBefore(java.time.LocalDateTime.now().minusDays(windowDays))) {
            throw new BusinessException("已超过" + windowDays + "天售后窗口，无法申请售后");
        }
        OrderAfterSale afterSale = new OrderAfterSale();
        afterSale.setOrderId(vo.getOrderId());
        afterSale.setUserId(vo.getUserId());
        afterSale.setShopId(vo.getShopId());
        afterSale.setType(vo.getType());
        afterSale.setStatus(0);
        afterSale.setReason(vo.getReason());
        afterSale.setRefundAmount(vo.getRefundAmount());
        save(afterSale);
        return afterSale.getId();
    }

    @Override
    public IPage<AfterSaleInfoVo> pageInnerAfterSales(Long userId, Integer status, Integer page, Integer pageSize) {
        IPage<OrderAfterSale> salePage = page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<OrderAfterSale>()
                        .eq(OrderAfterSale::getUserId, userId)
                        .eq(status != null, OrderAfterSale::getStatus, status)
                        .orderByDesc(OrderAfterSale::getCreateTime));

        Page<AfterSaleInfoVo> result = new Page<>(salePage.getCurrent(), salePage.getSize(), salePage.getTotal());
        List<OrderAfterSale> records = salePage.getRecords();
        if (records.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        // 关联订单号
        Map<Long, String> orderNoMap = orderInfoMapper.selectBatchIds(
                        records.stream().map(OrderAfterSale::getOrderId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(OrderInfo::getId, OrderInfo::getOrderNo, (a, b) -> a));

        result.setRecords(records.stream()
                .map(s -> toInnerVo(s, orderNoMap.get(s.getOrderId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public boolean existsActiveAfterSale(Long orderId, Long userId) {
        return count(new LambdaQueryWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getOrderId, orderId)
                .eq(OrderAfterSale::getUserId, userId)
                .in(OrderAfterSale::getStatus, Arrays.asList(0, 1))) > 0;
    }

    @Override
    public AfterSaleInfoVo getInnerAfterSale(Long id) {
        OrderAfterSale afterSale = getById(id);
        if (afterSale == null) {
            return null;
        }
        OrderInfo order = orderInfoMapper.selectById(afterSale.getOrderId());
        return toInnerVo(afterSale, order != null ? order.getOrderNo() : null);
    }

    private AfterSaleInfoVo toInnerVo(OrderAfterSale afterSale, String orderNo) {
        AfterSaleInfoVo vo = new AfterSaleInfoVo();
        vo.setId(afterSale.getId());
        vo.setOrderId(afterSale.getOrderId());
        vo.setOrderNo(orderNo);
        vo.setUserId(afterSale.getUserId());
        vo.setShopId(afterSale.getShopId());
        vo.setType(afterSale.getType());
        vo.setStatus(afterSale.getStatus());
        vo.setReason(afterSale.getReason());
        vo.setRefundAmount(afterSale.getRefundAmount());
        vo.setMerchantRemark(afterSale.getMerchantRemark());
        vo.setPlatformRemark(afterSale.getPlatformRemark());
        vo.setCreateTime(afterSale.getCreateTime());
        vo.setUpdateTime(afterSale.getUpdateTime());
        return vo;
    }

    // ==================== 平台仲裁（PRD 6.3） ====================

    @Override
    public void applyArbitrateInner(Long afterSaleId, Long userId) {
        OrderAfterSale afterSale = getById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该售后单");
        }
        // CAS 5→6：仅"已拒绝"可申请介入（终态 3/7 不可再申请，防循环）
        boolean ok = update(new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, afterSaleId)
                .eq(OrderAfterSale::getStatus, 5)
                .set(OrderAfterSale::getStatus, 6));
        if (!ok) {
            throw new BusinessException("当前状态无法申请平台介入");
        }
        log.info("[arbitrate] 用户申请平台介入 afterSaleId={} orderId={}", afterSaleId, afterSale.getOrderId());
    }

    @Override
    public IPage<AfterSaleInfoVo> pageArbitrations(IPage<OrderAfterSale> page, Integer status) {
        IPage<OrderAfterSale> raw = page(page, new LambdaQueryWrapper<OrderAfterSale>()
                .eq(status != null, OrderAfterSale::getStatus, status)
                .orderByAsc(OrderAfterSale::getUpdateTime));
        // 平台视角：待仲裁(6)优先展示（调用方默认传 6），历史单据按需查
        Map<Long, String> orderNoMap = raw.getRecords().isEmpty()
                ? Collections.emptyMap()
                : orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getId, raw.getRecords().stream()
                                .map(OrderAfterSale::getOrderId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(OrderInfo::getId, OrderInfo::getOrderNo));
        Page<AfterSaleInfoVo> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(a -> toInnerVo(a, orderNoMap.get(a.getOrderId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arbitrate(AfterSaleArbitrateVo vo, String operator) {
        OrderAfterSale afterSale = getById(vo.getAfterSaleId());
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (vo.getRemark() == null || vo.getRemark().trim().isEmpty()) {
            throw new BusinessException("仲裁意见必填");
        }
        String remark = "[" + (vo.getSupportUser() ? "支持用户" : "维持拒绝") + "] " + operator + "：" + vo.getRemark();
        if (Boolean.TRUE.equals(vo.getSupportUser())) {
            // 支持用户：CAS 6→3，随后走与"商家同意退款"一致的完整退款链路
            boolean ok = update(new LambdaUpdateWrapper<OrderAfterSale>()
                    .eq(OrderAfterSale::getId, vo.getAfterSaleId())
                    .eq(OrderAfterSale::getStatus, 6)
                    .set(OrderAfterSale::getStatus, 3)
                    .set(OrderAfterSale::getPlatformRemark, remark));
            if (!ok) {
                throw new BusinessException("该售后单已被处理");
            }
            // 退券（幂等 best-effort）
            try {
                java.util.Map<String, Long> req = new java.util.HashMap<>(1);
                req.put("orderId", afterSale.getOrderId());
                marketingFeignClient.returnCoupon(req);
            } catch (Exception ex) {
                log.error("[arbitrate] 仲裁退券失败（可人工补偿）afterSaleId={}", afterSale.getId(), ex);
            }
            // 退款流水 + 积分结算（均 best-effort，与 handle agree 同语义）
            sendRefundLog(afterSale);
            settlePointsOnRefund(afterSale);
            // 结算联动：未结算作废 / 已结算扣回（幂等）
            try {
                settlementService.onRefundCompleted(afterSale);
            } catch (Exception ex) {
                log.error("[arbitrate] 结算联动失败（可兜底补偿）afterSaleId={}", afterSale.getId(), ex);
            }
            log.info("[arbitrate] 仲裁支持用户，退款完成 afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId());
            notificationService.send(afterSale.getUserId(), "arbitrate", "平台仲裁：支持用户",
                    "售后单 " + afterSale.getId() + " 平台判定支持用户，退款 ¥"
                            + afterSale.getRefundAmount() + " 将原路退回。" + vo.getRemark());
        } else {
            // 维持拒绝：CAS 6→7（终态，不可再申请介入）
            boolean ok = update(new LambdaUpdateWrapper<OrderAfterSale>()
                    .eq(OrderAfterSale::getId, vo.getAfterSaleId())
                    .eq(OrderAfterSale::getStatus, 6)
                    .set(OrderAfterSale::getStatus, 7)
                    .set(OrderAfterSale::getPlatformRemark, remark));
            if (!ok) {
                throw new BusinessException("该售后单已被处理");
            }
            log.info("[arbitrate] 仲裁维持拒绝 afterSaleId={} orderId={}",
                    afterSale.getId(), afterSale.getOrderId());
            notificationService.send(afterSale.getUserId(), "arbitrate", "平台仲裁：维持拒绝",
                    "售后单 " + afterSale.getId() + " 平台判定维持商家拒绝。" + vo.getRemark());
        }
    }
}
