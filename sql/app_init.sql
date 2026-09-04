CREATE DATABASE IF NOT EXISTS degel_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE degel_app;

-- ========== C端用户表 ==========
CREATE TABLE IF NOT EXISTS mall_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    openid      VARCHAR(100) DEFAULT '' COMMENT '微信openid，H5用户为空',
    nickname    VARCHAR(50)  DEFAULT '' COMMENT '昵称',
    avatar      VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    phone       VARCHAR(20)  DEFAULT '' COMMENT '手机号，微信用户可为空',
    password    VARCHAR(100) DEFAULT '' COMMENT '密码(BCrypt)，微信用户为空',
    status      TINYINT      DEFAULT 0 COMMENT '0=正常 1=封禁',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT      DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户表';

-- ========== 收货地址表 ==========
CREATE TABLE IF NOT EXISTS mall_address (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    name        VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    province    VARCHAR(50)  DEFAULT '' COMMENT '省',
    city        VARCHAR(50)  DEFAULT '' COMMENT '市',
    district    VARCHAR(50)  DEFAULT '' COMMENT '区/县',
    detail      VARCHAR(255) DEFAULT '' COMMENT '详细地址',
    longitude   DECIMAL(9,6) DEFAULT NULL COMMENT '经度（GCJ-02 火星坐标系，高德地图选点）',
    latitude    DECIMAL(8,6) DEFAULT NULL COMMENT '纬度（GCJ-02 火星坐标系，高德地图选点）',
    is_default  TINYINT      DEFAULT 0 COMMENT '0=否 1=默认地址',
    del_flag    TINYINT      DEFAULT 0 COMMENT '0=正常 1=已删除',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ========== 购物车表 ==========
CREATE TABLE IF NOT EXISTS mall_cart (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    spu_id      BIGINT   NOT NULL COMMENT 'SPU ID',
    sku_id      BIGINT   NOT NULL COMMENT 'SKU ID',
    quantity    INT      NOT NULL DEFAULT 1 COMMENT '数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT  DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    UNIQUE KEY uk_user_sku (user_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ========== 支付/退款流水表 ==========
CREATE TABLE IF NOT EXISTS mall_payment_log (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    user_id     BIGINT         NOT NULL COMMENT 'C端用户ID',
    order_id    BIGINT         NOT NULL COMMENT '订单ID',
    order_no    VARCHAR(64)    NOT NULL COMMENT '订单编号',
    amount      DECIMAL(10, 2) NOT NULL COMMENT '金额',
    direction   VARCHAR(10)    NOT NULL COMMENT 'pay=支付 refund=退款',
    status      TINYINT        DEFAULT 0 COMMENT '0=成功 1=失败',
    remark      VARCHAR(255)   DEFAULT '' COMMENT '备注',
    create_time DATETIME       DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付退款流水表';
