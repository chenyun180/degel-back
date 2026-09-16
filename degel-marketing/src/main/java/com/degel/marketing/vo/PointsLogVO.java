package com.degel.marketing.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分明细条目（C 端我的积分页） */
@Data
public class PointsLogVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 类型：earn/redeem/redeem_return/earn_reclaim/checkin/freeze/unfreeze */
    private String type;

    /** 正=入账 负=出账 */
    private Integer points;

    private Long orderId;

    private String orderNo;

    private String remark;

    private LocalDateTime createTime;
}
