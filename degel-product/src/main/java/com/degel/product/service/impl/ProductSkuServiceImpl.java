package com.degel.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.product.entity.ProductSku;
import com.degel.product.mapper.ProductSkuMapper;
import com.degel.product.service.IProductSkuService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku> implements IProductSkuService {

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
}
