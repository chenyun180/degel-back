package com.degel.app.vo;

import lombok.Data;

import java.util.List;

/**
 * 秒杀场次 C 端出参。
 * 时间出 epoch ms（小程序端 new Date(ms) 直接可用，避免字符串时区歧义）；
 * status 由 BFF 按当前时间现算：0=未开始 1=进行中 2=已结束。
 * remaining/percent 为实时数据，不进静态缓存（缓存命中后由 BFF 重新填充）。
 */
@Data
public class SeckillSessionVO {

    private String id;
    private String name;
    /** 开始时间（epoch ms） */
    private Long startAt;
    /** 结束时间（epoch ms） */
    private Long endAt;
    /** 0=未开始 1=进行中 2=已结束（现算） */
    private Integer status;
    private Integer sort;
    private List<SeckillProductVO> products;
}
