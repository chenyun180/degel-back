package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日签到（mk_checkin）。uk_user_date 唯一索引天然防重（DB 兜底，Redis SETNX 是第一道）。
 */
@Data
@TableName("mk_checkin")
public class MkCheckin implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate checkinDate;

    /** 当日发放积分数（连续递增，记录实际值便于对账） */
    private Integer points;

    private LocalDateTime createTime;
}
