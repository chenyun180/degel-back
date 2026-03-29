package com.degel.app.vo.dto;

import lombok.Data;

/**
 * 订单状态更新 VO（传给 degel-order）
 */
@Data
public class OrderStatusUpdateVO {

    /** 更新后的状态 */
    private Integer status;
    /** 支付时间（status=1时设置）*/
    private java.time.LocalDateTime payTime;
    /** 支付流水ID（status=1时设置）*/
    private Long payLogId;
    /** 确认收货时间（status=3时设置）*/
    private java.time.LocalDateTime receiveTime;
    /** 取消时间（status=4时设置）*/
    private java.time.LocalDateTime cancelTime;
    /** 取消原因（status=4时设置）*/
    private String cancelReason;
}
