-- ====================================================================
-- Migration: order_item 补齐 BaseEntity 公共列
-- 描述：OrderItem 实体继承 BaseEntity（含 update_time/del_flag），但建表时
--       order_item 漏了这两列，导致 MyBatis-Plus SELECT 报
--       Unknown column 'update_time' → 订单列表接口 500
-- 适用：已有数据的数据库迁移（order_init.sql 已同步修正建表语句）
-- ====================================================================

USE degel_order;

ALTER TABLE order_item
    ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time,
    ADD COLUMN del_flag TINYINT DEFAULT 0 COMMENT '0=未删除 1=已删除' AFTER update_time;
