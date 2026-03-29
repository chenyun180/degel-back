-- ====================================================================
-- Migration: Shop Role Simplification
-- 描述：将店铺角色从「每店独立店长+店员」简化为全局预设单一「店铺」角色
--       所有店铺账号共用 role_key='shop', shop_id=0 的全局角色
-- 适用：已有数据的数据库迁移（新环境通过 DataInitRunner 自动正确初始化）
-- ====================================================================

USE degel_admin;

-- 1. 解除所有旧角色（shop_admin / shop_staff）的用户绑定
DELETE sur FROM sys_user_role sur
INNER JOIN sys_role sr ON sur.role_id = sr.id
WHERE sr.role_key IN ('shop_admin', 'shop_staff');

-- 2. 删除所有旧角色的菜单关联
DELETE srm FROM sys_role_menu srm
INNER JOIN sys_role sr ON srm.role_id = sr.id
WHERE sr.role_key IN ('shop_admin', 'shop_staff');

-- 3. 软删除所有旧角色（含全局模板 shopId=0 和各店铺实例）
UPDATE sys_role SET del_flag = 1
WHERE role_key IN ('shop_admin', 'shop_staff');

-- 4. 软删除员工管理菜单及其子按钮（perms: shop:staff:*）
UPDATE sys_menu SET del_flag = 1
WHERE perms LIKE 'shop:staff%';

-- 5. 软删除角色管理菜单及其子按钮（perms: shop:role:*）
UPDATE sys_menu SET del_flag = 1
WHERE perms LIKE 'shop:role%';

-- 6. 创建全局预设「店铺」角色（shopId=0，所有店铺账号共用）
INSERT IGNORE INTO sys_role (role_name, role_key, role_type, shop_id, sort, status, remark, del_flag)
VALUES ('店铺', 'shop', 'shop', 0, 1, 0, '店铺账号，拥有商品管理与店铺信息权限', 0);

-- 7. 为全局店铺角色绑定菜单（shop: 开头，排除 staff 和 role 相关）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
INNER JOIN sys_menu m
    ON m.del_flag = 0
    AND m.perms LIKE 'shop:%'
    AND m.perms NOT LIKE 'shop:staff%'
    AND m.perms NOT LIKE 'shop:role%'
WHERE r.role_key = 'shop' AND r.shop_id = 0 AND r.del_flag = 0;

-- 8. 为所有现有店铺账号绑定全局店铺角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.shop_id != 0
  AND u.del_flag = 0
  AND r.role_key = 'shop'
  AND r.shop_id = 0
  AND r.del_flag = 0;
