-- ====================================================================
-- Migration: Fix Menu Mismatch
-- 描述：修复两处菜单数据不一致
--   1. 平台「商品管理」目录 path 曾被 DataInitRunner 初始化为 'product'，
--      与前端静态路由 /platform-product 不匹配，导致菜单指向不存在的页面
--   2. 清理平台角色误绑的店铺端菜单（如 admin 能看到「店铺工作台」）
-- 适用：已有数据的数据库迁移（新环境通过修正后的 DataInitRunner 正确初始化）
-- 注意：店铺端菜单（店铺工作台子树）的 perms 均以 'shop:' 开头，
--       平台菜单无此前缀，故按 perms 前缀匹配即可覆盖整棵子树（含目录/菜单/按钮）
-- ====================================================================

USE degel_admin;

-- 1. 平台「商品管理」目录 path 对齐前端路由 /platform-product
--    （menu_name 条件避免误伤其他 path='product' 的记录）
UPDATE sys_menu
SET path = 'platform-product'
WHERE parent_id = 0
  AND menu_type = 'M'
  AND menu_name = '商品管理'
  AND path = 'product'
  AND del_flag = 0;

-- 2. 清理平台角色（含 admin）绑定的店铺端菜单（含店铺工作台整棵子树）
DELETE srm FROM sys_role_menu srm
INNER JOIN sys_role sr ON srm.role_id = sr.id
INNER JOIN sys_menu m  ON srm.menu_id = m.id
WHERE sr.role_type = 'platform'
  AND sr.del_flag = 0
  AND m.perms LIKE 'shop:%';

-- 3. 清理店铺角色绑定的平台端菜单（对称清理，防止反向误绑）
--    平台菜单的 perms 前缀为 system:% / product:%；平台目录（perms 为空）按名称定位
DELETE srm FROM sys_role_menu srm
INNER JOIN sys_role sr ON srm.role_id = sr.id
INNER JOIN sys_menu m  ON srm.menu_id = m.id
WHERE sr.role_type = 'shop'
  AND sr.del_flag = 0
  AND (
    m.perms LIKE 'system:%' OR m.perms LIKE 'product:%'
    OR (m.parent_id = 0 AND m.menu_type = 'M' AND m.menu_name IN ('系统管理', '商品管理'))
  );

-- 4. 验证：admin 角色不应再有任何 shop:% 权限
-- SELECT m.menu_name, m.path, m.perms
-- FROM sys_role_menu srm
-- JOIN sys_role sr ON sr.id = srm.role_id AND sr.role_key = 'admin'
-- JOIN sys_menu m ON m.id = srm.menu_id
-- WHERE m.perms LIKE 'shop:%';
-- 预期结果：空集
