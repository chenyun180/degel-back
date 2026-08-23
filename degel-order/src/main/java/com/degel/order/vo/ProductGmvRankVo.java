package com.degel.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 畅销商品排行项（按订单明细聚合，商品名取下单快照）
 */
@Data
public class ProductGmvRankVo {

    private Long spuId;

    /** 商品名快照（来自 order_item.spu_name） */
    private String spuName;

    /** 所属店铺ID（店铺名由前端经 admin 服务映射） */
    private Long shopId;

    /** 销量合计 */
    private Integer quantity;

    /** 销售额合计 */
    private BigDecimal amount;
}
