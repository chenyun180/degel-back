package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情 VO
 */
@Data
public class OrderDetailVO {

    // ======== 基本信息 ========
    private Long orderId;
    private String orderNo;
    /** 订单类型：0普通 1秒杀 */
    private Integer orderType;
    /** 0待付款 1待发货 2待收货 3已完成 4已取消 5已退款 */
    private Integer status;
    private String statusDesc;
    private String remark;
    private String cancelReason;
    private Long payLogId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shipTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    // ======== 金额 ========
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    /** 积分抵扣：本单使用的积分数 */
    private Integer pointsUsed;
    /** 积分抵扣：抵扣金额（已并入 discountAmount 口径） */
    private BigDecimal pointsDeduct;
    /** 本单获得积分数（确认收货后回写） */
    private Integer pointsEarned;

    // ======== 收货信息 ========
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    // ======== 物流 ========
    private String expressCompany;
    private String expressNo;

    // ======== 商品明细 ========
    private List<OrderItemVO> items;
}
