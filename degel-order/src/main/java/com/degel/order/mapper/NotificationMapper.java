package com.degel.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.order.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 全部已读（CAS 只动未读行） */
    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0 AND del_flag = 0")
    int markAllRead(@Param("userId") Long userId);
}
