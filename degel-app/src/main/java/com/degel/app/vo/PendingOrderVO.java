package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端待评价订单（订单列表"待评价"Tab 用）
 */
@Data
public class PendingOrderVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal payAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 未评价明细数 */
    private Integer pendingCount;

    private List<PendingItemVO> items;

    @Data
    public static class PendingItemVO {
        private Long orderItemId;
        private Long spuId;
        private String spuName;
        private String skuSpec;
        private String skuImage;
    }
}
