package com.degel.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderListVo {

    private Long id;
    private String orderNo;
    private Long shopId;
    private BigDecimal payAmount;
    private Integer status;
    private String firstItemName;
    private String firstItemImage;
    private Integer itemCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
}
