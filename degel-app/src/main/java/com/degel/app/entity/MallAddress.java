package com.degel.app.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址实体
 * 对应数据库表 mall_address（degel_app 库）
 *
 * 注意：mall_address 不继承 BaseEntity，因其 del_flag 已在表定义中，
 * 且需要直接控制 is_default 字段，避免 BaseEntity 自动注入干扰。
 */
@Data
@TableName("mall_address")
public class MallAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 收货人姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String detail;

    /**
     * 是否默认地址
     * 0 = 否
     * 1 = 是
     */
    private Integer isDefault;

    /**
     * 逻辑删除标志
     * 0 = 正常
     * 1 = 已删除
     */
    @TableLogic
    private Integer delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
