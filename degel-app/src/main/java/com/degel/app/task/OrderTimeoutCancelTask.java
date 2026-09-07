package com.degel.app.task;

import com.degel.app.feign.OrderFeignClient;
import com.degel.app.feign.StockFeignClient;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.dto.StockRestoreVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单自动取消任务
 *
 * <p>取消动作由 degel-order 原子完成（UPDATE ... WHERE status=0，与并发支付互斥），
 * 本任务负责触发并恢复已取消订单的库存。
 *
 * <p>TODO(MQ)：当前实现为定时轮询（每分钟全量扫描超时订单），存在最多 1 分钟的取消延迟、
 * 空轮询开销与多实例重复触发的问题（后者已被 WHERE status=0 幂等吸收）。
 * 后续引入 MQ（RocketMQ 延迟消息 / RabbitMQ 死信队列）替代：
 * 下单时发一条 30 分钟延迟消息，到期消费时执行单笔取消，实现准点触发、零扫描。
 *
 * <p>注意：恢复库存失败只记日志不回滚取消（订单已取消是正确终态，库存少恢复属数据订正项，
 * 与 PayServiceImpl 的支付补偿同属"待补偿任务"范畴）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelTask {

    private final OrderFeignClient orderFeignClient;
    private final StockFeignClient stockFeignClient;
    private final com.degel.app.feign.MarketingFeignClient marketingFeignClient;

    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        R<java.util.List<OrderInfoVO>> resp;
        try {
            resp = orderFeignClient.cancelTimeoutOrders();
        } catch (Exception e) {
            log.error("[OrderTimeoutCancelTask] 调用超时取消失败", e);
            return;
        }
        if (resp == null || resp.getCode() != 200 || resp.getData() == null || resp.getData().isEmpty()) {
            return;
        }

        for (OrderInfoVO order : resp.getData()) {
            log.info("[OrderTimeoutCancelTask] 订单超时自动取消 orderId={} orderNo={}", order.getId(), order.getOrderNo());
            if (order.getItems() == null) {
                continue;
            }
            for (OrderInfoVO.OrderItemInfoVO item : order.getItems()) {
                try {
                    stockFeignClient.restoreStock(new StockRestoreVO(item.getSkuId(), item.getQuantity()));
                } catch (Exception ex) {
                    log.error("[OrderTimeoutCancelTask] 恢复库存失败 orderId={} skuId={}", order.getId(), item.getSkuId(), ex);
                }
            }
            // 释放超时取消订单占用的优惠券（幂等；失败由 marketing 60 分钟僵尸锁任务兜底）
            if (order.getCouponId() != null) {
                try {
                    com.degel.app.vo.dto.CouponOrderRefVO ref = new com.degel.app.vo.dto.CouponOrderRefVO();
                    ref.setOrderNo(order.getOrderNo());
                    marketingFeignClient.unlock(ref);
                } catch (Exception ex) {
                    log.error("[OrderTimeoutCancelTask] 券释放失败 orderId={}", order.getId(), ex);
                }
            }
        }
    }
}
