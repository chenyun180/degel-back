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
    /** 订单类型：0=普通（默认） 1=秒杀 */
    private Integer orderType;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Long couponId;
    private BigDecimal platformSubsidy;
    private BigDecimal shopSubsidy;
    /** 积分抵扣：本单使用的积分数（app 侧已冻结） */
    private Integer pointsUsed;
    /** 积分抵扣：抵扣金额（已并入 discountAmount 口径） */
    private BigDecimal pointsDeduct;
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
        private BigDecimal couponDiscount;
    }
}
