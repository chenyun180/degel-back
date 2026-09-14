package com.degel.order.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 创建评价（degel-app 经 Feign 调用；userId 由 BFF 从登录态注入，不可信客户端传参）
 */
@Data
public class ReviewCreateInnerVo {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "订单明细ID不能为空")
    private Long orderItemId;

    /** 评价人（BFF 注入） */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "星级不能为空")
    @Min(value = 1, message = "星级最低 1 星")
    @Max(value = 5, message = "星级最高 5 星")
    private Integer star;

    @Size(max = 500, message = "评价内容最长 500 字")
    private String content;
}
