package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.vo.NotificationVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端站内信（发货/售后/仲裁结果通知；数据在 degel_order，经 Feign 转发）
 */
@RestController
@RequestMapping("/app/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final OrderFeignClient orderFeignClient;

    /** 消息列表（未读在前） */
    @GetMapping
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<NotificationVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return orderFeignClient.notificationPage(userId, page, pageSize);
    }

    /** 未读数（用户中心红点） */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long userId = UserContext.getUserId();
        return orderFeignClient.notificationUnreadCount(userId);
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return orderFeignClient.notificationRead(id, userId);
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = UserContext.getUserId();
        return orderFeignClient.notificationReadAll(userId);
    }
}
