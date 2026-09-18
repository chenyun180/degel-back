package com.degel.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.order.entity.Notification;

/**
 * C 端站内信。写入点全在 degel-order（发货/售后审核/仲裁），best-effort 不阻断主流程
 */
public interface NotificationService extends IService<Notification> {

    /** 发通知（best-effort：失败仅记日志） */
    void send(Long userId, String type, String title, String content);

    /** 用户消息分页（未读在前） */
    com.baomidou.mybatisplus.core.metadata.IPage<Notification> pageForUser(Long userId, Integer page, Integer pageSize);

    long unreadCount(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);
}
