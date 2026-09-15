package com.degel.marketing.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 轮播图新增/编辑入参（id 空=新增，非空=编辑）。
 */
@Data
public class BannerCreateVo {

    /** 空=新增（MP 自动雪花）；非空=编辑 */
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字符")
    private String title;

    @NotBlank(message = "图片不能为空")
    @Size(max = 255, message = "图片地址过长")
    private String image;

    /** 0=无跳转 1=内部页面 2=商品详情 3=外部链接 */
    @NotNull(message = "跳转类型不能为空")
    private Integer linkType;

    /** linkType>0 必填；=2 必须纯数字 spuId；=3 必须 https:// 前缀（service 校验） */
    @Size(max = 255, message = "跳转值过长")
    private String linkValue;

    @NotNull(message = "排序不能为空")
    private Integer sort;

    /** 生效时间（NULL=立即） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 失效时间（NULL=永久） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
