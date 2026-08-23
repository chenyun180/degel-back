-- ====================================================================
-- Migration: 移除 operator / common 角色（简化为 admin + shop 双角色模型）
-- 适用：已有数据的数据库迁移（新环境通过 DataInitRunner 自动正确初始化）
-- 注意：本迁移会将原 operator/common 角色的用户改绑为超级管理员（获得全部权限）。
--      这是双角色模型（admin+shop）下的既定决策；如需收紧，请执行前手工核对这些用户。
-- ====================================================================
USE degel_admin;

-- 1. operator/common 角色的用户改绑 admin（若有用户挂着这两个角色）
-- 前置条件：库中存在 role_key='admin' 且 del_flag=0 的角色（新库由 DataInitRunner/data_init.sql 保证）
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT sur.user_id, (SELECT id FROM sys_role WHERE role_key = 'admin' AND del_flag = 0 LIMIT 1)
FROM sys_user_role sur
INNER JOIN sys_role sr ON sur.role_id = sr.id
WHERE sr.role_key IN ('operator', 'common');

-- 2. 解绑并软删除 operator/common
DELETE sur FROM sys_user_role sur
INNER JOIN sys_role sr ON sur.role_id = sr.id
WHERE sr.role_key IN ('operator', 'common');

DELETE srm FROM sys_role_menu srm
INNER JOIN sys_role sr ON srm.role_id = sr.id
WHERE sr.role_key IN ('operator', 'common');

UPDATE sys_role SET del_flag = 1 WHERE role_key IN ('operator', 'common');
