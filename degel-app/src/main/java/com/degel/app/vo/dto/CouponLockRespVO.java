package com.degel.app.vo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 锁券响应（镜像 marketing LockRespVO）：本单优惠额与出资拆分
 */
@Data
public class CouponLockRespVO {

    private BigDecimal discountAmount;
    private BigDecimal platformAmount;
    private BigDecimal shopAmount;
    private String couponName;
}
