package com.degel.app.task;

import com.degel.app.feign.OrderFeignClient;
import com.degel.app.service.OrderService;
import com.degel.app.vo.OrderInfoVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 发货后超时自动确认收货任务（照 OrderTimeoutCancelTask 模式：失败只 log 不抛）。
 *
 * <p>收货动作由 degel-order 原子完成（UPDATE ... WHERE status=2，与并发确认收货/售后流转互斥），
 * 本任务仅定时触发。确认收货是纯状态变更（2→3 + receiveTime），无库存/券等副作用。
 *
 * <p>周期：每小时（发货 7 天收货的口径下无需秒级实时）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoReceiveTask {

    /** 发货后自动确认收货天数（行业惯例 7 天） */
    private static final int AUTO_RECEIVE_SHIP_DAYS = 7;

    private final OrderFeignClient orderFeignClient;
    private final OrderService orderService;

    /** 每小时第 15 分执行（避开整点，与 CouponTask 的第 5 分错开） */
    @Scheduled(cron = "0 15 * * * ?")
    public void autoConfirmReceipts() {
        R<List<OrderInfoVO>> resp;
        try {
            resp = orderFeignClient.autoConfirmReceipts(AUTO_RECEIVE_SHIP_DAYS);
        } catch (Exception e) {
            log.error("[OrderAutoReceiveTask] 调用自动确认收货失败", e);
            return;
        }
        if (resp == null || resp.getCode() != 200 || resp.getData() == null || resp.getData().isEmpty()) {
            return;
        }
        log.info("[OrderAutoReceiveTask] 发货超 {} 天订单自动确认收货 {} 单", AUTO_RECEIVE_SHIP_DAYS, resp.getData().size());
        // ✅ 决策：确认收货后发放积分（幂等，best-effort）
        for (OrderInfoVO order : resp.getData()) {
            orderService.grantPointsForOrder(order);
        }
    }
}
