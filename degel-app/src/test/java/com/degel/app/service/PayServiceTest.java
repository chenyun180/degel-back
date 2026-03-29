package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.app.entity.MallPaymentLog;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.mapper.MallPaymentLogMapper;
import com.degel.app.service.impl.PayServiceImpl;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.PayResultVO;
import com.degel.app.vo.dto.InnerRefundReqVO;
import com.degel.common.core.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PayServiceImpl 单元测试
 *
 * <p>覆盖用例：
 * 1. pay_duplicatePay_shouldThrowException   — 已存在支付流水时重复支付被拒绝
 * 2. pay_orderNotPending_shouldThrow         — 订单非待付款状态时支付失败
 * 3. refund_success_shouldReturnPayLogId     — 退款成功后返回新流水ID
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayServiceImpl 单元测试")
class PayServiceTest {

    private static final Long USER_ID  = 1001L;
    private static final Long ORDER_ID = 9001L;

    @Mock
    private MallPaymentLogMapper mallPaymentLogMapper;

    @Mock
    private OrderFeignClient orderFeignClient;

    @InjectMocks
    private PayServiceImpl payService;

    // ======================================================================
    // 用例 1：pay — 同一订单已有支付流水，重复支付被拒绝（40018）
    // ======================================================================

    @Test
    @DisplayName("pay_duplicatePay_shouldThrowException — 同订单已存在pay流水时抛40018拒绝重复支付")
    void pay_duplicatePay_shouldThrowException() {
        // given: 订单属于当前用户且处于待付款
        OrderInfoVO orderInfo = buildPendingOrder(ORDER_ID, USER_ID);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // 已存在一条 direction=pay, status=0 的流水（重复支付场景）
        when(mallPaymentLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> payService.pay(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40018);
                    assertThat(be.getMessage()).contains("已支付");
                });

        // 不应插入新流水
        verify(mallPaymentLogMapper, never()).insert(any());
    }

    // ======================================================================
    // 用例 2：pay — 订单状态非待付款（status≠0），支付失败（40018）
    // ======================================================================

    @Test
    @DisplayName("pay_orderNotPending_shouldThrow — 订单状态非0时支付抛40018")
    void pay_orderNotPending_shouldThrow() {
        // given: 订单状态=1（待发货），不是待付款
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, USER_ID, 1, new BigDecimal("199.00"));
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // when & then
        assertThatThrownBy(() -> payService.pay(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40018);
                    assertThat(be.getMessage()).contains("待付款");
                });

        // 不查幂等，不插入流水
        verify(mallPaymentLogMapper, never()).selectCount(any());
        verify(mallPaymentLogMapper, never()).insert(any());
    }

    // ======================================================================
    // 用例 3：refund — 退款成功后返回新生成的流水 ID
    // ======================================================================

    @Test
    @DisplayName("refund_success_shouldReturnPayLogId — 退款写入flow表后返回新流水ID")
    void refund_success_shouldReturnPayLogId() {
        // given: 构造退款请求 VO
        InnerRefundReqVO reqVO = new InnerRefundReqVO();
        reqVO.setUserId(USER_ID);
        reqVO.setOrderId(ORDER_ID);
        reqVO.setOrderNo("ORDER20260329001");
        reqVO.setAmount(new BigDecimal("99.00"));

        // 模拟 insert 时为 MallPaymentLog 设置自增 ID（通过 doAnswer 模拟 MyBatis 回填主键行为）
        doAnswer(invocation -> {
            MallPaymentLog log = invocation.getArgument(0);
            log.setId(88888L);
            return 1;
        }).when(mallPaymentLogMapper).insert(any(MallPaymentLog.class));

        // when
        Long payLogId = payService.refund(reqVO);

        // then: 返回 insert 后回填的 ID
        assertThat(payLogId).isEqualTo(88888L);

        // 验证插入的流水属性正确
        ArgumentCaptor<MallPaymentLog> captor = ArgumentCaptor.forClass(MallPaymentLog.class);
        verify(mallPaymentLogMapper, times(1)).insert(captor.capture());
        MallPaymentLog inserted = captor.getValue();
        assertThat(inserted.getDirection()).isEqualTo("refund");
        assertThat(inserted.getStatus()).isEqualTo(0);
        assertThat(inserted.getUserId()).isEqualTo(USER_ID);
        assertThat(inserted.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(inserted.getAmount()).isEqualByComparingTo("99.00");
    }

    // ======================================================================
    // 私有构建辅助方法
    // ======================================================================

    /** 构造待付款（status=0）订单 */
    private OrderInfoVO buildPendingOrder(Long orderId, Long userId) {
        return buildOrderInfoVO(orderId, userId, 0, new BigDecimal("199.00"));
    }

    private OrderInfoVO buildOrderInfoVO(Long orderId, Long userId, Integer status, BigDecimal payAmount) {
        OrderInfoVO vo = new OrderInfoVO();
        vo.setId(orderId);
        vo.setOrderNo("ORDER" + orderId);
        vo.setUserId(userId);
        vo.setStatus(status);
        vo.setPayAmount(payAmount);
        return vo;
    }
}
