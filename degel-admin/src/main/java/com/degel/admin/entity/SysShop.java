package com.degel.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_shop")
public class SysShop extends BaseEntity {

    private String shopName;
    private String logo;
    private String announcement;
    private String description;
    private String contactName;
    private String contactPhone;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
