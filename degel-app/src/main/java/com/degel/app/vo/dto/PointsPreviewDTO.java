package com.degel.app.vo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 积分抵扣试算（与 marketing PointsPreviewVO 对齐的 BFF 侧副本） */
@Data
public class PointsPreviewDTO {

    private Integer available;
    private Integer maxRedeemPoints;
    private BigDecimal maxDeductAmount;
    private String rule;
}
