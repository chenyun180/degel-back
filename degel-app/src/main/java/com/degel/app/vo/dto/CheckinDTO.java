package com.degel.app.vo.dto;

import lombok.Data;

import java.util.List;

/** 签到状态/结果（与 marketing CheckinVO 对齐的 BFF 侧副本） */
@Data
public class CheckinDTO {

    private Boolean checked;
    private Integer points;
    private Integer continuous;
    private Integer nextPoints;
    private Integer balance;
    /** 最近签到日期 yyyy-MM-dd（签到页日历条） */
    private List<String> recentDates;
}
