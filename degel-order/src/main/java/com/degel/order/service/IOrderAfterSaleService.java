package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.vo.AfterSaleArbitrateVo;
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

    // ==================== 平台仲裁（PRD 6.3） ====================

    /**
     * 用户申请平台介入（C 端）：CAS 5→6，仅"已拒绝"可申请（终态 3/7 防循环）
     */
    void applyArbitrateInner(Long afterSaleId, Long userId);

    /**
     * 平台仲裁列表（全店铺，status 可筛：6=待仲裁）
     */
    IPage<AfterSaleInfoVo> pageArbitrations(IPage<OrderAfterSale> page, Integer status);

    /**
     * 平台判定：supportUser=true → 6→3 走完整退款链路；false → 6→7 维持拒绝（终态）
     */
    void arbitrate(AfterSaleArbitrateVo vo, String operator);
}
