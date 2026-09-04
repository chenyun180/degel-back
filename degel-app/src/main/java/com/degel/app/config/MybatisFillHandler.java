package com.degel.app.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充（BaseEntity.createTime/updateTime 标了 FieldFill.INSERT/INSERT_UPDATE）
 *
 * 缺失该 handler 时，带 fill 注解的 null 字段会被显式写入 NULL 列，
 * 绕过数据库 DEFAULT CURRENT_TIMESTAMP——mall_cart/mall_payment_log 的 create_time 曾因此恒为 NULL
 */
@Component
public class MybatisFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
