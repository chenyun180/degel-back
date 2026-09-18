-- 平台仲裁：售后"商家拒绝 → 用户申请介入 → 平台判定"闭环（PRD 6.3）
-- 状态机扩展（代码语义对齐）：5=已拒绝(可申请介入) → 6=平台介入中 → 3=退款完成(支持用户) / 7=仲裁维持拒绝(终态)
-- 幂等；执行后删 Redis admin:user:info:{username} 并重新登录

USE degel_order;

-- 仲裁意见（支持用户/维持拒绝的处理说明，判定人随文案写入）
-- ⚠️ MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行本脚本时跳过下面这条 ALTER（重复列报错=已应用）
ALTER TABLE order_after_sale
    ADD COLUMN platform_remark VARCHAR(500) DEFAULT '' COMMENT '平台仲裁意见（判定结果+操作人）';

-- 验证列已加
SHOW COLUMNS FROM order_after_sale LIKE 'platform_remark';

-- ========== 管理台菜单：平台「售后仲裁」 ==========
USE degel_admin;

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 225, 0, '售后仲裁', 'platform-aftersale', NULL, NULL, 'audit', 'M', 7, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 225 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 226, 225, '仲裁列表', 'arbitration', './Platform/AfterSale/Arbitration', 'order:aftersale:arbitrate', NULL, 'C', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 226 AND del_flag = 0);

-- admin 角色（id=1）绑定：目录 + 菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (225, 226) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 验证
SELECT m.id, m.menu_name, m.path, GROUP_CONCAT(rm.role_id) AS roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
WHERE m.id IN (225, 226) AND m.del_flag = 0
GROUP BY m.id, m.menu_name, m.path;
