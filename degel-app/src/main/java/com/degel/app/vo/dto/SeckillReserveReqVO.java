package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 秒杀抢购（预扣）请求 VO
 */
@Data
public class SeckillReserveReqVO {

    @NotNull(message = "场次不能为空")
    private Long sessionId;

    @NotNull(message = "SKU 不能为空")
    private Long skuId;
}
