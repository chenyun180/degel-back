package com.degel.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_shop")
public class SysShop extends BaseEntity {

    private String shopName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
}
