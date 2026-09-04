package com.degel.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端售后单视图（与 degel-app AfterSaleInfoVO 字段对齐）
 * orderNo 来自 order_info 关联查询
 */
@Data
public class AfterSaleInfoVo {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Integer type;
    private Integer status;
    private String reason;
    private BigDecimal refundAmount;
    private String merchantRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
