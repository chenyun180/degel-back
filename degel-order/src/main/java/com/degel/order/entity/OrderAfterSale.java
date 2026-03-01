package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_after_sale")
public class OrderAfterSale extends BaseEntity {

    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long shopId;
    private Integer type;
    private Integer status;
    private String reason;
    private BigDecimal refundAmount;
    private String expressCompany;
    private String expressNo;
    private String merchantRemark;
}
