-- 优惠券一期：degel_marketing 库初始化
-- 设计文档：docs/优惠券功能设计方案.md §4
CREATE DATABASE IF NOT EXISTS degel_marketing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE degel_marketing;

CREATE TABLE IF NOT EXISTS mk_coupon (
    id               BIGINT        NOT NULL COMMENT '雪花id（MP ASSIGN_ID）',
    name             VARCHAR(100)  NOT NULL COMMENT '券名，如 新客立减10元',
    funder_type      TINYINT       NOT NULL COMMENT '1=平台券 2=店铺券 3=分摊券（一期仅1）',
    shop_id          BIGINT        DEFAULT NULL COMMENT '店铺券/分摊券必填；平台券NULL',
    platform_amount  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台承担金额',
    shop_amount      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '店铺承担金额',
    discount_type    TINYINT       NOT NULL COMMENT '1=满减 2=折扣(三期) 3=无门槛',
    threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛（满X元），无门槛=0',
    discount_value   DECIMAL(10,2) NOT NULL COMMENT '满减=减免额；折扣=折数如8.5；无门槛=减免额',
    scope_type       TINYINT       NOT NULL DEFAULT 0 COMMENT '0=全场 1=指定分类 2=指定商品（一期仅0）',
    total_count      INT           NOT NULL COMMENT '发放总量',
    issued_count     INT           NOT NULL DEFAULT 0 COMMENT '已发放数量',
    per_user_limit   INT           NOT NULL DEFAULT 1 COMMENT '每人限领',
    receive_start    DATETIME      NOT NULL COMMENT '可领开始时间',
    receive_end      DATETIME      NOT NULL COMMENT '可领截止时间',
    valid_type       TINYINT       NOT NULL COMMENT '1=绝对时间 2=领取后N天',
    valid_start      DATETIME      DEFAULT NULL COMMENT 'valid_type=1 用券起',
    valid_end        DATETIME      DEFAULT NULL COMMENT 'valid_type=1 用券止',
    valid_days       INT           DEFAULT NULL COMMENT 'valid_type=2 领取后N天有效',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0=草稿 1=进行中 2=停发',
    audit_status     TINYINT       NOT NULL DEFAULT 2 COMMENT '0=草稿 1=待审核 2=已通过 3=已驳回（店铺券二期启用平台审核；一期全2）',
    create_by        BIGINT        NOT NULL COMMENT '创建人 sys_user.id',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_funder_shop (funder_type, shop_id),
    KEY idx_status (status, receive_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- 预留：指定分类/商品的适用范围（一期不读写）
CREATE TABLE IF NOT EXISTS mk_coupon_scope (
    id         BIGINT   NOT NULL,
    coupon_id  BIGINT   NOT NULL,
    scope_type TINYINT  NOT NULL COMMENT '1=分类 2=商品',
    target_id  BIGINT   NOT NULL COMMENT '分类id或spu_id',
    PRIMARY KEY (id),
    KEY idx_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='券适用范围（预留）';

CREATE TABLE IF NOT EXISTS mk_user_coupon (
    id           BIGINT        NOT NULL COMMENT '雪花id（MP ASSIGN_ID）',
    coupon_id    BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0=未用 1=已锁定 2=已核销 3=已过期 4=已退回 5=已作废',
    order_id     BIGINT        DEFAULT NULL COMMENT '核销时的订单id（confirm 回填；锁券时只有预生成 orderNo）',
    order_no     VARCHAR(32)   DEFAULT NULL COMMENT '锁券时的预生成订单号（lock/unlock 定位）',
    platform_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '领取时快照-平台承担',
    shop_amount  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '领取时快照-店铺承担',
    receive_time DATETIME      NOT NULL,
    expire_time  DATETIME      NOT NULL COMMENT '按 valid_type 计算落库，惰性+定时双重过期',
    use_time     DATETIME      DEFAULT NULL,
    lock_time    DATETIME      DEFAULT NULL COMMENT '最近一次锁券时间（60分钟未 confirm 自动释放兜底用）',
    create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag     TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_coupon_user (coupon_id, user_id),
    KEY idx_order (order_id),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';
