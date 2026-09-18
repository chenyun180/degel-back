package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.Notification;
import com.degel.order.mapper.NotificationMapper;
import com.degel.order.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Override
    public void send(Long userId, String type, String title, String content) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setIsRead(0);
            save(n);
        } catch (Exception e) {
            // 通知是旁路增强，失败不阻断发货/售后主流程
            log.error("[notify] 站内信写入失败 userId={} type={}", userId, type, e);
        }
    }

    @Override
    public IPage<Notification> pageForUser(Long userId, Integer page, Integer pageSize) {
        return page(new Page<>(page, pageSize), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getIsRead)
                .orderByDesc(Notification::getId));
    }

    @Override
    public long unreadCount(Long userId) {
        return count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long id, Long userId) {
        Notification n = getById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException("消息不存在");
        }
        if (Integer.valueOf(0).equals(n.getIsRead())) {
            n.setIsRead(1);
            updateById(n);
        }
    }

    @Override
    public void markAllRead(Long userId) {
        baseMapper.markAllRead(userId);
    }
}
