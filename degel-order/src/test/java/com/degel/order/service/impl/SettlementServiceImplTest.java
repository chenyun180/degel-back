package com.degel.order.service.impl;

import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.SettlementAccount;
import com.degel.order.entity.SettlementAccountLog;
import com.degel.order.entity.SettlementConfig;
import com.degel.order.entity.SettlementOrder;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.mapper.SettlementAccountLogMapper;
import com.degel.order.mapper.SettlementAccountMapper;
import com.degel.order.mapper.SettlementConfigMapper;
import com.degel.order.mapper.SettlementOrderMapper;
import com.degel.order.vo.SettleCandidateVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SettlementServiceImpl 单元测试——资金链路核心正确性锁：
 *
 * 1. 结算任务：佣金公式（gross*rate/100 HALF_UP）/配置缺失回退默认/明细幂等（uk 冲突跳过）
 * 2. 入账 CAS：被退款钩子抢先作废则不入账；入账中途失败回退待入账（下轮重试）
 * 3. 退款扣回两分支：结算前作废（无款可扣）/结算后扣回（允许负余额）
 * 4. 并发互斥：扣回 CAS 失败（别处先扣）不再扣款
 * 5. 兜底对账：遗漏售后单被补做
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("结算服务单元测试（资金链路）")
class SettlementServiceImplTest {

    private static final Long SHOP_ID = 4L;
    private static final Long ORDER_ID = 100L;
    private static final Long AFTERSALE_ID = 14L;

    @Mock
    private SettlementOrderMapper baseMapper;
    @Mock
    private SettlementAccountMapper accountMapper;
    @Mock
    private SettlementAccountLogMapper accountLogMapper;
    @Mock
    private SettlementConfigMapper configMapper;
    @Mock
    private OrderAfterSaleMapper orderAfterSaleMapper;

    @InjectMocks
    private SettlementServiceImpl service;

