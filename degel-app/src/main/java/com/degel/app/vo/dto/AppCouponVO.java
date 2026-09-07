package com.degel.app.vo.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板出参（镜像 marketing CouponVO 的 C 端字段）
 */
@Data
public class AppCouponVO {

    /** 雪花 id，转字符串防 JS 精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    /** 1=平台券 2=店铺券（展示"店铺专享"标记用） */
    private Integer funderType;
    private Integer discountType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountValue;
    private Integer totalCount;
    private Integer issuedCount;
    private Integer perUserLimit;
    private LocalDateTime receiveStart;
    private LocalDateTime receiveEnd;
    private Integer validType;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer status;
}
