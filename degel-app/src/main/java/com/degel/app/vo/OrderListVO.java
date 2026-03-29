package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表 VO
 */
@Data
public class OrderListVO {

    /** 订单ID */
    private Long orderId;
    /** 订单编号 */
    private String orderNo;
    /**
     * 订单状态：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款
     */
    private Integer status;
    /** 订单状态描述 */
    private String statusDesc;
    /** 实付金额 */
    private BigDecimal payAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;

    /** 第一个商品信息（列表展示用） */
    private OrderItemBriefVO firstItem;

    /** 订单中SKU种类数 */
    private Integer itemCount;
}
