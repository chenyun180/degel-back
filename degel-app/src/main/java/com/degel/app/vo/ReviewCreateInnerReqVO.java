package com.degel.app.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 评价创建内部请求（字段与 degel-order ReviewCreateInnerVo 对齐；userId 由 BFF 注入）
 */
@Data
public class ReviewCreateInnerReqVO {

    @NotNull
    private Long orderId;

    @NotNull
    private Long orderItemId;

    @NotNull
    private Long userId;

    private Integer star;

    private String content;
}
