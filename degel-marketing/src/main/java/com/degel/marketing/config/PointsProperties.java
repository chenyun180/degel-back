package com.degel.marketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 积分规则配置（degel.marketing.points.*，环境变量可覆盖）。
 * 见 doc/积分系统设计.md。
 */
@Data
@Component
@ConfigurationProperties(prefix = "degel.marketing.points")
public class PointsProperties {

    /** 总开关（false 时下单不抵扣、签到不发放、得分不发放——灰度/应急一键停） */
    private boolean enabled = true;

    /** 下单获得：实付 1 元 = 1 分（确认收货后发放） */
    private int earnRate = 1;

    /** 抵现换算：100 分 = 1 元（仅支持整百抵扣，换算成金额时无除不尽问题） */
    private int redeemRate = 100;

    /** 单笔订单积分最多抵应付金额的百分比 */
    private int redeemMaxPercent = 20;

    /** 签到：第 N 天得 min(base+(N-1)*inc, cap)，断签重置 */
    private int checkinBase = 5;
    private int checkinInc = 1;
    private int checkinCap = 10;
}
