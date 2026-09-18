package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 结算候选（Mapper 候选查询结果，金额原样取自 order_info，基数/佣金在 service 内计算） */
@Data
public class SettleCandidateVo {

    private Long orderId;
    private String orderNo;
    private Long shopId;
    private Long userId;
    private BigDecimal payAmount;
    private BigDecimal platformSubsidy;
    private BigDecimal pointsDeduct;
}
