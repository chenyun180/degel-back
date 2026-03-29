package com.degel.app.service;

import com.alibaba.fastjson.JSON;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.service.impl.ProductServiceImpl;
import com.degel.app.vo.AppSkuVO;
import com.degel.app.vo.AppSpuDetailVO;
import com.degel.app.vo.CategoryTreeVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
 * ProductServiceImpl 单元测试
 *
 * <p>覆盖用例：
 * 1. getCategoryTree_cacheHit_shouldNotCallFeign     — 缓存命中不调Feign
 * 2. getCategoryTree_cacheMiss_shouldCallFeignAndCache — 缓存未命中调Feign并写缓存
 * 3. getProductDetail_notOnSale_shouldThrow40400     — 下架商品返回404
 * 4. getProductDetail_success_shouldReturnMergedVO   — 正常返回SPU+SKU合并VO
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl 单元测试")
class ProductServiceTest {

    private static final String CACHE_CATEGORY_TREE = "product:category:tree";
    private static final String CACHE_SPU_PREFIX    = "product:spu:";

    @Mock
    private ProductFeignClient productFeignClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        // 让 redisTemplate.opsForValue() 始终返回 mock 的 ValueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ======================================================================
    // 用例 1：getCategoryTree — 缓存命中，不应调用 Feign
    // ======================================================================

    @Test
    @DisplayName("getCategoryTree_cacheHit_shouldNotCallFeign — 缓存命中时直接返回缓存数据，不调用Feign")
    void getCategoryTree_cacheHit_shouldNotCallFeign() {
        // given: Redis 返回已缓存的分类列表（fastjson 序列化格式）
        CategoryTreeVO node = new CategoryTreeVO();
        node.setId(1L);
        node.setName("服装");
        List<CategoryTreeVO> cached = Collections.singletonList(node);
        // ProductServiceImpl 通过 JSON.parseArray(JSON.toJSONString(cached), ...) 还原
        when(valueOperations.get(CACHE_CATEGORY_TREE)).thenReturn(cached);

        // when
        List<CategoryTreeVO> result = productService.getCategoryTree();

        // then: Feign 不被调用
        verify(productFeignClient, never()).getCategoryTree();
        // 结果非空且包含正确数据
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("服装");
    }

    // ======================================================================
    // 用例 2：getCategoryTree — 缓存未命中，调 Feign 并写缓存
    // ======================================================================

