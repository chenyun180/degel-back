package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分流水（mk_points_log）——积分变动的唯一事实源。
 * type：earn 下单获得 / redeem 抵扣落定 / redeem_return 抵扣退回 /
 *       earn_reclaim 获得回收 / checkin 签到 / freeze 下单冻结 / unfreeze 冻结回补
 */
@Data
@TableName("mk_points_log")
public class MkPointsLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    /** 正=入账 负=出账 */
    private Integer points;

    /** 关联订单（签到为空） */
    private Long orderId;

    private String orderNo;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
