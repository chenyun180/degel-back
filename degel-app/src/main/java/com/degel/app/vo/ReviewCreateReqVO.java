package com.degel.app.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * C 端创建评价请求
 */
@Data
public class ReviewCreateReqVO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "订单明细ID不能为空")
    private Long orderItemId;

    @NotNull(message = "星级不能为空")
    @Min(value = 1, message = "星级最低 1 星")
    @Max(value = 5, message = "星级最高 5 星")
    private Integer star;

    @Size(max = 500, message = "评价内容最长 500 字")
    private String content;
}
