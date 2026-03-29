package com.degel.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_cart")
public class MallCart extends BaseEntity {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 数量
     */
    private Integer quantity;
}
