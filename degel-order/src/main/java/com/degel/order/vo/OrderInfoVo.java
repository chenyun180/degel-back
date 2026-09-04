package com.degel.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端订单视图（与 degel-app OrderInfoVO 字段对齐，经 /inner/order/** 返回）
 *
 */
@Data
public class OrderInfoVo {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shipTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private String expressCompany;
    private String expressNo;
    private Long payLogId;
    private String cancelReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private List<OrderItemVo> items;

    @Data
    public static class OrderItemVo {
        private Long spuId;
        private Long skuId;
        private String spuName;
        private String skuSpec;
        private String skuImage;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal totalAmount;
    }
}
