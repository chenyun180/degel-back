-- 店铺分账清结算（模拟支付版）：5 张结算表 + 管理台菜单
-- 设计文档：docs/plans/2026-09-17-settlement-design.md
-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT WHERE NOT EXISTS / INSERT..ON DUPLICATE
-- 执行前提：MySQL 192.168.1.14:3306 可达；执行后删 Redis admin:user:info:{username} 并重新登录
--
-- ⚠️ sys_menu 存量约定（照 migrate_marketing_menu.sql，违反任一条菜单不显示）：
--   path 存相对段不带前导 /；visible=0 才可见；component 带 ./ 前缀；
--   店铺菜单挂「店铺工作台」目录 id=24 下；sys_role_menu 必须连祖先目录一起插；
--   店铺角色按 role_key='shop' 动态找 id 禁止写死
-- 菜单 id 段：平台 220-222、店铺 160-161（执行前先 SELECT MAX(id) FROM sys_menu 确认未占用）

USE degel_order;

-- ========== 1) 结算配置（佣金比例，改值仅影响之后新生成的结算明细） ==========
CREATE TABLE IF NOT EXISTS settlement_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key   VARCHAR(64)  NOT NULL COMMENT '目前仅 commission_rate（百分比数值，5=5%）',
  config_value VARCHAR(128) NOT NULL,
  remark       VARCHAR(255) DEFAULT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算配置';

INSERT INTO settlement_config (config_key, config_value, remark)
SELECT 'commission_rate', '5', '全局佣金比例（%），快照到每行结算明细'
WHERE NOT EXISTS (SELECT 1 FROM settlement_config WHERE config_key = 'commission_rate');

-- ========== 2) 结算明细（一订单一行，T+7 定时任务生成） ==========
CREATE TABLE IF NOT EXISTS settlement_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id    BIGINT NOT NULL,
  order_no    VARCHAR(64) NOT NULL,
  shop_id     BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  pay_amount       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '用户实付',
  platform_subsidy DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '平台承担券部分',
  points_deduct    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '积分抵扣（平台承担成本）',
  gross_amount     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '基数=pay+platform_subsidy+points_deduct',
  commission_rate  DECIMAL(5,2)  NOT NULL DEFAULT 0 COMMENT '生成时快照的全局比例（%）',
  commission_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  net_amount       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '入余额金额=gross-commission',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0待入账 1已入账 2已扣回(结算后整单退款)',
  settle_time DATETIME DEFAULT NULL,
  deduct_time DATETIME DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_shop_status (shop_id, status),
  KEY idx_settle_time (settle_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺结算明细（T+7）';

-- ========== 3) 商家结算账户（余额可负：结算后退款扣回不足时） ==========
CREATE TABLE IF NOT EXISTS settlement_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id  BIGINT NOT NULL,
  balance  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '可提现余额，可为负',
  total_settled   DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '累计结算入账（审计口径，不冲减）',
  total_withdrawn DECIMAL(14,2) NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家结算账户';

-- ========== 4) 账户流水（uk_biz 幂等核心；balance_after 供对账自校验） ==========
CREATE TABLE IF NOT EXISTS settlement_account_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id  BIGINT NOT NULL,
  biz_type TINYINT NOT NULL COMMENT '1结算入账 2提现支出 3退款扣回',
  biz_no   BIGINT NOT NULL COMMENT '1→结算明细id 2→提现单id 3→售后单id',
  amount   DECIMAL(12,2) NOT NULL COMMENT '有符号：1为正，2/3为负',
  balance_after DECIMAL(12,2) NOT NULL COMMENT '变动后余额快照',
  remark   VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_biz (biz_type, biz_no),
  KEY idx_shop (shop_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算账户流水';

-- ========== 5) 提现单（审核通过=模拟打款完成，凭证=本单+流水 biz_type=2） ==========
CREATE TABLE IF NOT EXISTS settlement_withdraw (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  withdraw_no VARCHAR(32) NOT NULL,
  shop_id  BIGINT NOT NULL,
  amount   DECIMAL(12,2) NOT NULL,
  status   TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过(已模拟打款) 2已驳回',
  apply_remark VARCHAR(255) DEFAULT NULL,
  audit_remark VARCHAR(255) DEFAULT NULL,
  audit_by VARCHAR(64) DEFAULT NULL COMMENT '平台审核人',
  audit_time DATETIME DEFAULT NULL,
  pay_time  DATETIME DEFAULT NULL COMMENT '模拟打款时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_withdraw_no (withdraw_no),
  KEY idx_shop (shop_id, create_time),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家提现单';

-- ========== 管理台菜单：平台「结算管理」+ 店铺「资金结算」 ==========
USE degel_admin;

-- 平台侧：结算管理目录 + 提现审核 + 佣金配置
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 220, 0, '结算管理', 'platform-settlement', NULL, NULL, 'money', 'M', 6, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 220 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 221, 220, '提现审核', 'withdraw', './Platform/Settlement/Withdraw', 'order:settlement:withdraw', NULL, 'C', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 221 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 222, 220, '佣金配置', 'config', './Platform/Settlement/Config', 'order:settlement:config', NULL, 'C', 2, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 222 AND del_flag = 0);

-- admin 角色（id=1）绑定：目录 + 菜单全插
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (220, 221, 222) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 店铺侧：资金结算（挂在「店铺工作台」id=24 下，单页含余额/明细/提现）
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 160, 24, '资金结算', 'shop-settlement-account', './Shop/Settlement/Account', 'shop:settlement:list', NULL, 'C', 6, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 160 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 161, 160, '申请提现', NULL, NULL, 'shop:settlement:withdraw', NULL, 'F', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 161 AND del_flag = 0);

-- shop 角色绑定：菜单 + 按钮 + 祖先目录（店铺工作台 id=24 已有，无需补）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_menu m, sys_role r
WHERE r.role_key = 'shop' AND r.del_flag = 0
  AND m.id IN (160, 161) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ========== 验证 ==========
SELECT m.id, m.menu_name, m.path, m.visible, GROUP_CONCAT(rm.role_id) AS roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
WHERE m.id IN (220, 221, 222, 160, 161) AND m.del_flag = 0
GROUP BY m.id, m.menu_name, m.path, m.visible;

USE degel_order;
SELECT config_key, config_value FROM settlement_config;
SHOW TABLES LIKE 'settlement%';

-- ⚠️ 菜单是登录时下发的：执行后需重新登录 admin / 店铺账号才可见；
--    admin 服务缓存 admin:user:info:{username}（Redis DB 0），改库后必须删 key
