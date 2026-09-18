package com.degel.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** C 端站内信（发货/售后/仲裁结果通知；表在 degel_order，写入方均在本服务进程内） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    private Long userId;
    /** ship=发货 aftersale=售后结果 arbitrate=仲裁结果 */
    private String type;
    private String title;
    private String content;
    /** 0=未读 1=已读 */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
