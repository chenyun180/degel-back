-- 积分系统迁移（2026-09-16）
-- 设计文档：doc/积分系统设计.md（v2，积分域归属 degel-marketing）
-- 执行：任意库连接即可（root），表名带库前缀

-- ========== degel_marketing 库 ==========

-- 积分账户（余额冗余；懒创建——首次获得积分时 INSERT）
CREATE TABLE IF NOT EXISTS degel_marketing.mk_points_account (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT 'C端用户ID',
    balance      INT NOT NULL DEFAULT 0 COMMENT '积分余额',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户';

-- 积分流水（源；余额可由 SUM(points) 对账）
CREATE TABLE IF NOT EXISTS degel_marketing.mk_points_log (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    type         VARCHAR(20) NOT NULL COMMENT 'earn/redeem/redeem_return/earn_reclaim/checkin/freeze/unfreeze',
    points       INT NOT NULL COMMENT '正=入账 负=出账',
    order_id     BIGINT DEFAULT NULL COMMENT '关联订单（签到为空）',
    order_no     VARCHAR(32) DEFAULT NULL,
    remark       VARCHAR(200) DEFAULT '',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, create_time),
    KEY idx_order (order_id),
    KEY idx_type_order (type, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- 每日签到（DB 兜底防重；Redis SETNX 是第一道）
CREATE TABLE IF NOT EXISTS degel_marketing.mk_checkin (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    points       INT NOT NULL COMMENT '当日发放积分数',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到';

-- ========== degel_order 库 ==========

ALTER TABLE degel_order.order_info
    ADD COLUMN points_used INT NOT NULL DEFAULT 0 COMMENT '本单抵扣积分数',
    ADD COLUMN points_deduct DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '积分抵扣金额',
    ADD COLUMN points_earned INT NOT NULL DEFAULT 0 COMMENT '本单获得积分数（确认收货后回写）';
