package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项中的简略商品信息
 */
@Data
public class OrderItemBriefVO {

    private String spuName;
    private String skuSpec;
    private String skuImage;
    private BigDecimal price;
    private Integer quantity;
}
