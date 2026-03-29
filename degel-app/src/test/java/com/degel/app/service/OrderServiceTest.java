package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.app.entity.MallAddress;
import com.degel.app.entity.MallCart;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.feign.StockFeignClient;
import com.degel.app.mapper.MallAddressMapper;
import com.degel.app.mapper.MallCartMapper;
import com.degel.app.service.impl.OrderServiceImpl;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.dto.OrderCreateReqVO;
import com.degel.app.vo.dto.StockDeductVO;
import com.degel.common.core.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 单元测试
 *
 * <p>覆盖用例：
 * 1. createOrder_insufficientStock_shouldThrow40012   — 库存不足抛40012
 * 2. getOrderDetail_otherUser_shouldThrow40016        — 越权查看抛40016
 * 3. cancelOrder_notPending_shouldThrow40017          — 非待付款取消抛40017
 * 4. confirmReceive_notShipped_shouldThrow40019       — 非待收货确认抛40019
 * 5. createOrder_success_shouldDeleteCart             — 购物车模式成功后删购物车
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl 单元测试")
class OrderServiceTest {

    private static final Long USER_ID  = 1001L;
    private static final Long ORDER_ID = 9001L;
    private static final Long SKU_ID   = 2001L;
    private static final Long ADDRESS_ID = 3001L;

    @Mock
    private OrderFeignClient orderFeignClient;

    @Mock
    private ProductFeignClient productFeignClient;

    @Mock
    private StockFeignClient stockFeignClient;

    @Mock
    private MallCartMapper mallCartMapper;

    @Mock
    private MallAddressMapper mallAddressMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ======================================================================
    // 用例 1：createOrder — 库存不足抛 40012
    // ======================================================================

    @Test
    @DisplayName("createOrder_insufficientStock_shouldThrow40012 — SKU库存为0时下单抛40012")
    void createOrder_insufficientStock_shouldThrow40012() throws InterruptedException {
        // given: 直购模式请求
        OrderCreateReqVO req = buildDirectBuyReq(SKU_ID, 5, ADDRESS_ID);

        // SKU 在售但库存只有 2，请求购买 5 个
        ProductSkuVO sku = buildSku(SKU_ID, new BigDecimal("99.00"), 2);
        R<List<ProductSkuVO>> skuResp = R.ok(Collections.singletonList(sku));
        when(productFeignClient.batchGetSku(Collections.singletonList(SKU_ID))).thenReturn(skuResp);

        // 模拟 Redisson 锁可以加锁成功（走到库存校验分支后才抛异常）
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(req, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40012);
                    assertThat(be.getMessage()).contains("库存");
                });
    }

    // ======================================================================
    // 用例 2：getOrderDetail — 其他用户的订单越权查看抛 40016
    // ======================================================================

    @Test
    @DisplayName("getOrderDetail_otherUser_shouldThrow40016 — 订单归属不同用户时抛40016")
    void getOrderDetail_otherUser_shouldThrow40016() {
        // given: 订单属于 userId=9999，当前登录用户是 1001
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, 9999L, 0);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // when & then
        assertThatThrownBy(() -> orderService.getOrderDetail(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40016);
                    assertThat(be.getMessage()).contains("无权");
                });
    }

    // ======================================================================
    // 用例 3：cancelOrder — 非待付款（status≠0）取消抛 40017
    // ======================================================================

    @Test
    @DisplayName("cancelOrder_notPending_shouldThrow40017 — 订单状态非待付款时取消抛40017")
    void cancelOrder_notPending_shouldThrow40017() {
        // given: 订单属于当前用户，但状态=1（待发货），不允许取消
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, USER_ID, 1);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40017);
                    assertThat(be.getMessage()).contains("待付款");
                });
    }

    // ======================================================================
    // 用例 4：confirmReceive — 非待收货（status≠2）确认收货抛 40019
    // ======================================================================

    @Test
    @DisplayName("confirmReceive_notShipped_shouldThrow40019 — 订单状态非待收货时确认收货抛40019")
    void confirmReceive_notShipped_shouldThrow40019() {
        // given: 订单属于当前用户，但状态=1（待发货），不允许确认收货
        OrderInfoVO orderInfo = buildOrderInfoVO(ORDER_ID, USER_ID, 1);
        when(orderFeignClient.getOrder(ORDER_ID)).thenReturn(R.ok(orderInfo));

        // when & then
        assertThatThrownBy(() -> orderService.confirmReceive(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40019);
                    assertThat(be.getMessage()).contains("待收货");
                });
    }

    // ======================================================================
    // 用例 5：createOrder — 购物车模式下单成功后删除购物车记录
    // ======================================================================

    @Test
    @DisplayName("createOrder_success_shouldDeleteCart — 购物车模式下单成功后调用deleteByIdsAndUserId删除购物车")
    void createOrder_success_shouldDeleteCart() throws InterruptedException {
        // given: 购物车模式，购物车中有一条记录
        Long cartId = 601L;
        List<Long> cartIds = Collections.singletonList(cartId);

        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setCartIds(cartIds);
        req.setAddressId(ADDRESS_ID);

        // 购物车记录
        MallCart cart = new MallCart();
        cart.setId(cartId);
        cart.setUserId(USER_ID);
        cart.setSpuId(10L);
        cart.setSkuId(SKU_ID);
        cart.setQuantity(2);
        when(mallCartMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(cart));

        // SKU：在售，库存充足
        ProductSkuVO sku = buildSku(SKU_ID, new BigDecimal("50.00"), 100);
        when(productFeignClient.batchGetSku(Collections.singletonList(SKU_ID)))
                .thenReturn(R.ok(Collections.singletonList(sku)));

        // Redisson 锁
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // 库存扣减成功
        when(stockFeignClient.deductStock(any(StockDeductVO.class)))
                .thenReturn(R.ok(Boolean.TRUE));

        // 收货地址
        MallAddress address = buildAddress(ADDRESS_ID, USER_ID);
        when(mallAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(address);

        // Feign 创建订单成功，返回新订单 ID
        when(orderFeignClient.createOrder(any())).thenReturn(R.ok(ORDER_ID));

        // when
        orderService.createOrder(req, USER_ID);

        // then: 必须调用 deleteByIdsAndUserId 删除购物车，且传入 userId 防止越权
        verify(mallCartMapper, times(1)).deleteByIdsAndUserId(cartIds, USER_ID);
    }

    // ======================================================================
    // 私有构建辅助方法
    // ======================================================================

    private OrderCreateReqVO buildDirectBuyReq(Long skuId, Integer quantity, Long addressId) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setAddressId(addressId);
        return req;
    }

    private ProductSkuVO buildSku(Long skuId, BigDecimal price, Integer stock) {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(skuId);
        sku.setSpuId(10L);
        sku.setSkuName("测试SKU-" + skuId);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        sku.setImage("http://img.example.com/sku.jpg");
        return sku;
    }

    private OrderInfoVO buildOrderInfoVO(Long orderId, Long userId, Integer status) {
        OrderInfoVO vo = new OrderInfoVO();
        vo.setId(orderId);
        vo.setOrderNo("TEST" + orderId);
        vo.setUserId(userId);
        vo.setStatus(status);
        vo.setPayAmount(new BigDecimal("100.00"));
        vo.setItems(Collections.emptyList());
        return vo;
    }

    private MallAddress buildAddress(Long addressId, Long userId) {
        MallAddress address = new MallAddress();
        address.setId(addressId);
        address.setUserId(userId);
        address.setName("张三");
        address.setPhone("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路1号");
        return address;
    }
}
