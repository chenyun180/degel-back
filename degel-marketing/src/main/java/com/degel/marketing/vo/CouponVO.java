package com.degel.marketing.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板出参（管理端列表 / C 端可领列表共用，字段裁剪由 service 决定）。
 */
@Data
public class CouponVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    /** 1=平台券 2=店铺券 3=分摊券 */
    private Integer funderType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long shopId;

    /** 1=满减 2=折扣 3=无门槛 */
    private Integer discountType;

    private BigDecimal thresholdAmount;
    private BigDecimal discountValue;

    private Integer totalCount;
    private Integer issuedCount;
    private Integer perUserLimit;

    private LocalDateTime receiveStart;
    private LocalDateTime receiveEnd;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer validType;

    /** 0=草稿 1=进行中 2=停发 */
    private Integer status;

    /** 0=草稿 1=待审核 2=已通过 3=已驳回 */
    private Integer auditStatus;

    /** 审核驳回理由 */
    private String rejectReason;

    /** 平台承担金额（快照比例基数） */
    private BigDecimal platformAmount;

    /** 店铺承担金额 */
    private BigDecimal shopAmount;

    private LocalDateTime createTime;
}
