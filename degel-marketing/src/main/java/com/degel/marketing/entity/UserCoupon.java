package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券（mk_user_coupon）。
 *
 * 状态机：
 * 0 未用 --下单锁券--> 1 已锁定 --支付成功--> 2 已核销
 * 1 已锁定 --取消/下单失败补偿--> 0 未用
 * 2 已核销 --整单退款--> 4 已退回（已过期则 5 作废）
 * 0 未用 --过期--> 3 已过期
 * 4 已退回 --再次下单--> 走 0 的完整路径
 *
 * platformAmount/shopAmount 为领取时快照：模板后续调整不影响已发券的记账口径。
 */
@Data
@TableName("mk_user_coupon")
public class UserCoupon {

    /** MP ASSIGN_ID 雪花 */
    private Long id;

    private Long couponId;
    private Long userId;

    private Integer status;

    /** 核销时的订单id（confirm 回填；锁券时只有预生成 orderNo） */
    private Long orderId;

    /** 锁券时的预生成订单号（lock/unlock 定位） */
    private String orderNo;

    private BigDecimal platformAmount;
    private BigDecimal shopAmount;

    private LocalDateTime receiveTime;

    /** 按 valid_type 计算落库，惰性+定时双重过期 */
    private LocalDateTime expireTime;

    private LocalDateTime useTime;

    /** 最近一次锁券时间（60 分钟未 confirm 自动释放兜底） */
    private LocalDateTime lockTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
