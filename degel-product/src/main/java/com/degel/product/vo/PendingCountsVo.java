package com.degel.product.vo;

import lombok.Data;

@Data
public class PendingCountsVo {
    private Integer pendingShipment;
    private Integer pendingAfterSale;
    private Integer stockWarningCount;
    private Integer pendingAudit;
}
