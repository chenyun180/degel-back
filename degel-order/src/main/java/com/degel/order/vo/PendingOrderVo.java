package com.degel.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待评价订单（status=3 且存在未评价明细；items 仅含未评价明细）
 */
@Data
public class PendingOrderVo {

    private Long orderId;
    private String orderNo;
    private BigDecimal payAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 未评价明细数 */
    private Integer pendingCount;

    /** 未评价明细（按明细维度评价） */
    private List<PendingItemVo> items;

    @Data
    public static class PendingItemVo {
        private Long orderItemId;
        private Long spuId;
        private String spuName;
        private String skuSpec;
        private String skuImage;
    }
}
