USE degel_admin;

-- 1. sys_role 新增 role_type 和 shop_id 字段
ALTER TABLE sys_role
  ADD COLUMN role_type VARCHAR(10) DEFAULT 'platform' COMMENT 'platform=平台角色 shop=店铺角色' AFTER role_key,
  ADD COLUMN shop_id BIGINT DEFAULT 0 COMMENT '店铺ID，平台角色为0' AFTER role_type;

-- 2. 更新已有角色为平台角色
UPDATE sys_role SET role_type = 'platform', shop_id = 0 WHERE id > 0;

-- 3. 去掉 role_key 的唯一约束，改为 role_key + shop_id 组合唯一
DROP INDEX uk_role_key ON sys_role;
ALTER TABLE sys_role ADD UNIQUE KEY uk_role_key_shop (role_key, shop_id);
