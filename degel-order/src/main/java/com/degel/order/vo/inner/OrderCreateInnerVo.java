package com.degel.order.vo.inner;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端创建订单内部请求（与 degel-app OrderCreateInnerReqVO 字段对齐）
 */
@Data
public class OrderCreateInnerVo {

    private Long userId;
    private Long shopId;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime autoCancelTime;
    private List<OrderItemInnerVo> items;

    @Data
    public static class OrderItemInnerVo {
        private Long spuId;
        private Long skuId;
        private String spuName;
        private String skuSpec;
        private String skuImage;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal totalAmount;
    }
}
