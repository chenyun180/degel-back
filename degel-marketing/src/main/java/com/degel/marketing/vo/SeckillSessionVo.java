package com.degel.marketing.vo;

import com.degel.marketing.entity.SeckillSession;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀场次出参（管理端分页 / C 端 current 共用；不含 delFlag）。
 */
@Data
public class SeckillSessionVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 0=停用 1=启用 */
    private Integer status;

    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 场次下的秒杀商品（sort 升序）；仅 /inner/seckill/current 填充，管理端分页不填充（输出 null） */
    private List<SeckillProductVo> products;

    /** 手写转换（不用 BeanUtils）：出参字段与实体独立演进，且天然裁掉 delFlag */
    public static SeckillSessionVo from(SeckillSession session) {
        SeckillSessionVo vo = new SeckillSessionVo();
        vo.setId(session.getId());
        vo.setName(session.getName());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());
        vo.setStatus(session.getStatus());
        vo.setSort(session.getSort());
        vo.setCreateTime(session.getCreateTime());
        return vo;
    }
}
