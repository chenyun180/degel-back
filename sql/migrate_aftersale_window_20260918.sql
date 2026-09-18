-- 售后窗口限制：已完成订单限"确认收货后 N 天"内可申请售后（法定七天无理由底线，默认 7）
-- 与结算 T+7 咬合：退款窗口先关闭、结算后入账，负余额欠款退化为兜底逻辑
-- 配置复用 settlement_config 键值表（degel_order 库内唯一 KV 表，键名带领域前缀区分）
-- 幂等：WHERE NOT EXISTS

USE degel_order;

INSERT INTO settlement_config (config_key, config_value, remark)
SELECT 'aftersale_days', '7', '售后窗口（天）：确认收货后 N 天内可申请售后；改值即时生效'
WHERE NOT EXISTS (SELECT 1 FROM settlement_config WHERE config_key = 'aftersale_days');

-- 菜单更名：222「佣金配置」→「交易配置」（页面同时管理佣金比例与售后窗口）
USE degel_admin;
UPDATE sys_menu SET menu_name = '交易配置'
WHERE id = 222 AND del_flag = 0 AND menu_name = '佣金配置';

-- 验证
USE degel_order;
SELECT config_key, config_value FROM settlement_config;
