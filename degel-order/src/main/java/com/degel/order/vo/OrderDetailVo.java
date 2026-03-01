package com.degel.order.vo;

import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.OrderInfo;
import com.degel.order.entity.OrderItem;
import lombok.Data;

import java.util.List;

@Data
public class OrderDetailVo {

    private OrderInfo order;
    private List<OrderItem> items;
    private List<OrderAfterSale> afterSales;
}
