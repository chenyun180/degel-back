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

    /** 备注 */
    private String remark;
}
