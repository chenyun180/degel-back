package com.degel.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 店铺资料变更申请。审批由 Flowable 流程 shopChange 驱动，状态终态由流程内 delegate 回写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_shop_change")
public class SysShopChange extends BaseEntity {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_WITHDRAWN = 3;

    private Long shopId;
    private String shopName;
    /** 变更前资料快照（ShopProfileVo JSON） */
    private String beforeData;
    /** 申请变更后资料（ShopProfileVo JSON） */
    private String afterData;
    private Integer status;
    private String processInstanceId;
    private Long applyUserId;
    private Long auditUserId;
    private String auditRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;
}
