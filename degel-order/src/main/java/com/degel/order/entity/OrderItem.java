package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpec;
    private String skuImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
