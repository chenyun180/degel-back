package com.degel.app.vo;

import lombok.Data;

/**
 * C 端轮播图 VO（image 已拼完整 URL）
 */
@Data
public class BannerVO {

    /** 雪花 id 字符串化，防 JS 精度丢失（marketing 侧 Long 经 ToStringSerializer 已序列化为字符串） */
    private String id;

    /** 完整图片 URL（file-base-url + /file/view/ + objectKey） */
    private String image;

    /** 0=无跳转 1=内部页面 2=商品详情 3=外部链接 */
    private Integer linkType;

    private String linkValue;
}
