package com.degel.marketing.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 签到状态/结果 */
@Data
public class CheckinVO {

    /** 今日是否已签 */
    private Boolean checked;

    /** 今日（或刚才签到）发放积分数 */
    private Integer points;

    /** 截至今日连续签到天数 */
    private Integer continuous;

    /** 明天签到可得积分数（递增预告） */
    private Integer nextPoints;

    /** 签到后余额（未签时为 null） */
    private Integer balance;

    /** 最近签到日期（签到页日历条用，升序） */
    private List<LocalDate> recentDates;
}
