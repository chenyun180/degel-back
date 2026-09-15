package com.degel.marketing.vo;

import com.degel.marketing.entity.SeckillProduct;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 场次秒杀商品出参（管理端分页 / C 端 current 附带 / C 端 detail 共用；不含 delFlag）。
 */
@Data
public class SeckillProductVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀库存 */
    private Integer seckillStock;

    /** 每人限购 */
    private Integer perLimit;

    private Integer sort;

    /** 手写转换（不用 BeanUtils）：出参字段与实体独立演进，且天然裁掉 delFlag */
    public static SeckillProductVo from(SeckillProduct product) {
        SeckillProductVo vo = new SeckillProductVo();
        vo.setId(product.getId());
        vo.setSessionId(product.getSessionId());
        vo.setSpuId(product.getSpuId());
        vo.setSkuId(product.getSkuId());
        vo.setSeckillPrice(product.getSeckillPrice());
        vo.setSeckillStock(product.getSeckillStock());
        vo.setPerLimit(product.getPerLimit());
        vo.setSort(product.getSort());
        return vo;
    }
}
