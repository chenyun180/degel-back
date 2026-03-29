package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细 VO（详情页用）
 */
@Data
public class OrderItemVO {

    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpec;
    private String skuImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
