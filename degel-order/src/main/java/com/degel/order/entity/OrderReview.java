package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单评价（订单已完成 status=3 后可评，每个 order_item 仅可评一次）
 * spu_name/sku_spec 为下单时快照，商品后续改名不影响评价展示
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_review")
public class OrderReview extends BaseEntity {

    private Long orderId;
    private Long orderItemId;
    private Long userId;
    /** 下单时冗余，店铺回复/店铺维度过滤用 */
    private Long shopId;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpec;
    /** 星级 1-5 */
    private Integer star;
    private String content;
    /** 晒图 URL JSON 数组（一期未开放上传，预留） */
    private String images;
    private String reply;
    private LocalDateTime replyTime;
    /** 0=已发布 1=已隐藏（预留平台屏蔽） */
    private Integer status;
}
