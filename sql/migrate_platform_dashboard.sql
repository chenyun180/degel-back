-- ====================================================================
-- Migration: Platform Dashboard Menu（平台工作台/数据看板）
-- 描述：为平台角色新增「平台工作台 > 数据看板」菜单，作为管理员登录默认页
-- 适用：已有数据的数据库（新环境通过 DataInitRunner 正确初始化）
-- 注意：现网库菜单 ID 为自增不连续，本脚本一律按 path/perms/parent 定位，
--       全部 WHERE NOT EXISTS 幂等，可重复执行
-- ====================================================================

USE degel_admin;

-- 1. 插入顶级目录「平台工作台」（sort=0 排最前）
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag, create_time, update_time)
SELECT 0, '平台工作台', 'platform', '', '', 'DashboardOutlined', 'M', 0, 0, 0, 0, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'platform' AND menu_type = 'M' AND del_flag = 0
);

-- 2. 插入子菜单「数据看板」（前端路由 /platform/dashboard，与 buildRouters 拼接规则一致）
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag, create_time, update_time)
SELECT p.id, '数据看板', 'dashboard', './Platform/Dashboard', 'platform:dashboard', 'DashboardOutlined', 'C', 1, 0, 0, 0, NOW(), NOW()
FROM sys_menu p
WHERE p.parent_id = 0 AND p.path = 'platform' AND p.menu_type = 'M' AND p.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu c WHERE c.parent_id = p.id AND c.path = 'dashboard' AND c.del_flag = 0
  );

-- 3. 绑定所有平台角色（含 admin；未来新建的平台角色也应手动绑定）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
    (m.parent_id = 0 AND m.path = 'platform' AND m.menu_type = 'M' AND m.del_flag = 0)
    OR (m.path = 'dashboard' AND m.perms = 'platform:dashboard' AND m.del_flag = 0)
)
WHERE r.role_type = 'platform' AND r.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- 4. 验证：admin 角色应能看到平台工作台两条菜单
-- SELECT m.menu_name, m.path, m.perms
-- FROM sys_role_menu srm
-- JOIN sys_role sr ON sr.id = srm.role_id AND sr.role_key = 'admin'
-- JOIN sys_menu m ON m.id = srm.menu_id
-- WHERE m.path IN ('platform', 'dashboard');
-- 预期：2 行（平台工作台 / 数据看板）
-- 执行后 admin 需重新登录（routers 在 /admin/user/info 查询，非实时刷新）
