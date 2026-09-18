-- 平台资金总览菜单（结算管理下第三个页面，2026-09-18）
-- 幂等：WHERE NOT EXISTS；执行后删 Redis admin:user:info:{username} 并重新登录

USE degel_admin;

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 223, 220, '资金总览', 'overview', './Platform/Settlement/Overview', 'order:settlement:overview', NULL, 'C', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 223 AND del_flag = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (220, 223) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 验证
SELECT m.id, m.menu_name, m.path, GROUP_CONCAT(rm.role_id) AS roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
WHERE m.id IN (220, 223) AND m.del_flag = 0
GROUP BY m.id, m.menu_name, m.path;
