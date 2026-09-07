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
            // 扣库存即售出：SPU 销量 +quantity（同事务，与扣减一起成败）
            ProductSku sku = getById(skuId);
            if (sku != null) {
                spuMapper.update(null, new LambdaUpdateWrapper<ProductSpu>()
                        .setSql("sale_count = sale_count + " + quantity)
                        .eq(ProductSpu::getId, sku.getSpuId()));
            }
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
            // 回补库存即退回：销量 -quantity，GREATEST 兜底不为负（历史脏数据时）
            ProductSku sku = getById(skuId);
            if (sku != null) {
                spuMapper.update(null, new LambdaUpdateWrapper<ProductSpu>()
                        .setSql("sale_count = GREATEST(sale_count - " + quantity + ", 0)")
                        .eq(ProductSpu::getId, sku.getSpuId()));
            }
            publishStockEvent(skuId);
        }
        return updated;
    }

    /** 库存/销量变化后同步所属 SPU 的 ES 索引（totalStock、saleCount 冗余字段） */
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
        Map<Long, ProductSpu> spuMap = spuMapper.selectBatchIds(
                        skus.stream().map(ProductSku::getSpuId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ProductSpu::getId, spu -> spu, (a, b) -> a));
        return skus.stream().map(sku -> {
            AppSkuVo vo = new AppSkuVo();
            vo.setId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSkuCode(sku.getSkuCode());
            ProductSpu spu = spuMap.get(sku.getSpuId());
            String spuName = spu != null ? spu.getName() : null;
            vo.setSpuName(spuName);
            // SPU 带出店铺归属（C 端按店拆单用）
            if (spu != null) {
                vo.setShopId(spu.getShopId());
            }
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
