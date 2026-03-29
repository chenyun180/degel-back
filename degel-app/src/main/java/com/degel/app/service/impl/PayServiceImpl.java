package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.entity.MallPaymentLog;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.mapper.MallPaymentLogMapper;
import com.degel.app.service.PayService;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.PayLogVO;
import com.degel.app.vo.PayResultVO;
import com.degel.app.vo.dto.InnerRefundReqVO;
import com.degel.app.vo.dto.OrderStatusUpdateVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支付服务实现（C-07 / C-08 / C-09）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final MallPaymentLogMapper mallPaymentLogMapper;
    private final OrderFeignClient orderFeignClient;
    private final RedissonClient redissonClient;

    // =========================================================
    // C-07: POST /app/pay/{orderId} — 发起模拟支付
    // =========================================================

    @Override
    public PayResultVO pay(Long orderId, Long userId) {
        // 1. 查 order_info，校验归属 + status==0
        R<OrderInfoVO> orderResp = orderFeignClient.getOrder(orderId);
        if (orderResp == null || orderResp.getCode() != 200 || orderResp.getData() == null) {
            throw BusinessException.of(40015, "订单不存在");
        }
        OrderInfoVO orderInfo = orderResp.getData();
        if (!userId.equals(orderInfo.getUserId())) {
            throw BusinessException.of(40016, "无权操作该订单");
        }
        if (!Integer.valueOf(0).equals(orderInfo.getStatus())) {
            throw BusinessException.of(40018, "该订单不处于待付款状态，无法支付");
        }

        // 分布式锁：防止并发重复支付（锁住幂等校验 + INSERT 整段逻辑）
        RLock lock = redissonClient.getLock("lock:pay:" + orderId);
        try {
            boolean locked;
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw BusinessException.of(50001, "系统繁忙，请稍后重试");
            }
            if (!locked) {
                throw BusinessException.of(50001, "系统繁忙，请稍后重试");
            }

            try {
                // 幂等校验：同一 order_id 不能重复支付
                Long existCount = mallPaymentLogMapper.selectCount(
                        new LambdaQueryWrapper<MallPaymentLog>()
                                .eq(MallPaymentLog::getOrderId, orderId)
                                .eq(MallPaymentLog::getDirection, "pay")
                                .eq(MallPaymentLog::getStatus, 0)
                );
                if (existCount > 0) {
                    throw BusinessException.of(40018, "该订单已支付，请勿重复操作");
                }

                // 2. INSERT mall_payment_log（direction=pay, status=0）
                LocalDateTime payTime = LocalDateTime.now();
                MallPaymentLog payLog = new MallPaymentLog();
                payLog.setUserId(userId);
                payLog.setOrderId(orderId);
                payLog.setOrderNo(orderInfo.getOrderNo());
                payLog.setAmount(orderInfo.getPayAmount());
                payLog.setDirection("pay");
                payLog.setStatus(0);
                payLog.setRemark("模拟支付成功");
                payLog.setCreateTime(payTime);
                mallPaymentLogMapper.insert(payLog);

                // 3. Feign → degel-order 更新：status=1, pay_time=NOW(), pay_log_id={新ID}
                OrderStatusUpdateVO updateVO = new OrderStatusUpdateVO();
                updateVO.setStatus(1);
                updateVO.setPayTime(payTime);
                updateVO.setPayLogId(payLog.getId());
                R<Void> updateResp = orderFeignClient.updateOrderStatus(orderId, updateVO);
                if (updateResp == null || updateResp.getCode() != 200) {
                    // 支付流水已写，记录日志，由补偿任务处理
                    log.error("[PayServiceImpl] 支付流水写入成功但更新订单状态失败 orderId={} payLogId={}", orderId, payLog.getId());
                }

                // 4. 返回 PayResultVO
                PayResultVO result = new PayResultVO();
                result.setPayLogId(payLog.getId());
                result.setOrderNo(orderInfo.getOrderNo());
                result.setAmount(orderInfo.getPayAmount());
                result.setPayTime(payTime);
                return result;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PayServiceImpl] pay 异常 orderId={}", orderId, e);
            throw BusinessException.of(50001, "支付失败：" + e.getMessage());
        }
    }

    // =========================================================
    // C-08: GET /app/pay/log — 我的支付流水列表（分页）
    // =========================================================

    @Override
    public IPage<PayLogVO> listPayLog(Long userId, String direction, Integer page, Integer size) {
        LambdaQueryWrapper<MallPaymentLog> wrapper = new LambdaQueryWrapper<MallPaymentLog>()
                .eq(MallPaymentLog::getUserId, userId)
                .eq(StringUtils.hasText(direction), MallPaymentLog::getDirection, direction)
                .orderByDesc(MallPaymentLog::getCreateTime);

        Page<MallPaymentLog> pageParam = new Page<>(page, size);
        IPage<MallPaymentLog> rawPage = mallPaymentLogMapper.selectPage(pageParam, wrapper);

        // 转换
        IPage<PayLogVO> resultPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        List<PayLogVO> records = rawPage.getRecords().stream().map(this::convertToPayLogVO)
                .collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    private PayLogVO convertToPayLogVO(MallPaymentLog log) {
        PayLogVO vo = new PayLogVO();
        vo.setId(log.getId());
        vo.setOrderId(log.getOrderId());
        vo.setOrderNo(log.getOrderNo());
        vo.setAmount(log.getAmount());
        vo.setDirection(log.getDirection());
        vo.setStatus(log.getStatus());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    // =========================================================
    // C-09: POST /app/inner/pay/refund — 内部退款接口
    // =========================================================

    @Override
    public Long refund(InnerRefundReqVO reqVO) {
        MallPaymentLog payLog = new MallPaymentLog();
        payLog.setUserId(reqVO.getUserId());
        payLog.setOrderId(reqVO.getOrderId());
        payLog.setOrderNo(reqVO.getOrderNo());
        payLog.setAmount(reqVO.getAmount());
        payLog.setDirection("refund");
        payLog.setStatus(0);
        payLog.setRemark("售后退款");
        payLog.setCreateTime(LocalDateTime.now());
        mallPaymentLogMapper.insert(payLog);
        return payLog.getId();
    }
}
