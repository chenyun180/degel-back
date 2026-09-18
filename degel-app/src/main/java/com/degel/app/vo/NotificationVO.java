package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** C 端站内信（对应 degel_order.notification，Feign 分页返回用具体类禁 IPage） */
@Data
public class NotificationVO {

    private Long id;
    /** ship=发货 aftersale=售后结果 arbitrate=仲裁结果 */
    private String type;
    private String title;
    private String content;
    /** 0=未读 1=已读 */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
