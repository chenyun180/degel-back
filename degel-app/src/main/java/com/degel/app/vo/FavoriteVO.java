package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端收藏列表 VO（商品信息实时回查，下架/删除商品 invalid=true 仍展示）
 */
@Data
public class FavoriteVO {

    private Long spuId;

    private String spuName;

    private String mainImage;

    private BigDecimal minPrice;

    private Integer saleCount;

    /** 商品已下架/删除/未过审时 true（前端置灰，仍可查看） */
    private Boolean invalid;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime favoriteTime;
}
