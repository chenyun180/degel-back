package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按日流水（折线图数据点，无订单的日期补零）
 */
@Data
public class DailyGmvVo {

    /** yyyy-MM-dd */
    private String date;

    private BigDecimal gmv;

    private Integer orderCount;

    public DailyGmvVo() {
    }

    public DailyGmvVo(String date, BigDecimal gmv, Integer orderCount) {
        this.date = date;
        this.gmv = gmv;
        this.orderCount = orderCount;
    }
}
