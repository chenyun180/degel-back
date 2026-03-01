package com.degel.product.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardOverviewVo {
    private BigDecimal todayGmv;
    private Integer todayOrderCount;
    private Integer todayVisitorCount;
    private BigDecimal yesterdayGmv;
    private Integer yesterdayOrderCount;
}
