package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场次秒杀商品（mk_seckill_product）。
 *
 * 对齐 Banner 先例：裸 Long id + MP assign_id 雪花，
 * createTime/updateTime/delFlag 由 DB DEFAULT / ON UPDATE 兜底。
 */
@Data
@TableName("mk_seckill_product")
public class SeckillProduct {

    /** MP ASSIGN_ID 雪花；出参必须 @JsonSerialize(ToStringSerializer) 防 JS 精度丢失 */
    private Long id;

    /** 所属场次（mk_seckill_session.id） */
    private Long sessionId;

    /** 商品 spuId（展示冗余，C 端跳详情用） */
    private Long spuId;

    /** SKU id（下单/扣库存定位到 SKU） */
    private Long skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀库存 */
    private Integer seckillStock;

    /** 每人限购 */
    private Integer perLimit;

    /** 数字小靠前 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
