package com.degel.marketing.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户券出参（我的券 / 可用券）。
 */
@Data
public class UserCouponVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    /** 0=未用 1=已锁定 2=已核销 3=已过期 4=已退回 5=已作废 */
    private Integer status;

    private String couponName;
    private Integer discountType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountValue;

    /** 本单可优惠金额（usable 场景填；mine 场景 null） */
    private BigDecimal discountAmount;

    private BigDecimal platformAmount;
    private BigDecimal shopAmount;

    private LocalDateTime receiveTime;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;
}
