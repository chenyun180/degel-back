package com.degel.app.vo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单锁券请求（镜像 marketing LockReqVO，Feign 序列化用）
 */
@Data
public class CouponLockReqVO {

    private Long userCouponId;
    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Long shopId;
}
