package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 秒杀下单请求 VO（两段式第二段：凭 reserve 返回的 token 下单）
 */
@Data
public class SeckillOrderReqVO {

    @NotBlank(message = "抢购资格不能为空")
    private String token;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 使用积分抵现（默认 false；单店单商品，抵扣额= min(余额,20%上限)） */
    private Boolean usePoints;
}
