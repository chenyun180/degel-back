package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_info")
public class OrderInfo extends BaseEntity {

    private String orderNo;
    private Long userId;
    private Long shopId;
    /** 订单类型：0=普通 1=秒杀（order_type，默认 0） */
    private Integer orderType;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    /** 使用的用户券id（mk_user_coupon.id），未用券为 null */
    private Long couponId;
    /** 平台补贴（平台承担部分，佣金基数=券后实收的行业惯例预留） */
    private BigDecimal platformSubsidy;
    /** 店铺补贴（店铺承担部分） */
    private BigDecimal shopSubsidy;
    /** 积分抵扣：本单使用的积分数（app 侧下单时已冻结） */
    private Integer pointsUsed;
    /** 积分抵扣：抵扣金额（已并入 discountAmount 口径） */
    private BigDecimal pointsDeduct;
    /** 本单获得积分数（确认收货后由 app 回写） */
    private Integer pointsEarned;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shipTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    private String cancelReason;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private String expressCompany;
    private String expressNo;
    private Long payLogId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;
}
