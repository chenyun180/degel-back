package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家提现单。审核通过 = 模拟打款完成（真实打款渠道接入前，凭证=本单 status=1+pay_time + 流水 biz_type=2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_withdraw")
public class SettlementWithdraw extends BaseEntity {

    private String withdrawNo;
    private Long shopId;
    private BigDecimal amount;
    /** 0=待审核 1=已通过(已模拟打款) 2=已驳回 */
    private Integer status;
    private String applyRemark;
    private String auditRemark;
    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
}
