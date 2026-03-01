package com.degel.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_spu")
public class ProductSpu extends BaseEntity {

    private Long shopId;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String mainImage;
    private String images;
    private String detailContent;
    private Integer saleCount;
    private String keyword;
    private Integer viewCount;
    private Integer auditStatus;
    private String rejectReason;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Integer status;
}
