package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.app.entity.MallCart;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.mapper.MallCartMapper;
import com.degel.app.service.impl.CartServiceImpl;
import com.degel.app.vo.CartCheckVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.app.vo.dto.CartAddReqVO;
import com.degel.app.vo.dto.CartCheckReqVO;
import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CartServiceImpl 单元测试
 *
 * <p>覆盖用例：
 * 1. addToCart_existingSku_shouldIncreaseQuantity   — 同SKU累加数量
 * 2. addToCart_exceedStock_shouldThrowException     — 超出库存上限报错
 * 3. addToCart_skuNotOnSale_shouldThrow             — SKU下架不能加购
 * 4. checkCart_shouldCalculateTotalCorrectly        — 预览结算金额计算正确
 * 5. deleteCart_otherUserItems_shouldNotDelete      — 批量删除只删自己记录
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartServiceImpl 单元测试")
class CartServiceTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private MallCartMapper mallCartMapper;

    @Mock
    private ProductFeignClient productFeignClient;

    @InjectMocks
    private CartServiceImpl cartService;

    // ======================================================================
    // 用例 1：addToCart — 同 SKU 已在购物车中，累加数量
    // ======================================================================

    @Test
    @DisplayName("addToCart_existingSku_shouldIncreaseQuantity — 购物车中已有同SKU时数量累加而非新增")
    void addToCart_existingSku_shouldIncreaseQuantity() {
        final Long skuId = 2001L;

        // given: SKU 正常上架，库存充足
        ProductSkuVO sku = buildSku(skuId, 10L, new BigDecimal("59.00"), 100);
        mockSingleSku(skuId, sku);

        // 购物车中已有该 SKU，数量=3
        MallCart existCart = buildCart(500L, USER_ID, 10L, skuId, 3);
        when(mallCartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existCart);

        // when: 再加 2 个
        CartAddReqVO req = buildAddReq(skuId, 2);
        cartService.addToCart(USER_ID, req);

        // then: 调用 updateById，数量变为 5
        ArgumentCaptor<MallCart> captor = ArgumentCaptor.forClass(MallCart.class);
        verify(mallCartMapper, times(1)).updateById(captor.capture());
        verify(mallCartMapper, never()).insert(any());
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
    }

    // ======================================================================
    // 用例 2：addToCart — 累加后超出库存上限，抛出 BusinessException(40002)
    // ======================================================================

    @Test
    @DisplayName("addToCart_exceedStock_shouldThrowException — 累加数量超过SKU库存时抛出40002异常")
    void addToCart_exceedStock_shouldThrowException() {
        final Long skuId = 2002L;

        // given: SKU 库存 5
        ProductSkuVO sku = buildSku(skuId, 10L, new BigDecimal("99.00"), 5);
        mockSingleSku(skuId, sku);

        // 购物车已有 4 个
        MallCart existCart = buildCart(501L, USER_ID, 10L, skuId, 4);
        when(mallCartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existCart);

        // when & then: 再加 3 个，4+3=7 > 5，抛异常
        CartAddReqVO req = buildAddReq(skuId, 3);
        assertThatThrownBy(() -> cartService.addToCart(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40002);
                    assertThat(be.getMessage()).contains("库存");
                });

        // 不应有任何写操作
        verify(mallCartMapper, never()).updateById(any());
        verify(mallCartMapper, never()).insert(any());
    }

    // ======================================================================
    // 用例 3：addToCart — SKU 已下架（status=0），抛出 BusinessException(40001)
    // ======================================================================

    @Test
    @DisplayName("addToCart_skuNotOnSale_shouldThrow — SKU下架时加购抛出40001异常")
    void addToCart_skuNotOnSale_shouldThrow() {
        final Long skuId = 2003L;

        // given: SKU status=0（已下架）
        ProductSkuVO offlineSku = buildSku(skuId, 10L, new BigDecimal("79.00"), 30);
        offlineSku.setStatus(0);
        mockSingleSku(skuId, offlineSku);

        // when & then
        CartAddReqVO req = buildAddReq(skuId, 1);
        assertThatThrownBy(() -> cartService.addToCart(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40001);
                    assertThat(be.getMessage()).contains("下架");
                });

        // 不查询购物车，不执行写操作
        verify(mallCartMapper, never()).selectOne(any());
        verify(mallCartMapper, never()).insert(any());
        verify(mallCartMapper, never()).updateById(any());
    }

    // ======================================================================
    // 用例 4：checkCart — 预览结算金额计算正确
    // ======================================================================

    @Test
    @DisplayName("checkCart_shouldCalculateTotalCorrectly — 结算汇总 totalAmount = Σ(price × quantity)")
    void checkCart_shouldCalculateTotalCorrectly() {
        // given: 用户勾选了两条购物车记录
        Long cartId1 = 601L;
        Long cartId2 = 602L;
        Long skuId1  = 3001L;
        Long skuId2  = 3002L;
        Long spuId1  = 20L;
        Long spuId2  = 21L;

        MallCart cart1 = buildCart(cartId1, USER_ID, spuId1, skuId1, 2);  // 2 × 50.00 = 100.00
        MallCart cart2 = buildCart(cartId2, USER_ID, spuId2, skuId2, 3);  // 3 × 30.00 = 90.00
        when(mallCartMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(cart1, cart2));

        // SKU 批量查询
        ProductSkuVO sku1 = buildSku(skuId1, spuId1, new BigDecimal("50.00"), 20);
        ProductSkuVO sku2 = buildSku(skuId2, spuId2, new BigDecimal("30.00"), 10);
        R<List<ProductSkuVO>> skuResp = R.ok(Arrays.asList(sku1, sku2));
        when(productFeignClient.batchGetSku(anyList())).thenReturn(skuResp);

        // SPU 查询（逐个）
        ProductSpuVO spu1 = buildSpuVO(spuId1, "商品A");
        ProductSpuVO spu2 = buildSpuVO(spuId2, "商品B");
        when(productFeignClient.getSpuDetail(spuId1)).thenReturn(R.ok(spu1));
        when(productFeignClient.getSpuDetail(spuId2)).thenReturn(R.ok(spu2));

        CartCheckReqVO reqVO = new CartCheckReqVO();
        reqVO.setCartIds(Arrays.asList(cartId1, cartId2));

        // when
        CartCheckVO result = cartService.checkCart(USER_ID, reqVO);

        // then: 金额校验
        // totalAmount = 2×50 + 3×30 = 100 + 90 = 190
        assertThat(result.getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("190.00"));
        // 运费固定为 0
        assertThat(result.getFreightAmount())
                .isEqualByComparingTo(BigDecimal.ZERO);
        // 实付 = total + freight = 190 + 0 = 190
        assertThat(result.getPayAmount())
                .isEqualByComparingTo(new BigDecimal("190.00"));
        // 明细数量
        assertThat(result.getItems()).hasSize(2);
        // 单条 subtotal 校验
        result.getItems().forEach(item -> {
            if (item.getSkuId().equals(skuId1)) {
                assertThat(item.getSubtotal()).isEqualByComparingTo("100.00");
                assertThat(item.getSpuName()).isEqualTo("商品A");
            } else if (item.getSkuId().equals(skuId2)) {
                assertThat(item.getSubtotal()).isEqualByComparingTo("90.00");
                assertThat(item.getSpuName()).isEqualTo("商品B");
            }
        });
    }

    // ======================================================================
    // 用例 5：deleteCart — 只删除属于当前用户的记录
    // ======================================================================

    @Test
    @DisplayName("deleteCart_otherUserItems_shouldNotDelete — deleteByIdsAndUserId携带userId，防止越权删除他人记录")
    void deleteCart_otherUserItems_shouldNotDelete() {
        // given: 目标删除 ID 列表（其中一条可能属于其他用户）
        List<Long> idsToDelete = Arrays.asList(701L, 702L, 703L);
        // Mapper 层通过 userId 过滤，模拟只删除了属于当前用户的 2 条
        when(mallCartMapper.deleteByIdsAndUserId(idsToDelete, USER_ID)).thenReturn(2);

        // when
        cartService.deleteCart(USER_ID, idsToDelete);

        // then: deleteByIdsAndUserId 被调用，且传入了正确的 userId（保证只删自己的数据）
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<List> idsCaptor    = ArgumentCaptor.forClass(List.class);
        verify(mallCartMapper, times(1))
                .deleteByIdsAndUserId(idsCaptor.capture(), userIdCaptor.capture());

        // 传入的 userId 必须是当前用户，杜绝越权
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        // 传入的 id 列表与请求一致
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(idsToDelete);

        // 绝对不允许使用不带 userId 的批量删除（防止全表误删）
        verify(mallCartMapper, never()).deleteBatchIds(any());
    }

    // ======================================================================
    // 私有构建辅助方法
    // ======================================================================

    /** 构造加购请求 VO */
    private CartAddReqVO buildAddReq(Long skuId, Integer quantity) {
        CartAddReqVO vo = new CartAddReqVO();
        vo.setSkuId(skuId);
        vo.setQuantity(quantity);
        return vo;
    }

    /** 构造购物车实体 */
    private MallCart buildCart(Long id, Long userId, Long spuId, Long skuId, Integer quantity) {
        MallCart cart = new MallCart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setSpuId(spuId);
        cart.setSkuId(skuId);
        cart.setQuantity(quantity);
        return cart;
    }

    /** 构造 SKU VO，status=1（正常） */
    private ProductSkuVO buildSku(Long id, Long spuId, BigDecimal price, Integer stock) {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(id);
        sku.setSpuId(spuId);
        sku.setSkuCode("SKU-" + id);
        sku.setPrice(price);
        sku.setOriginalPrice(price.add(new BigDecimal("10.00")));
        sku.setStock(stock);
        sku.setStatus(1);
        sku.setImage("http://img.example.com/sku" + id + ".jpg");
        return sku;
    }

    /** 构造 SPU VO，status=1（上架） */
    private ProductSpuVO buildSpuVO(Long id, String name) {
        ProductSpuVO spu = new ProductSpuVO();
        spu.setId(id);
        spu.setName(name);
        spu.setStatus(1);
        spu.setAuditStatus(1);
        return spu;
    }

    /**
     * Mock getSingleSku(skuId) 的底层调用：
     * CartServiceImpl.getSingleSku() 内部调用 productFeignClient.batchGetSku(singletonList(skuId))
     */
    private void mockSingleSku(Long skuId, ProductSkuVO sku) {
        R<List<ProductSkuVO>> resp = R.ok(Collections.singletonList(sku));
        when(productFeignClient.batchGetSku(Collections.singletonList(skuId))).thenReturn(resp);
    }
}
