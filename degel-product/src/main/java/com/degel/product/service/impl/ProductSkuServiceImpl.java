package com.degel.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.product.entity.ProductSku;
import com.degel.product.es.SpuIndexEvent;
import com.degel.product.entity.ProductSpu;
import com.degel.product.mapper.ProductSkuMapper;
import com.degel.product.mapper.ProductSpuMapper;
import com.degel.product.service.IProductSkuService;
import com.degel.product.vo.AppSkuVo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku> implements IProductSkuService {

    // 注入 Mapper 而非 Service，避免与 ProductSpuServiceImpl 形成构造器循环依赖
    private final ProductSpuMapper spuMapper;
    /** 库存变化 → ES 索引 totalStock 冗余字段同步（事务提交后异步） */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<ProductSku> listBySpuId(Long spuId) {
        return list(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .eq(ProductSku::getDelFlag, 0)
                .orderByAsc(ProductSku::getId));
    }

    @Override
    public void deleteBySpuId(Long spuId) {
        update(new LambdaUpdateWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .set(ProductSku::getDelFlag, 1));
    }

    @Override
    public List<AppSkuVo> getSkuList(Long spuId) {
        return toVoList(listBySpuId(spuId));
    }

    @Override
    public List<AppSkuVo> batchGetSku(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductSku> skus = list(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getId, skuIds)
                .eq(ProductSku::getDelFlag, 0));
        return toVoList(skus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity <= 0) {
            return false;
        }
        // 原子扣减：stock >= quantity 且上架中才更新；影响行数=0 即库存不足
        // quantity 为 Integer 直接拼接，无注入风险
        boolean updated = update(new LambdaUpdateWrapper<ProductSku>()
                .setSql("stock = stock - " + quantity)
                .eq(ProductSku::getId, skuId)
                .eq(ProductSku::getDelFlag, 0)
                .eq(ProductSku::getStatus, 1)
                .ge(ProductSku::getStock, quantity));
        if (updated) {
            publishStockEvent(skuId);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreStock(Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity <= 0) {
            return false;
        }
        boolean updated = update(new LambdaUpdateWrapper<ProductSku>()
                .setSql("stock = stock + " + quantity)
                .eq(ProductSku::getId, skuId)
                .eq(ProductSku::getDelFlag, 0));
        if (updated) {
            publishStockEvent(skuId);
        }
        return updated;
    }

    /** 库存变化后同步所属 SPU 的 ES 索引（totalStock 冗余字段） */
    private void publishStockEvent(Long skuId) {
        ProductSku sku = getById(skuId);
        if (sku != null) {
            eventPublisher.publishEvent(new SpuIndexEvent(sku.getSpuId(), false));
        }
    }

    private List<AppSkuVo> toVoList(List<ProductSku> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyList();
        }
        // 关联 SPU 名称（degel-app 下单快照取 spuName，skuName 兜底同值）
        Map<Long, String> spuNameMap = spuMapper.selectBatchIds(
                        skus.stream().map(ProductSku::getSpuId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ProductSpu::getId, ProductSpu::getName, (a, b) -> a));
        return skus.stream().map(sku -> {
            AppSkuVo vo = new AppSkuVo();
            vo.setId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSkuCode(sku.getSkuCode());
            String spuName = spuNameMap.get(sku.getSpuId());
            vo.setSpuName(spuName);
            vo.setSkuName(spuName);
            vo.setSpecData(sku.getSpecData());
            vo.setPrice(sku.getPrice());
            vo.setOriginalPrice(sku.getOriginalPrice());
            vo.setStock(sku.getStock());
            vo.setImage(sku.getImage());
            vo.setStatus(sku.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }
}
