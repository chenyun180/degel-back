package com.degel.order.vo.inner;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端订单状态更新内部请求（与 degel-app OrderStatusUpdateVO 字段对齐）
 * 非空字段才更新；payLogId/cancelReason 当前无对应列，接收但忽略
 */
@Data
public class OrderStatusUpdateInnerVo {

    private Integer status;
    private LocalDateTime payTime;
    private Long payLogId;
    private LocalDateTime receiveTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
}
