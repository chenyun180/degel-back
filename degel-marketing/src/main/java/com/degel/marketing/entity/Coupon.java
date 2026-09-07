package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板（mk_coupon）。
 *
 * 不继承 BaseEntity：BaseEntity 的 @TableId(AUTO) 是 DB 自增，与券的雪花 id 设计冲突
 * （照 mall_address 先例：裸 Long id + bootstrap 全局 id-type=assign_id → MP 内置雪花）。
 * createTime/updateTime/delFlag 由 DB DEFAULT / ON UPDATE 兜底（MP 非空字段才进 INSERT）。
 */
@Data
@TableName("mk_coupon")
public class Coupon {

    /** MP ASSIGN_ID 雪花；出参必须 @JsonSerialize(ToStringSerializer) 防 JS 精度丢失 */
    private Long id;

    private String name;

    /** 1=平台券 2=店铺券 3=分摊券（一期仅1） */
    private Integer funderType;

    /** 店铺券/分摊券必填；平台券 NULL */
    private Long shopId;

    private BigDecimal platformAmount;
    private BigDecimal shopAmount;

    /** 1=满减 2=折扣(三期) 3=无门槛 */
    private Integer discountType;

    /** 使用门槛（满X元），无门槛=0 */
    private BigDecimal thresholdAmount;

    /** 满减=减免额；折扣=折数；无门槛=减免额 */
    private BigDecimal discountValue;

    /** 0=全场 1=指定分类 2=指定商品（一期仅0） */
    private Integer scopeType;

    private Integer totalCount;
    private Integer issuedCount;
    private Integer perUserLimit;

    private LocalDateTime receiveStart;
    private LocalDateTime receiveEnd;

    /** 1=绝对时间 2=领取后N天 */
    private Integer validType;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;

    /** 0=草稿 1=进行中 2=停发 */
    private Integer status;

    /** 0=草稿 1=待审核 2=已通过 3=已驳回（三期起店铺券走审核；平台券/分摊券恒 2） */
    private Integer auditStatus;

    /** 审核驳回理由 */
    private String rejectReason;

    private Long createBy;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
