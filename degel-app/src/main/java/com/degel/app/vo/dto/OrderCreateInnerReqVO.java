package com.degel.app.vo.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单内部请求 VO（传给 degel-order）
 */
@Data
public class OrderCreateInnerReqVO {

    private Long userId;
    private Long shopId;
    private String orderNo;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal freightAmount;
    private java.math.BigDecimal discountAmount;
    private java.math.BigDecimal payAmount;
    /** 使用的用户券id（mk_user_coupon.id），未用券为 null */
    private Long couponId;
    /** 平台补贴（平台承担部分，订单记账） */
    private java.math.BigDecimal platformSubsidy;
    /** 店铺补贴（店铺承担部分，订单记账） */
    private java.math.BigDecimal shopSubsidy;
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人手机 */
    private String receiverPhone;
    /** 收货人地址（省+市+区+详细） */
    private String receiverAddress;
    /** 备注 */
    private String remark;
    /** 自动取消时间（下单后30分钟）*/
    private java.time.LocalDateTime autoCancelTime;
    /** 订单明细 */
    private List<OrderItemInnerVO> items;

    @Data
    public static class OrderItemInnerVO {
        private Long spuId;
        private Long skuId;
        private String spuName;
        private String skuSpec;
        private String skuImage;
        private java.math.BigDecimal price;
        private Integer quantity;
        private java.math.BigDecimal totalAmount;
        /** 该商品分摊的券优惠额（退款取数依据：item 实付 = totalAmount - couponDiscount） */
        private java.math.BigDecimal couponDiscount;
    }
}
