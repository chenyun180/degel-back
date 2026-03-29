package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发起支付响应 VO
 */
@Data
public class PayResultVO {

    /** 支付流水ID */
    private Long payLogId;
    /** 订单编号 */
    private String orderNo;
    /** 支付金额 */
    private BigDecimal amount;
    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
}
