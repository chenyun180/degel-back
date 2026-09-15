package com.degel.app.vo;

import lombok.Data;

/**
 * 秒杀抢购（预扣）成功出参：token 用于两段式第二段下单
 */
@Data
public class SeckillReserveVO {

    /** 抢购资格 token（90 秒有效，过期需重新抢购） */
    private String token;
    /** 资格有效秒数 */
    private Integer expireSeconds;
}
