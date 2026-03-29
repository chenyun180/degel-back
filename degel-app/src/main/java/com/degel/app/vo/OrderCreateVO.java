package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单响应 VO
 */
@Data
public class OrderCreateVO {

    /** 订单ID */
    private Long orderId;
    /** 订单编号 */
    private String orderNo;
    /** 实付金额 */
    private BigDecimal payAmount;
    /** 自动取消时间（前端倒计时） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;
}
