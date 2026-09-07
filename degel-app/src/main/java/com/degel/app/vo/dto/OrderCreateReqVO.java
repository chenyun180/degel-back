package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建订单请求 VO
 * cartIds 和 skuId 二选一：
 *  - 购物车模式：提供 cartIds
 *  - 直购模式：提供 skuId + quantity
 */
@Data
public class OrderCreateReqVO {

    /** 购物车ID列表（购物车模式） */
    private List<Long> cartIds;

    /** SKU ID（直购模式） */
    private Long skuId;

    /** 购买数量（直购模式） */
    @Min(value = 1, message = "购买数量不能小于1")
    private Integer quantity;

    /** 收货地址ID */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /** 用户券id（mk_user_coupon.id，可选；单店兼容口径——挂唯一子单或金额最大子单） */
    private Long couponId;

    /** 每子单独立选券（拆单正式口径：店铺券绑对应店，平台券绑任一子单；与 couponId 二选一，同时传以本字段为准） */
    private List<CouponBinding> couponBindings;

    /** 备注 */
    private String remark;

    /** 子单券绑定 */
    @Data
    public static class CouponBinding {
        /** 子单归属店铺 */
        private Long shopId;
        /** 该子单使用的用户券 */
        private Long couponId;
    }
}
