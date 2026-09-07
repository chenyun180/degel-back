package com.degel.marketing.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券创建入参（三期：平台券1/店铺券2/分摊券3，满减/无门槛/折扣）。
 * funderType/shopId/platformAmount/shopAmount 仅平台端创建分摊券时使用；
 * 店铺端创建恒为 funderType=2（controller 强制）。
 */
@Data
public class CouponCreateVo {

    @NotBlank(message = "券名不能为空")
    private String name;

    /** 1=平台券（默认） 2=店铺券（店铺端） 3=分摊券（平台端） */
    private Integer funderType;

    /** 分摊券必填：合作的店铺 */
    private Long shopId;

    /** 分摊券必填：平台承担（金额，满减分摊=优惠额的一部分；折扣分摊=预期减免额的一部分，仅定比例） */
    private BigDecimal platformAmount;

    /** 分摊券必填：店铺承担，与 platformAmount 共同定出资比例 */
    private BigDecimal shopAmount;

    @NotNull(message = "优惠类型不能为空")
    private Integer discountType;

    /** 使用门槛（满X元），无门槛传 0 */
    @NotNull(message = "使用门槛不能为空")
    private BigDecimal thresholdAmount;

    /** 满减=减免额；无门槛=减免额 */
    @NotNull(message = "优惠金额不能为空")
    private BigDecimal discountValue;

    @NotNull(message = "发放总量不能为空")
    @Positive(message = "发放总量必须大于0")
    private Integer totalCount;

    @NotNull(message = "每人限领不能为空")
    @Positive(message = "每人限领必须大于0")
    private Integer perUserLimit;

    @NotNull(message = "可领开始时间不能为空")
    private LocalDateTime receiveStart;

    @NotNull(message = "可领截止时间不能为空")
    private LocalDateTime receiveEnd;

    @NotNull(message = "有效期类型不能为空")
    private Integer validType;

    /** valid_type=1 用券起止 */
    private LocalDateTime validStart;
    private LocalDateTime validEnd;

    /** valid_type=2 领取后N天有效 */
    private Integer validDays;
}
