package com.degel.marketing.vo;

import com.degel.marketing.entity.Banner;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图出参（平台管理端分页 / C 端生效列表共用；不含 delFlag）。
 */
@Data
public class BannerVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String image;

    /** 0=无跳转 1=内部页面 2=商品详情 3=外部链接 */
    private Integer linkType;

    private String linkValue;

    private Integer sort;

    /** 0=下架 1=上架 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 手写转换（不用 BeanUtils）：出参字段与实体独立演进，且天然裁掉 delFlag */
    public static BannerVo from(Banner banner) {
        BannerVo vo = new BannerVo();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setImage(banner.getImage());
        vo.setLinkType(banner.getLinkType());
        vo.setLinkValue(banner.getLinkValue());
        vo.setSort(banner.getSort());
        vo.setStatus(banner.getStatus());
        vo.setStartTime(banner.getStartTime());
        vo.setEndTime(banner.getEndTime());
        vo.setCreateTime(banner.getCreateTime());
        return vo;
    }
}
