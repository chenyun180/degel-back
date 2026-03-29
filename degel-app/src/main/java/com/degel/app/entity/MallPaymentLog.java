package com.degel.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付/退款流水实体
 * 对应数据库表 mall_payment_log（degel_app 库）
 *
 * 注意：此表无 update_time / del_flag，不继承 BaseEntity
 */
@Data
@TableName("mall_payment_log")
public class MallPaymentLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** C端用户ID */
    private Long userId;

    /** 订单ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 金额 */
    private BigDecimal amount;

    /**
     * 方向：pay=支付 / refund=退款
     */
    private String direction;

    /**
     * 状态：0=成功 1=失败
     */
    private Integer status;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
