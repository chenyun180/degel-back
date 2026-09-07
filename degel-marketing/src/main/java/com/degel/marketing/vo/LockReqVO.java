package com.degel.marketing.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 下单锁券入参（degel-app → marketing 内部接口）。
 */
@Data
public class LockReqVO {

    /** mk_user_coupon.id */
    @NotNull(message = "用户券id不能为空")
    private Long userCouponId;

    @NotNull(message = "用户id不能为空")
    private Long userId;

    /** 预生成订单号（orderId 是落库后自增，锁券时只有 orderNo） */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 商品合计（不含运费，券不抵运费） */
    @NotNull(message = "订单金额不能为空")
    @Positive(message = "订单金额必须大于0")
    private BigDecimal totalAmount;

    /** 一期平台券全场通用，shopId 仅预留（店铺券二期校验本店） */
    private Long shopId;
}
