package com.degel.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /** 近 N 天各 SKU 销量（已支付口径，product 滞销预警用） */
    @Select("SELECT i.sku_id AS skuId, SUM(i.quantity) AS cnt FROM order_item i "
            + "JOIN order_info o ON o.id = i.order_id AND o.del_flag = 0 AND o.status IN (1, 2, 3, 5) "
            + "WHERE i.del_flag = 0 AND o.pay_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "GROUP BY i.sku_id")
    List<Map<String, Object>> sumSkuSalesRecent(int days);
}
