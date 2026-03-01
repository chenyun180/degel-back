CREATE DATABASE IF NOT EXISTS degel_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE degel_order;

-- ========== 订单主表 ==========
CREATE TABLE IF NOT EXISTS order_info (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单编号',
    user_id         BIGINT        NOT NULL COMMENT '买家ID',
    shop_id         BIGINT        NOT NULL COMMENT '店铺ID',
    total_amount    DECIMAL(10,2) NOT NULL COMMENT '商品总金额',
    freight_amount  DECIMAL(10,2) DEFAULT 0 COMMENT '运费',
    discount_amount DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
    pay_amount      DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待付款 1=已付款待发货 2=已发货 3=已完成 4=已取消 5=售后中',
    pay_time        DATETIME      DEFAULT NULL,
    ship_time       DATETIME      DEFAULT NULL,
    receive_time    DATETIME      DEFAULT NULL,
    cancel_time     DATETIME      DEFAULT NULL,
    receiver_name   VARCHAR(50)   DEFAULT '',
    receiver_phone  VARCHAR(20)   DEFAULT '',
    receiver_address VARCHAR(500) DEFAULT '',
    remark          VARCHAR(500)  DEFAULT '' COMMENT '买家留言',
    express_company VARCHAR(50)   DEFAULT '' COMMENT '快递公司',
    express_no      VARCHAR(50)   DEFAULT '' COMMENT '快递单号',
    auto_cancel_time DATETIME     DEFAULT NULL COMMENT '自动取消时间',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_shop_id_status (shop_id, status),
    KEY idx_user_id (user_id),
    KEY idx_pay_time (pay_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ========== 订单明细表 ==========
CREATE TABLE IF NOT EXISTS order_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    spu_id          BIGINT        NOT NULL,
    sku_id          BIGINT        NOT NULL,
    spu_name        VARCHAR(200)  DEFAULT '' COMMENT '商品名快照',
    sku_spec        VARCHAR(500)  DEFAULT '' COMMENT '规格快照',
    sku_image       VARCHAR(500)  DEFAULT '',
    price           DECIMAL(10,2) NOT NULL COMMENT '单价快照',
    quantity        INT           NOT NULL,
    total_amount    DECIMAL(10,2) NOT NULL,
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ========== 售后单表 ==========
CREATE TABLE IF NOT EXISTS order_after_sale (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    order_item_id   BIGINT        DEFAULT NULL COMMENT '售后商品，NULL=整单退',
    user_id         BIGINT        NOT NULL,
    shop_id         BIGINT        NOT NULL,
    type            TINYINT       NOT NULL COMMENT '1=仅退款 2=退货退款',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待商家处理 1=待买家退货 2=待商家收货 3=退款中 4=已完成 5=已拒绝 6=平台介入',
    reason          VARCHAR(500)  DEFAULT '',
    refund_amount   DECIMAL(10,2) DEFAULT 0,
    express_company VARCHAR(50)   DEFAULT '',
    express_no      VARCHAR(50)   DEFAULT '',
    merchant_remark VARCHAR(500)  DEFAULT '' COMMENT '商家处理备注',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_shop_id_status (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单表';
