package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.OrderInfo;
import com.degel.order.feign.MarketingFeignClient;
import com.degel.order.feign.PayFeignClient;
import com.degel.order.feign.PointsFeignClient;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.mapper.OrderInfoMapper;
import com.degel.order.service.ISettlementService;
import com.degel.order.service.NotificationService;
import com.degel.order.vo.AfterSaleArbitrateVo;
import com.degel.order.vo.AfterSaleHandleVo;
import com.degel.order.vo.inner.AfterSaleCreateInnerVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderAfterSaleServiceImpl 单元测试——退款链路 + 2026-09-18 新增规则锁：
 *
 * 1. 售后窗口：status=2 以发货时间起算（在途仅退款）；超窗/非法状态拒绝
 * 2. 退款完成链路（agree type=1 / 仲裁支持用户）：退券+退款流水+积分+结算联动+**已发货订单封口**
 * 3. 封口 CAS 与并发收货互斥：update 0 行不抛
 * 4. 拒绝分支：不触发任何资金动作
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("售后服务单元测试（退款链路+在途退款）")
class OrderAfterSaleServiceImplTest {

    private static final Long ORDER_ID = 152L;
    private static final Long AFTERSALE_ID = 14L;
    private static final Long SHOP_ID = 4L;
    private static final Long USER_ID = 1L;

