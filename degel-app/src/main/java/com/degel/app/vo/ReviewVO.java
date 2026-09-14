package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端评价展示 VO
 */
@Data
public class ReviewVO {

    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpec;
    /** SKU 图片快照（pageMine 时按 skuId 批量回查 degel-product 填充，查不到为 null） */
    private String skuImage;
    private Integer star;
    private String content;
    private String reply;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
