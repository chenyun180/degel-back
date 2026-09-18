package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.SettlementOrder;
import com.degel.order.vo.PlatformSettlementOverviewVo;
import com.degel.order.vo.SettlementConfigVo;
import com.degel.order.vo.ShopSettlementAccountVo;

import java.math.BigDecimal;

/**
 * 店铺分账清结算（模拟支付版）：T+7 自动结算、退款扣回、佣金配置、店铺端查询
 */
public interface ISettlementService {

    /** 结算入账 CAS：0→1，失败返回 false（退款钩子已抢先作废该明细） */
    boolean trySettleOne(SettlementOrder detail);

    /** T+7 结算定时任务（每小时 30 分）：扫确认收货满 7 天的已完成订单逐单结算 */
    void settleDueOrders();

    /**
     * 退款完成钩子（best-effort，由售后终态调用）：
     * 待入账明细直接作废；已入账明细扣回余额（允许负数）
     */
    void onRefundCompleted(OrderAfterSale afterSale);

    /** 兜底对账（每 30 分钟）：补做钩子失败遗漏的退款扣回 */
    void reconcileRefundDeduct();

    ShopSettlementAccountVo getShopAccount(Long shopId);

    IPage<SettlementOrder> pageDetail(IPage<SettlementOrder> page, Long shopId, Integer status);

    /** 读全局佣金比例（%），配置缺失时默认 5 并落库 */
    BigDecimal readCommissionRate();

    /** 读售后窗口（天）：确认收货后 N 天内可申请售后；配置缺失时默认 7（法定七天无理由底线） */
    int readAftersaleDays();

    SettlementConfigVo getConfig();

    void updateConfig(BigDecimal commissionRate);

    /** 售后窗口（天）配置，1~90 */
    void updateAftersaleDays(Integer days);

    /** 平台资金总览（纯聚合；Feign 汇总失败时用户支付净额记 0 并 log，不阻塞页面） */
    PlatformSettlementOverviewVo getPlatformOverview();
}
