package com.degel.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductSku;

import java.util.List;

public interface IProductSkuService extends IService<ProductSku> {

    List<ProductSku> listBySpuId(Long spuId);

    void deleteBySpuId(Long spuId);
}