    @BeforeEach
    void injectBaseMapper() {
        // ServiceImpl.baseMapper 是父类 @Autowired 字段，Mockito 构造注入不覆盖，显式注入
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    // ================================================================ 结算任务

    @Test
    @DisplayName("结算：佣金按 gross*rate/100 HALF_UP 计算（实付+平台补贴+积分抵扣为基数）")
    void settle_commission_calculated() {
        stubRate("8");
        SettleCandidateVo c = candidate(new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("5"));
        when(baseMapper.selectSettleCandidates(anyInt(), anyInt())).thenReturn(Collections.singletonList(c));
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList()); // 无待入账

        service.settleDueOrders();

        ArgumentCaptor<SettlementOrder> captor = ArgumentCaptor.forClass(SettlementOrder.class);
        verify(baseMapper).insert(captor.capture());
        SettlementOrder detail = captor.getValue();
        // gross = 100 + 10 + 5 = 115；commission = 115 * 8% = 9.20；net = 105.80
        assertThat(detail.getGrossAmount()).isEqualByComparingTo("115");
        assertThat(detail.getCommissionRate()).isEqualByComparingTo("8");
        assertThat(detail.getCommissionAmount()).isEqualByComparingTo("9.20");
        assertThat(detail.getNetAmount()).isEqualByComparingTo("105.80");
        assertThat(detail.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("结算：佣金配置缺失回退默认 5%")
    void settle_defaultRate_whenConfigMissing() {
        when(configMapper.selectOne(any())).thenReturn(null);
        when(baseMapper.selectSettleCandidates(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.settleDueOrders();
        assertThat(service.readCommissionRate()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("结算：明细 uk_order_id 冲突（多实例/重跑）幂等跳过不抛")
    void settle_duplicateKey_idempotentSkip() {
        stubRate("5");
        when(baseMapper.selectSettleCandidates(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(candidate(BigDecimal.TEN, null, null)));
        when(baseMapper.insert(any(SettlementOrder.class))).thenThrow(new DuplicateKeyException("uk_order_id"));
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatCode(() -> service.settleDueOrders()).doesNotThrowAnyException();
    }

    // ================================================================ 入账 CAS

    @Test
    @DisplayName("入账：CAS 成功 → 余额入账 + 流水(bizType=1) 落库")
    void settle_credit_and_log() {
        SettlementOrder detail = pendingDetail(new BigDecimal("95"));
        when(baseMapper.markSettled(detail.getId())).thenReturn(1);
        when(accountMapper.credit(eq(SHOP_ID), any(BigDecimal.class))).thenReturn(1);
        SettlementAccount acc = new SettlementAccount();
        acc.setBalance(new BigDecimal("195.00"));
        when(accountMapper.selectByShopId(SHOP_ID)).thenReturn(acc);

        assertThat(service.trySettleOne(detail)).isTrue();

        verify(accountMapper).credit(SHOP_ID, new BigDecimal("95"));
        ArgumentCaptor<SettlementAccountLog> captor = ArgumentCaptor.forClass(SettlementAccountLog.class);
        verify(accountLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizType()).isEqualTo(1);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("入账：CAS 失败（退款钩子抢先作废）→ 不入账不写流水")
    void settle_voided_noCredit() {
        SettlementOrder detail = pendingDetail(new BigDecimal("95"));
        when(baseMapper.markSettled(detail.getId())).thenReturn(0);

        assertThat(service.trySettleOne(detail)).isFalse();

        verify(accountMapper, never()).credit(anyLong(), any(BigDecimal.class));
        verify(accountLogMapper, never()).insert(any(SettlementAccountLog.class));
    }

    @Test
    @DisplayName("入账：余额落库中途失败 → 回退 CAS 待下轮重试，且无流水")
    void settle_creditFailed_revert() {
        SettlementOrder detail = pendingDetail(new BigDecimal("95"));
        when(baseMapper.markSettled(detail.getId())).thenReturn(1);
        when(accountMapper.credit(anyLong(), any(BigDecimal.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(service.trySettleOne(detail)).isFalse();

        verify(baseMapper).revertSettled(detail.getId());
        verify(accountLogMapper, never()).insert(any(SettlementAccountLog.class));
    }

    // ================================================================ 退款扣回

    @Test
    @DisplayName("退款扣回：结算前退款 → 抢占作废待入账明细，不查明细不扣款")
    void refund_pending_voided() {
        when(baseMapper.markVoidIfPending(ORDER_ID)).thenReturn(1);

        service.onRefundCompleted(afterSale());

        verify(baseMapper, never()).selectOne(any());
        verify(accountMapper, never()).deductAllowNegative(anyLong(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("退款扣回：结算后退款 → 扣回净额（允许负余额）+ 流水(bizType=3) 金额为负")
    void refund_settled_deductNegative() {
        when(baseMapper.markVoidIfPending(ORDER_ID)).thenReturn(0);
        SettlementOrder detail = settledDetail(new BigDecimal("100"));
        lenient().when(baseMapper.selectOne(any())).thenReturn(detail);
        when(baseMapper.markDeducted(ORDER_ID)).thenReturn(1);
        when(accountMapper.deductAllowNegative(eq(SHOP_ID), any(BigDecimal.class))).thenReturn(1);
        SettlementAccount acc = new SettlementAccount();
        acc.setBalance(new BigDecimal("-100"));
        when(accountMapper.selectByShopId(SHOP_ID)).thenReturn(acc);

        service.onRefundCompleted(afterSale());

        verify(accountMapper).deductAllowNegative(SHOP_ID, new BigDecimal("100"));
        ArgumentCaptor<SettlementAccountLog> captor = ArgumentCaptor.forClass(SettlementAccountLog.class);
        verify(accountLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizType()).isEqualTo(3);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-100");
    }

    @Test
    @DisplayName("退款扣回：扣回 CAS 失败（并发先扣）→ 不重复扣款")
    void refund_raceLost_noDeduct() {
        when(baseMapper.markVoidIfPending(ORDER_ID)).thenReturn(0);
        SettlementOrder detail = settledDetail(new BigDecimal("100"));
        lenient().when(baseMapper.selectOne(any())).thenReturn(detail);
        when(baseMapper.markDeducted(ORDER_ID)).thenReturn(0);

        service.onRefundCompleted(afterSale());

        verify(accountMapper, never()).deductAllowNegative(anyLong(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("退款扣回：无结算明细（未到 T+7 即退款）→ 无款可扣，安全返回")
    void refund_noDetail_noop() {
        when(baseMapper.markVoidIfPending(ORDER_ID)).thenReturn(0);
        when(baseMapper.selectOne(any())).thenReturn(null);

        service.onRefundCompleted(afterSale());

        verify(accountMapper, never()).deductAllowNegative(anyLong(), any(BigDecimal.class));
    }

    // ================================================================ 兜底对账

    @Test
    @DisplayName("兜底对账：钩子遗漏的售后单被补做扣回")
    void reconcile_backlog_reprocessed() {
        when(baseMapper.selectRefundDeductBacklog(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(AFTERSALE_ID));
        when(orderAfterSaleMapper.selectById(AFTERSALE_ID)).thenReturn(afterSale());
        // 走"结算前作废"分支即证明 onRefundCompleted 被补做
        when(baseMapper.markVoidIfPending(ORDER_ID)).thenReturn(1);

        service.reconcileRefundDeduct();

        verify(orderAfterSaleMapper).selectById(AFTERSALE_ID);
        verify(baseMapper).markVoidIfPending(ORDER_ID);
    }

    // ================================================================ fixtures

    private void stubRate(String rate) {
        SettlementConfig config = new SettlementConfig();
        config.setConfigKey("commission_rate");
        config.setConfigValue(rate);
        when(configMapper.selectOne(any())).thenReturn(config);
    }

    private SettleCandidateVo candidate(BigDecimal pay, BigDecimal subsidy, BigDecimal pointsDeduct) {
        SettleCandidateVo c = new SettleCandidateVo();
        c.setOrderId(ORDER_ID);
        c.setOrderNo("NO100");
        c.setShopId(SHOP_ID);
        c.setUserId(1L);
        c.setPayAmount(pay);
        c.setPlatformSubsidy(subsidy);
        c.setPointsDeduct(pointsDeduct);
        return c;
    }

    private SettlementOrder pendingDetail(BigDecimal net) {
        SettlementOrder d = new SettlementOrder();
        d.setId(55L);
        d.setOrderId(ORDER_ID);
        d.setOrderNo("NO100");
        d.setShopId(SHOP_ID);
        d.setNetAmount(net);
        d.setStatus(0);
        return d;
    }

    private SettlementOrder settledDetail(BigDecimal net) {
        SettlementOrder d = pendingDetail(net);
        d.setStatus(1);
        return d;
    }

    private OrderAfterSale afterSale() {
        OrderAfterSale a = new OrderAfterSale();
        a.setId(AFTERSALE_ID);
        a.setOrderId(ORDER_ID);
        a.setShopId(SHOP_ID);
        a.setUserId(1L);
        a.setRefundAmount(new BigDecimal("115"));
        return a;
    }
}
