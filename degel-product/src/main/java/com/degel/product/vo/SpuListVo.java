package com.degel.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SpuListVo {

    private Long id;
    private Long shopId;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String mainImage;
    private Integer auditStatus;
    private String rejectReason;
    private Integer status;
    private Integer saleCount;
    private BigDecimal minPrice;
    private Integer totalStock;
    private LocalDateTime createTime;
}