    @Mock
    private OrderAfterSaleMapper baseMapper;
    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private MarketingFeignClient marketingFeignClient;
    @Mock
    private PayFeignClient payFeignClient;
    @Mock
    private PointsFeignClient pointsFeignClient;
    @Mock
    private ISettlementService settlementService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderAfterSaleServiceImpl service;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // LambdaUpdateWrapper.set(SFunction) 立即解析列名，需要实体 TableInfo 缓存（单测无 MyBatis 启动流程）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrderAfterSale.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
    }

    // ================================================================ 售后窗口（status=2 在途仅退款）

    @Test
    @DisplayName("窗口：status=2 发货 1 天内 → 允许申请，售后单落 status=0")
    void create_shippedInWindow_ok() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(2, LocalDateTime.now().minusDays(1), null));
        when(settlementService.readAftersaleDays()).thenReturn(7);
        // mock insert 不回填雪花 id，手动回填（save 后 getId 断言用）
        when(baseMapper.insert(any(OrderAfterSale.class))).thenAnswer(inv -> {
            inv.getArgument(0, OrderAfterSale.class).setId(AFTERSALE_ID);
            return 1;
        });

        Long id = service.createInnerAfterSale(createVo());

        assertThat(id).isEqualTo(AFTERSALE_ID);
        ArgumentCaptor<OrderAfterSale> captor = ArgumentCaptor.forClass(OrderAfterSale.class);
        verify(baseMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
        assertThat(captor.getValue().getType()).isEqualTo(1);
    }

    @Test
    @DisplayName("窗口：status=2 发货超 7 天 → 拒绝")
    void create_shippedTooOld_reject() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(2, LocalDateTime.now().minusDays(8), null));
        when(settlementService.readAftersaleDays()).thenReturn(7);

        assertThatThrownBy(() -> service.createInnerAfterSale(createVo()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发货超过7天");
    }

    @Test
    @DisplayName("窗口：status=1（未发货）→ 拒绝（应走取消秒退而非售后）")
    void create_status1_reject() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(1, null, null));

        assertThatThrownBy(() -> service.createInnerAfterSale(createVo()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已发货或已完成");
    }

    @Test
    @DisplayName("窗口：status=3 收货超 7 天 → 拒绝（原七天无理由规则不回归）")
    void create_receivedTooOld_reject() {
        when(orderInfoMapper.selectById(ORDER_ID))
                .thenReturn(order(3, null, LocalDateTime.now().minusDays(8)));
        when(settlementService.readAftersaleDays()).thenReturn(7);

        assertThatThrownBy(() -> service.createInnerAfterSale(createVo()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("售后窗口");
    }

    // ================================================================ 退款完成链路

    @Test
    @DisplayName("handle agree(type=1 仅退款)：售后→3 + 退券/退款流水/积分/结算联动 + 已发货订单封口")
    void handle_agreeRefund_fullChain() {
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(refundableAfterSale());
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(orderInfoMapper.update(any(), any())).thenReturn(1); // 封口 CAS 命中
        lenient().when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(2, LocalDateTime.now().minusDays(1), null));

        AfterSaleHandleVo vo = new AfterSaleHandleVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setAction("agree");
        vo.setMerchantRemark("同意");
        service.handle(vo, SHOP_ID);

        // 资金四联动 + 订单封口
        verify(marketingFeignClient).returnCoupon(anyMap());
        verify(payFeignClient).refund(any());
        verify(pointsFeignClient).returnRedeem(eq(USER_ID), any());
        verify(settlementService).onRefundCompleted(any(OrderAfterSale.class));
        verify(orderInfoMapper).update(any(), any());
        verify(notificationService).send(eq(USER_ID), any(), any(), any());
    }

    @Test
    @DisplayName("handle reject：售后→5，不触发任何资金动作")
    void handle_reject_noRefundChain() {
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(refundableAfterSale());
        when(baseMapper.update(any(), any())).thenReturn(1);

        AfterSaleHandleVo vo = new AfterSaleHandleVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setAction("reject");
        service.handle(vo, SHOP_ID);

        verifyNoInteractions(marketingFeignClient, payFeignClient, pointsFeignClient, settlementService);
        verify(orderInfoMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("handle 非待审核状态 → 拒绝操作（防重复处理）")
    void handle_wrongStatus_reject() {
        OrderAfterSale done = refundableAfterSale();
        done.setStatus(3);
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(done);

        AfterSaleHandleVo vo = new AfterSaleHandleVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setAction("agree");

        assertThatThrownBy(() -> service.handle(vo, SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许操作");
    }

    // ================================================================ 平台仲裁

    @Test
    @DisplayName("仲裁支持用户：6→3 CAS 成功 → 完整退款链路 + 订单封口")
    void arbitrate_supportUser_refundChain() {
        OrderAfterSale arbitrating = refundableAfterSale();
        arbitrating.setStatus(6);
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(arbitrating);
        when(baseMapper.update(any(), any())).thenReturn(1); // CAS 6→3 命中
        when(orderInfoMapper.update(any(), any())).thenReturn(1);
        lenient().when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(2, LocalDateTime.now().minusDays(1), null));

        AfterSaleArbitrateVo vo = new AfterSaleArbitrateVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setSupportUser(true);
        vo.setRemark("支持用户");
        service.arbitrate(vo, "platform");

        verify(marketingFeignClient).returnCoupon(anyMap());
        verify(payFeignClient).refund(any());
        verify(settlementService).onRefundCompleted(any(OrderAfterSale.class));
        verify(orderInfoMapper).update(any(), any());
    }

    @Test
    @DisplayName("仲裁维持拒绝：6→7 终态，不触发资金动作")
    void arbitrate_maintainReject() {
        OrderAfterSale arbitrating = refundableAfterSale();
        arbitrating.setStatus(6);
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(arbitrating);
        when(baseMapper.update(any(), any())).thenReturn(1);

        AfterSaleArbitrateVo vo = new AfterSaleArbitrateVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setSupportUser(false);
        vo.setRemark("维持");
        service.arbitrate(vo, "platform");

        verifyNoInteractions(marketingFeignClient, payFeignClient, settlementService);
        verify(orderInfoMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("仲裁 CAS 失败（已被处理）→ 抛异常不重复退款")
    void arbitrate_alreadyHandled_reject() {
        OrderAfterSale arbitrating = refundableAfterSale();
        arbitrating.setStatus(6);
        when(baseMapper.selectById(AFTERSALE_ID)).thenReturn(arbitrating);
        when(baseMapper.update(any(), any())).thenReturn(0); // CAS 失败

        AfterSaleArbitrateVo vo = new AfterSaleArbitrateVo();
        vo.setAfterSaleId(AFTERSALE_ID);
        vo.setSupportUser(true);
        vo.setRemark("支持用户");

        assertThatThrownBy(() -> service.arbitrate(vo, "platform"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被处理");
        verify(payFeignClient, never()).refund(any());
    }

    // ================================================================ fixtures

    private AfterSaleCreateInnerVo createVo() {
        AfterSaleCreateInnerVo vo = new AfterSaleCreateInnerVo();
        vo.setOrderId(ORDER_ID);
        vo.setUserId(USER_ID);
        vo.setShopId(SHOP_ID);
        vo.setType(1);
        vo.setReason("测试");
        return vo;
    }

    private OrderInfo order(int status, LocalDateTime shipTime, LocalDateTime receiveTime) {
        OrderInfo o = new OrderInfo();
        o.setId(ORDER_ID);
        o.setOrderNo("NO152");
        o.setStatus(status);
        o.setShipTime(shipTime);
        o.setReceiveTime(receiveTime);
        return o;
    }

    private OrderAfterSale refundableAfterSale() {
        OrderAfterSale a = new OrderAfterSale();
        a.setId(AFTERSALE_ID);
        a.setOrderId(ORDER_ID);
        a.setShopId(SHOP_ID);
        a.setUserId(USER_ID);
        a.setType(1);
        a.setStatus(0);
        a.setRefundAmount(java.math.BigDecimal.TEN);
        return a;
    }
}
