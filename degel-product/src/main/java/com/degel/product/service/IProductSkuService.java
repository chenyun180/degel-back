package com.degel.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductSku;
import com.degel.product.vo.AppSkuVo;

import java.util.List;

public interface IProductSkuService extends IService<ProductSku> {

    List<ProductSku> listBySpuId(Long spuId);

    void deleteBySpuId(Long spuId);

    /**
     * 按 SPU 查 SKU 列表（C 端 BFF 专用，返回 AppSkuVo）
     */
    List<AppSkuVo> getSkuList(Long spuId);

    /**
     * 批量查询 SKU（内部接口，购物车/下单校验用）
     */
    List<AppSkuVo> batchGetSku(List<Long> skuIds);

    /**
     * 原子扣减库存：stock >= quantity 且 SKU 上架才更新，影响行数=0 返回 false（库存不足）
     */
    boolean deductStock(Long skuId, Integer quantity);

    /**
     * 恢复库存（取消订单 / 退款时调用）
     */
    boolean restoreStock(Long skuId, Integer quantity);
}
