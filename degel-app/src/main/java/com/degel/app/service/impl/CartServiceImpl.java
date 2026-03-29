package com.degel.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.app.entity.MallCart;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.mapper.MallCartMapper;
import com.degel.app.service.CartService;
import com.degel.app.vo.*;
import com.degel.app.vo.dto.CartAddReqVO;
import com.degel.app.vo.dto.CartCheckReqVO;
import com.degel.app.vo.dto.CartUpdateReqVO;
import com.degel.common.core.R;
import com.degel.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车 ServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final MallCartMapper mallCartMapper;
    private final ProductFeignClient productFeignClient;

    // ==================== B-05: 加入购物车 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToCart(Long userId, CartAddReqVO reqVO) {
        Long skuId = reqVO.getSkuId();
        Integer quantity = reqVO.getQuantity();

        // 1. Feign 查询 SKU，校验状态
        ProductSkuVO sku = getSingleSku(skuId);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BusinessException(40001, "SKU不存在或已下架");
        }

        // 2. 查询该用户购物车中是否已有该 SKU
        MallCart existCart = mallCartMapper.selectOne(
                new LambdaQueryWrapper<MallCart>()
                        .eq(MallCart::getUserId, userId)
                        .eq(MallCart::getSkuId, skuId)
                        .eq(MallCart::getDelFlag, 0)
        );

        if (existCart != null) {
            // 3a. 已存在 → 原子累加数量，先校验上限后再执行
            int newQuantity = existCart.getQuantity() + quantity;
            if (sku.getStock() != null && newQuantity > sku.getStock()) {
                throw new BusinessException(40002, "超出库存上限");
            }
            mallCartMapper.incrementQuantity(existCart.getId(), userId, quantity);
        } else {
            // 3b. 不存在 → INSERT
            if (sku.getStock() != null && quantity > sku.getStock()) {
                throw new BusinessException(40002, "超出库存上限");
            }
            MallCart cart = new MallCart();
            cart.setUserId(userId);
            cart.setSpuId(sku.getSpuId());
            cart.setSkuId(skuId);
            cart.setQuantity(quantity);
            mallCartMapper.insert(cart);
        }
    }

    // ==================== B-06: 购物车列表 ====================

    @Override
    public List<CartItemVO> listCart(Long userId) {
        // 1. 查询用户购物车列表（未删除，按 update_time DESC）
        List<MallCart> cartList = mallCartMapper.selectList(
                new LambdaQueryWrapper<MallCart>()
                        .eq(MallCart::getUserId, userId)
                        .eq(MallCart::getDelFlag, 0)
                        .orderByDesc(MallCart::getUpdateTime)
        );

        if (CollectionUtils.isEmpty(cartList)) {
            return Collections.emptyList();
        }

        // 2. 收集所有 skuId，批量 Feign 查询 SKU
        List<Long> skuIds = cartList.stream().map(MallCart::getSkuId).collect(Collectors.toList());
        Map<Long, ProductSkuVO> skuMap = batchGetSkuMap(skuIds);

        // 3. 收集所有 spuId，批量查询 SPU 名称（通过 SKU 的 spuId）
        // SKU 中已含 spuId，直接从已有数据中获取 SPU 信息（此处用 cartList 中的 spuId）
        // SPU 名称通过 batch sku 中没有，需要实时查询
        // 简化处理：使用购物车中记录的 spuId，通过 Feign 单独查（可优化为批量）
        // 注：如果 skuMap 中有 spuId，则收集 spuId 批量查 SPU
        Set<Long> spuIds = cartList.stream().map(MallCart::getSpuId).collect(Collectors.toSet());
        Map<Long, ProductSpuVO> spuMap = batchGetSpuMap(spuIds);

        // 4. 组装 CartItemVO，invalid 商品排到最后
        List<CartItemVO> validItems = new ArrayList<>();
        List<CartItemVO> invalidItems = new ArrayList<>();

        for (MallCart cart : cartList) {
            CartItemVO item = buildCartItemVO(cart, skuMap, spuMap);
            if (Boolean.TRUE.equals(item.getInvalid())) {
                invalidItems.add(item);
            } else {
                validItems.add(item);
            }
        }

        validItems.addAll(invalidItems);
        return validItems;
    }

    // ==================== B-07: 修改数量 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long cartId, CartUpdateReqVO reqVO) {
        Integer quantity = reqVO.getQuantity();

        // 查询购物车记录
        MallCart cart = mallCartMapper.selectOne(
                new LambdaQueryWrapper<MallCart>()
                        .eq(MallCart::getId, cartId)
                        .eq(MallCart::getUserId, userId)
                        .eq(MallCart::getDelFlag, 0)
        );
        if (cart == null) {
            throw new BusinessException(40003, "购物车记录不存在");
        }

        // 校验 quantity >= 1（注解已校验，此处二次保险）
        if (quantity < 1) {
            throw new BusinessException(40004, "数量不能小于1");
        }

        // 实时查询 SKU 校验库存
        ProductSkuVO sku = getSingleSku(cart.getSkuId());
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BusinessException(40001, "SKU不存在或已下架");
        }
        if (sku.getStock() != null && quantity > sku.getStock()) {
            throw new BusinessException(40002, "超出库存上限");
        }

        // 更新数量
        cart.setQuantity(quantity);
        mallCartMapper.updateById(cart);
    }

    // ==================== B-08: 批量删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCart(Long userId, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        mallCartMapper.deleteByIdsAndUserId(ids, userId);
    }

    // ==================== B-09: 预览结算 ====================

    @Override
    public CartCheckVO checkCart(Long userId, CartCheckReqVO reqVO) {
        List<Long> cartIds = reqVO.getCartIds();

        // 1. 查询勾选的购物车记录
        List<MallCart> cartList = mallCartMapper.selectList(
                new LambdaQueryWrapper<MallCart>()
                        .in(MallCart::getId, cartIds)
                        .eq(MallCart::getUserId, userId)
                        .eq(MallCart::getDelFlag, 0)
        );

        if (CollectionUtils.isEmpty(cartList)) {
            throw new BusinessException(40005, "请选择有效商品");
        }

        // 2. 批量实时查询 SKU 价格
        List<Long> skuIds = cartList.stream().map(MallCart::getSkuId).collect(Collectors.toList());
        Map<Long, ProductSkuVO> skuMap = batchGetSkuMap(skuIds);

        // 3. 批量查询 SPU 名称
        Set<Long> spuIds = cartList.stream().map(MallCart::getSpuId).collect(Collectors.toSet());
        Map<Long, ProductSpuVO> spuMap = batchGetSpuMap(spuIds);

        // 4. 组装结算明细
        List<CartCheckItemVO> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (MallCart cart : cartList) {
            ProductSkuVO sku = skuMap.get(cart.getSkuId());
            if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BusinessException(40001, "SKU不存在或已下架，skuId=" + cart.getSkuId());
            }

            ProductSpuVO spu = spuMap.get(cart.getSpuId());
            String spuName = (spu != null) ? spu.getName() : "";
            String skuSpec = parseSkuSpec(sku.getSpecData());

            CartCheckItemVO item = new CartCheckItemVO();
            item.setCartId(cart.getId());
            item.setSkuId(cart.getSkuId());
            item.setSpuId(cart.getSpuId());
            item.setSpuName(spuName);
            item.setSkuSpec(skuSpec);
            item.setSkuImage(sku.getImage());
            item.setPrice(sku.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setStock(sku.getStock());

            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));
            item.setSubtotal(subtotal);
            totalAmount = totalAmount.add(subtotal);

            items.add(item);
        }

        // 5. 组装结算汇总
        CartCheckVO checkVO = new CartCheckVO();
        checkVO.setItems(items);
        checkVO.setTotalAmount(totalAmount);
        checkVO.setFreightAmount(BigDecimal.ZERO);
        checkVO.setPayAmount(totalAmount);
        return checkVO;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查询单个 SKU
     */
    private ProductSkuVO getSingleSku(Long skuId) {
        R<List<ProductSkuVO>> r = productFeignClient.batchGetSku(Collections.singletonList(skuId));
        if (r == null || CollectionUtils.isEmpty(r.getData())) {
            return null;
        }
        return r.getData().get(0);
    }

    /**
     * 批量查询 SKU，返回 Map<skuId, ProductSkuVO>
     */
    private Map<Long, ProductSkuVO> batchGetSkuMap(List<Long> skuIds) {
        if (CollectionUtils.isEmpty(skuIds)) {
            return Collections.emptyMap();
        }
        try {
            R<List<ProductSkuVO>> r = productFeignClient.batchGetSku(skuIds);
            if (r == null || CollectionUtils.isEmpty(r.getData())) {
                return Collections.emptyMap();
            }
            return r.getData().stream()
                    .collect(Collectors.toMap(ProductSkuVO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.error("[CartServiceImpl] batchGetSkuMap 失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量查询 SPU（通过单个 Feign 调用），返回 Map<spuId, ProductSpuVO>
     * 注：当前 ProductFeignClient 无批量查 SPU 接口，通过逐个查询（可后续优化）
     */
    private Map<Long, ProductSpuVO> batchGetSpuMap(Set<Long> spuIds) {
        if (CollectionUtils.isEmpty(spuIds)) {
            return Collections.emptyMap();
        }
        Map<Long, ProductSpuVO> spuMap = new HashMap<>();
        for (Long spuId : spuIds) {
            try {
                R<ProductSpuVO> r = productFeignClient.getSpuDetail(spuId);
                if (r != null && r.getData() != null) {
                    spuMap.put(spuId, r.getData());
                }
            } catch (Exception e) {
                log.warn("[CartServiceImpl] 查询 SPU 失败，spuId={}", spuId, e);
            }
        }
        return spuMap;
    }

    /**
     * 组装单个 CartItemVO
     */
    private CartItemVO buildCartItemVO(MallCart cart,
                                       Map<Long, ProductSkuVO> skuMap,
                                       Map<Long, ProductSpuVO> spuMap) {
        CartItemVO item = new CartItemVO();
        item.setId(cart.getId());
        item.setSpuId(cart.getSpuId());
        item.setSkuId(cart.getSkuId());
        item.setQuantity(cart.getQuantity());

        ProductSkuVO sku = skuMap.get(cart.getSkuId());
        ProductSpuVO spu = spuMap.get(cart.getSpuId());

        boolean invalid = (sku == null || !Integer.valueOf(1).equals(sku.getStatus()))
                || (spu == null || !Integer.valueOf(1).equals(spu.getStatus()));
        item.setInvalid(invalid);

        if (spu != null) {
            item.setSpuName(spu.getName());
        }

        if (sku != null) {
            item.setSkuImage(sku.getImage());
            item.setPrice(sku.getPrice());
            item.setStock(sku.getStock());
            item.setSkuSpec(parseSkuSpec(sku.getSpecData()));

            if (!invalid && sku.getPrice() != null) {
                item.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
            } else {
                item.setSubtotal(BigDecimal.ZERO);
            }
        } else {
            item.setSubtotal(BigDecimal.ZERO);
        }

        return item;
    }

    /**
     * 解析 SKU 规格 JSON 为可读字符串，如 "颜色:红 / 尺码:XL"
     */
    private String parseSkuSpec(String specDataJson) {
        if (specDataJson == null || specDataJson.isEmpty()) {
            return "";
        }
        try {
            Map<String, String> specMap = JSON.parseObject(specDataJson, new TypeReference<Map<String, String>>() {});
            return specMap.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(" / "));
        } catch (Exception e) {
            return specDataJson;
        }
    }
}