    @Test
    @DisplayName("getCategoryTree_cacheMiss_shouldCallFeignAndCache — 缓存未命中时调用Feign并将结果写入Redis")
    void getCategoryTree_cacheMiss_shouldCallFeignAndCache() {
        // given: 缓存中没有数据
        when(valueOperations.get(CACHE_CATEGORY_TREE)).thenReturn(null);

        // Feign 返回两个分类节点
        CategoryTreeVO c1 = buildCategory(10L, "电子产品");
        CategoryTreeVO c2 = buildCategory(20L, "生活用品");
        R<List<CategoryTreeVO>> feignResult = R.ok(Arrays.asList(c1, c2));
        when(productFeignClient.getCategoryTree()).thenReturn(feignResult);

        // when
        List<CategoryTreeVO> result = productService.getCategoryTree();

        // then: Feign 被调用一次
        verify(productFeignClient, times(1)).getCategoryTree();
        // 结果正确
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryTreeVO::getName)
                .containsExactlyInAnyOrder("电子产品", "生活用品");
        // Redis 写缓存被调用，TTL=30min
        verify(valueOperations, times(1))
                .set(eq(CACHE_CATEGORY_TREE), anyList(), eq(30L), eq(TimeUnit.MINUTES));
    }

    // ======================================================================
    // 用例 3：getProductDetail — 商品下架，应抛出 BusinessException(40400)
    // ======================================================================

    @Test
    @DisplayName("getProductDetail_notOnSale_shouldThrow40400 — 下架(status=0)商品抛出40400业务异常")
    void getProductDetail_notOnSale_shouldThrow40400() {
        final Long spuId = 999L;

        // given: Redis 中无 SPU 缓存
        when(valueOperations.get(CACHE_SPU_PREFIX + spuId)).thenReturn(null);

        // Feign 返回 status=0（已下架）的 SPU
        ProductSpuVO offlineSpu = new ProductSpuVO();
        offlineSpu.setId(spuId);
        offlineSpu.setName("已下架商品");
        offlineSpu.setStatus(0);        // 下架
        offlineSpu.setAuditStatus(1);
        when(productFeignClient.getSpuDetail(spuId)).thenReturn(R.ok(offlineSpu));

        // SKU 查询也需要 mock（并发调用）
        when(productFeignClient.getSkuList(spuId)).thenReturn(R.ok(Collections.emptyList()));

        // when & then
        assertThatThrownBy(() -> productService.getProductDetail(spuId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下架")
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40400);
                });
    }

    // ======================================================================
    // 用例 4：getProductDetail — 正常商品，返回 SPU+SKU 合并 VO
    // ======================================================================

    @Test
    @DisplayName("getProductDetail_success_shouldReturnMergedVO — 正常上架商品返回包含SKU列表的详情VO")
    void getProductDetail_success_shouldReturnMergedVO() {
        final Long spuId = 100L;

        // given: Redis 中无 SPU 缓存
        when(valueOperations.get(CACHE_SPU_PREFIX + spuId)).thenReturn(null);

        // 构造正常上架的 SPU
        ProductSpuVO spu = new ProductSpuVO();
        spu.setId(spuId);
        spu.setName("测试商品");
        spu.setSubtitle("这是副标题");
        spu.setMainImage("http://img.example.com/main.jpg");
        spu.setImages("[\"http://img.example.com/1.jpg\",\"http://img.example.com/2.jpg\"]");
        spu.setDetailContent("<p>详情</p>");
        spu.setSaleCount(200);
        spu.setViewCount(5000);
        spu.setStatus(1);
        spu.setAuditStatus(1);
        when(productFeignClient.getSpuDetail(spuId)).thenReturn(R.ok(spu));

        // 构造两个 SKU
        ProductSkuVO sku1 = buildSku(1001L, spuId, new BigDecimal("99.00"), 50,
                "{\"颜色\":\"红\",\"尺码\":\"M\"}");
        ProductSkuVO sku2 = buildSku(1002L, spuId, new BigDecimal("109.00"), 0,
                "{\"颜色\":\"蓝\",\"尺码\":\"XL\"}");
        when(productFeignClient.getSkuList(spuId)).thenReturn(R.ok(Arrays.asList(sku1, sku2)));

        // SPU 缓存写入不报错
        doNothing().when(valueOperations)
                .set(eq(CACHE_SPU_PREFIX + spuId), any(), eq(5L), eq(TimeUnit.MINUTES));

        // when
        AppSpuDetailVO detail = productService.getProductDetail(spuId);

        // then: SPU 基本字段
        assertThat(detail.getSpuId()).isEqualTo(spuId);
        assertThat(detail.getName()).isEqualTo("测试商品");
        assertThat(detail.getSubtitle()).isEqualTo("这是副标题");
        assertThat(detail.getSaleCount()).isEqualTo(200);
        assertThat(detail.getViewCount()).isEqualTo(5000);

        // 图片列表解析正确
        assertThat(detail.getImages()).hasSize(2);
        assertThat(detail.getImages()).contains(
                "http://img.example.com/1.jpg",
                "http://img.example.com/2.jpg"
        );

        // SKU 列表
        assertThat(detail.getSkuList()).hasSize(2);
        List<AppSkuVO> skuList = detail.getSkuList();

        // SKU1 有库存 → soldOut=false
        AppSkuVO appSku1 = skuList.stream()
                .filter(s -> s.getSkuId().equals(1001L))
                .findFirst().orElseThrow();
        assertThat(appSku1.getPrice()).isEqualByComparingTo("99.00");
        assertThat(appSku1.getStock()).isEqualTo(50);
        assertThat(appSku1.getSoldOut()).isFalse();

        // SKU2 库存=0 → soldOut=true
        AppSkuVO appSku2 = skuList.stream()
                .filter(s -> s.getSkuId().equals(1002L))
                .findFirst().orElseThrow();
        assertThat(appSku2.getPrice()).isEqualByComparingTo("109.00");
        assertThat(appSku2.getStock()).isEqualTo(0);
        assertThat(appSku2.getSoldOut()).isTrue();

        // SPU 写缓存被调用一次（5 min）
        verify(valueOperations, times(1))
                .set(eq(CACHE_SPU_PREFIX + spuId), any(), eq(5L), eq(TimeUnit.MINUTES));
    }

    // ======================================================================
    // 私有构建辅助方法
    // ======================================================================

    private CategoryTreeVO buildCategory(Long id, String name) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private ProductSkuVO buildSku(Long id, Long spuId, BigDecimal price,
                                   Integer stock, String specData) {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(id);
        sku.setSpuId(spuId);
        sku.setSkuCode("SKU-" + id);
        sku.setPrice(price);
        sku.setOriginalPrice(price.add(new BigDecimal("20.00")));
        sku.setStock(stock);
        sku.setImage("http://img.example.com/sku" + id + ".jpg");
        sku.setStatus(1);
        sku.setSpecData(specData);
        return sku;
    }
}
