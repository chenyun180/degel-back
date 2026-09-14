package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 商家回复评价（店铺工作台）
 */
@Data
public class ReviewReplyVo {

    @NotNull(message = "评价ID不能为空")
    private Long reviewId;

    @NotNull(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容最长 500 字")
    private String reply;
}
