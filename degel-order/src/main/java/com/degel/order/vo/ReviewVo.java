package com.degel.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价展示 VO（C 端商品页/我的评价、店铺工作台共用）
 */
@Data
public class ReviewVo {

    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpec;
    private Integer star;
    private String content;
    private String reply;
    private Long shopId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
