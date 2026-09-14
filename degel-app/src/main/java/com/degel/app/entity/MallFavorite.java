package com.degel.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品收藏实体（取消收藏为物理删除，del_flag 仅作预留对齐表结构）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_favorite")
public class MallFavorite extends BaseEntity {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * SPU ID
     */
    private Long spuId;
}
