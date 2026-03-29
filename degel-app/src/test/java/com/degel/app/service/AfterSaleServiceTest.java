package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.entity.MallPaymentLog;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.mapper.MallPaymentLogMapper;
import com.degel.app.service.impl.AfterSaleServiceImpl;
import com.degel.app.vo.AfterSaleDetailVO;
import com.degel.app.vo.AfterSaleInfoVO;
import com.degel.app.vo.OrderInfoVO;
import com.degel.common.core.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AfterSaleServiceImpl 单元测试
 *
 * <p>覆盖用例：
 * 1. applyAfterSale_orderNotCompleted_shouldThrow  — 订单非已完成时申请售后抛40020
 * 2. applyAfterSale_duplicate_shouldThrow          — 已有进行中申请时重复提交抛40021
 * 3. getDetail_agreed_shouldIncludeRefundLog       — 状态已同意时详情包含退款流水
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AfterSaleServiceImpl 单元测试")
class AfterSaleServiceTest {

    private static final Long USER_ID      = 1001L;
    private static final Long ORDER_ID     = 9001L;
    private static final Long AFTERSALE_ID = 5001L;

    @Mock
    private OrderFeignClient orderFeignClient;

    @Mock
    private MallPaymentLogMapper mallPaymentLogMapper;

    @InjectMocks
    private AfterSaleServiceImpl afterSaleService;

    // ======================================================================
    // 用例 1：applyAfterSale — 订单非已完成（status≠3），抛 40020
    // ======================================================================

    @Test
    @DisplayName("applyAfterSale_orderNotCompleted_shouldThrow — 订单非已完成状态时申请售后抛40020")
    void applyAfterSale_orderNotCompleted_shouldThrow() {
        // given: 订单状态=2（待收货），不满足申请售后条件
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, USER_ID, 2);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        com.degel.app.vo.dto.AfterSaleCreateReqVO reqVO = buildAfterSaleReq(ORDER_ID, "质量问题");

        // when & then
        assertThatThrownBy(() -> afterSaleService.applyAfterSale(reqVO, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40020);
                    assertThat(be.getMessage()).contains("已完成");
                });

        // 不应查询售后单列表，也不应创建
        verify(orderFeignClient, never()).pageAfterSales(any(), any(), any(), any());
        verify(orderFeignClient, never()).createAfterSale(any());
    }

    // ======================================================================
    // 用例 2：applyAfterSale — 同一订单已有进行中申请（status=0），重复提交抛 40021
    // ======================================================================

    @Test
    @DisplayName("applyAfterSale_duplicate_shouldThrow — 已有待审核售后申请时重复提交抛40021")
    void applyAfterSale_duplicate_shouldThrow() {
        // given: 订单已完成（status=3）
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, USER_ID, 3);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // 已存在一条 status=0（待审核）的售后单
        AfterSaleInfoVO existingAfterSale = buildAfterSaleInfoVO(AFTERSALE_ID, ORDER_ID, USER_ID, 0);
        Page<AfterSaleInfoVO> page = new Page<>(1, 100, 1);
        page.setRecords(Collections.singletonList(existingAfterSale));
        when(orderFeignClient.pageAfterSales(eq(USER_ID), isNull(), eq(1), eq(100)))
                .thenReturn(R.ok(page));

        com.degel.app.vo.dto.AfterSaleCreateReqVO reqVO = buildAfterSaleReq(ORDER_ID, "质量问题");

        // when & then
        assertThatThrownBy(() -> afterSaleService.applyAfterSale(reqVO, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40021);
                    assertThat(be.getMessage()).contains("进行中");
                });

        // 不应创建新售后单
        verify(orderFeignClient, never()).createAfterSale(any());
    }

    // ======================================================================
    // 用例 3：getAfterSaleDetail — status=1（已同意）时详情中包含退款流水
    // ======================================================================

    @Test
    @DisplayName("getDetail_agreed_shouldIncludeRefundLog — 售后已同意时详情包含退款流水信息")
    void getDetail_agreed_shouldIncludeRefundLog() {
        // given: 存在一条已同意（status=1）的售后单
        AfterSaleInfoVO agreedAfterSale = buildAfterSaleInfoVO(AFTERSALE_ID, ORDER_ID, USER_ID, 1);
        agreedAfterSale.setOrderNo("ORDER20260329001");
        agreedAfterSale.setRefundAmount(new BigDecimal("199.00"));

        // pageAfterSales 返回包含该售后单的列表
        Page<AfterSaleInfoVO> page = new Page<>(1, 1000, 1);
        page.setRecords(Collections.singletonList(agreedAfterSale));
        when(orderFeignClient.pageAfterSales(eq(USER_ID), isNull(), eq(1), eq(1000)))
                .thenReturn(R.ok(page));

        // 对应的退款流水
        MallPaymentLog refundLog = buildRefundLog(77777L, ORDER_ID, USER_ID,
                "ORDER20260329001", new BigDecimal("199.00"));
        when(mallPaymentLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(refundLog);

        // when
        AfterSaleDetailVO detail = afterSaleService.getAfterSaleDetail(AFTERSALE_ID, USER_ID);

        // then: 基础字段
        assertThat(detail.getId()).isEqualTo(AFTERSALE_ID);
        assertThat(detail.getStatus()).isEqualTo(1);
        assertThat(detail.getStatusDesc()).isEqualTo("已同意");

        // 退款流水必须存在
        assertThat(detail.getPayLog()).isNotNull();
        assertThat(detail.getPayLog().getId()).isEqualTo(77777L);
        assertThat(detail.getPayLog().getDirection()).isEqualTo("refund");
        assertThat(detail.getPayLog().getAmount()).isEqualByComparingTo("199.00");
    }

    // ======================================================================
    // 私有构建辅助方法
    // ======================================================================

    private com.degel.app.vo.dto.AfterSaleCreateReqVO buildAfterSaleReq(Long orderId, String reason) {
        com.degel.app.vo.dto.AfterSaleCreateReqVO req = new com.degel.app.vo.dto.AfterSaleCreateReqVO();
        req.setOrderId(orderId);
        req.setReason(reason);
        return req;
    }

    private OrderInfoVO buildOrderInfoVO(Long orderId, Long userId, Integer status) {
        OrderInfoVO vo = new OrderInfoVO();
        vo.setId(orderId);
        vo.setOrderNo("ORDER" + orderId);
        vo.setUserId(userId);
        vo.setShopId(1L);
        vo.setStatus(status);
        vo.setPayAmount(new BigDecimal("199.00"));
        return vo;
    }

    private AfterSaleInfoVO buildAfterSaleInfoVO(Long id, Long orderId, Long userId, Integer status) {
        AfterSaleInfoVO vo = new AfterSaleInfoVO();
        vo.setId(id);
        vo.setOrderId(orderId);
        vo.setUserId(userId);
        vo.setStatus(status);
        vo.setType(1);
        vo.setReason("质量问题");
        vo.setRefundAmount(new BigDecimal("199.00"));
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    private MallPaymentLog buildRefundLog(Long id, Long orderId, Long userId,
                                          String orderNo, BigDecimal amount) {
        MallPaymentLog log = new MallPaymentLog();
        log.setId(id);
        log.setOrderId(orderId);
        log.setUserId(userId);
        log.setOrderNo(orderNo);
        log.setAmount(amount);
        log.setDirection("refund");
        log.setStatus(0);
        log.setRemark("售后退款");
        log.setCreateTime(LocalDateTime.now());
        return log;
    }
}
