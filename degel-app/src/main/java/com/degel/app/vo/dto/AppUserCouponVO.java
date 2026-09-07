package com.degel.app.vo.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户券出参（镜像 marketing UserCouponVO 的 C 端字段）
 */
@Data
public class AppUserCouponVO {

    /** 雪花 id，转字符串防 JS 精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;
    private Integer status;
    private String couponName;
    private Integer discountType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountValue;
    /** 本单可优惠金额（usable 场景） */
    private BigDecimal discountAmount;
    private LocalDateTime receiveTime;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;
}
