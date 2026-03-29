package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单信息 VO（Feign 从 degel-order 获取）
 */
@Data
public class OrderInfoVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    /** 0待付款 1待发货 2待收货 3已完成 4已取消 5已退款 */
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

    private List<OrderItemInfoVO> items;

    @Data
    public static class OrderItemInfoVO {
        private Long id;
        private Long orderId;
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
