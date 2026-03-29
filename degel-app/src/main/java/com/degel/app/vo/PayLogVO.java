package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水列表 VO
 */
@Data
public class PayLogVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    /** pay=支付 / refund=退款 */
    private String direction;
    /** 0=成功 1=失败 */
    private Integer status;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
