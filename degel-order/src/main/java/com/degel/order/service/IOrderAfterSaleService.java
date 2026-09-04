package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.vo.AfterSaleHandleVo;
import com.degel.order.vo.AfterSaleInfoVo;
import com.degel.order.vo.inner.AfterSaleCreateInnerVo;

public interface IOrderAfterSaleService extends IService<OrderAfterSale> {

    IPage<OrderAfterSale> pageAfterSales(IPage<OrderAfterSale> page, Long shopId, Integer status, Integer type);

    void handle(AfterSaleHandleVo vo, Long shopId);

    void confirmReceive(Long afterSaleId, Long shopId);

    // ==================== C 端内部接口（degel-app 经 Feign 调用） ====================

    /**
     * 创建售后单（C 端）
     */
    Long createInnerAfterSale(AfterSaleCreateInnerVo vo);

    /**
     * 按 userId 分页查售后单（C 端，带 orderNo 关联）
     */
    IPage<AfterSaleInfoVo> pageInnerAfterSales(Long userId, Integer status, Integer page, Integer pageSize);

    /**
     * 是否存在进行中的售后单（status IN 0,1）
     */
    boolean existsActiveAfterSale(Long orderId, Long userId);

    /**
     * 按 ID 精确查售后单（C 端）
     */
    AfterSaleInfoVo getInnerAfterSale(Long id);
}
