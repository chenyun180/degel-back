-- 优惠券三期：管理台菜单（平台「营销管理」+ 店铺工作台「营销中心/我的优惠券」）
-- 幂等：全部 WHERE NOT EXISTS；sys_role_menu 必须连祖先目录一起插（database.md 的坑）
--
-- ⚠️ sys_menu 存量约定（2026-09-07 踩坑总结，违反任一条菜单都不显示）：
--   1. path 存【相对段】不带前导 / —— SysMenuServiceImpl.buildRouters 会拼 parentPath + "/" + path
--   2. visible=0 才可见（构建器 setHidden(visible==1)，语义与字面相反）
--   3. component 带 ./ 前缀（如 ./Shop/Info）
--   4. 店铺侧菜单必须挂在「店铺工作台」目录（id=24，由 DataInitRunner 初始化）下
-- USE degel_admin;

-- ========== 平台侧：营销管理目录 + 优惠券管理 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 210, 0, '营销管理', 'platform-marketing', NULL, NULL, 'gift', 'M', 5, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 210 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 211, 210, '优惠券管理', 'coupon', './Platform/Marketing/Coupon', 'marketing:coupon:list', NULL, 'C', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 211 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 212, 211, '新增优惠券', NULL, NULL, 'marketing:coupon:add', NULL, 'F', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 212 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 213, 211, '审核店铺券', NULL, NULL, 'marketing:coupon:audit', NULL, 'F', 2, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 213 AND del_flag = 0);

-- admin 角色（id=1）绑定：目录 + 菜单 + 按钮全插
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (210, 211, 212, 213) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- ========== 店铺侧：营销中心目录 + 我的优惠券（挂在「店铺工作台」id=24 下） ==========
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 150, 24, '营销中心', 'shop-marketing-dir', NULL, NULL, 'gift', 'M', 5, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 150 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 151, 150, '我的优惠券', 'shop-coupon-list', './Shop/Marketing/Coupon', 'shop:coupon:list', NULL, 'C', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 151 AND del_flag = 0);

INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT 152, 151, '新增店铺券', NULL, NULL, 'shop:coupon:add', NULL, 'F', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 152 AND del_flag = 0);

-- shop 角色绑定：目录 + 菜单 + 按钮
-- ⚠️ 按 role_key='shop' 动态找角色 id，禁止写死——2026-09-07 踩坑：曾写死 id=4，
-- 角色重构（role_refactor.sql）后店铺角色已是 id=10，导致店铺账号看不到券菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_menu m, sys_role r
WHERE r.role_key = 'shop' AND r.del_flag = 0
  AND m.id IN (150, 151, 152) AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ========== 验证 ==========
SELECT m.id, m.menu_name, m.path, m.visible, GROUP_CONCAT(rm.role_id) AS roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
WHERE m.id IN (210, 211, 212, 213, 150, 151, 152) AND m.del_flag = 0
GROUP BY m.id, m.menu_name, m.path, m.visible;

-- ⚠️ 菜单是登录时下发的：执行后需重新登录 admin / 店铺账号才可见；
--    admin 服务还缓存了 admin:user:info:{username}（Redis DB 0），改库后必须删 key
