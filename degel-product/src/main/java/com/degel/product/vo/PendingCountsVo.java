package com.degel.product.vo;

import lombok.Data;

/**
 * 店铺工作台待办数（商品域）。待发货/待处理售后属订单域，
 * 由 degel-order /shop/dashboard/overview 出数（原此处恒 0 的假字段已删）。
 */
@Data
public class PendingCountsVo {
    private Integer stockWarningCount;
    private Integer pendingAudit;
}
