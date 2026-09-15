package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销轮播图（mk_banner）。
 *
 * 不继承 BaseEntity：BaseEntity 的 @TableId(AUTO) 是 DB 自增，与 banner 的雪花 id 设计冲突
 * （照 Coupon/mall_address 先例：裸 Long id + bootstrap 全局 id-type=assign_id → MP 内置雪花）。
 * createTime/updateTime/delFlag 由 DB DEFAULT / ON UPDATE 兜底（MP 非空字段才进 INSERT）。
 */
@Data
@TableName("mk_banner")
public class Banner {

    /** MP ASSIGN_ID 雪花；出参必须 @JsonSerialize(ToStringSerializer) 防 JS 精度丢失 */
    private Long id;

    /** 标题（管理端识别用） */
    private String title;

    /** 图片 objectKey（公开桶） */
    private String image;

    /** 0=无跳转 1=内部页面 2=商品详情 3=外部链接 */
    private Integer linkType;

    /** 跳转值：页面路径/spuId/https链接 */
    private String linkValue;

    /** 数字小靠前 */
    private Integer sort;

    /** 0=下架 1=上架；新增不设置时走 DB DEFAULT 1（默认上架） */
    private Integer status;

    /** 生效时间（NULL=立即） */
    private LocalDateTime startTime;

    /** 失效时间（NULL=永久） */
    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
