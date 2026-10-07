-- 店铺资料变更审批（Flowable 工作流首个接入业务）
-- 店铺端「店铺信息」保存不再直接生效：生成变更申请 → 平台审批（流程 shopChange）→ 通过后回写 sys_shop
-- Flowable 引擎表（ACT_*/FLW_*）由 degel-admin 启动时 database-schema-update=true 自动建，本脚本不含
-- 幂等；执行后删 Redis admin:user:info:{username} 并重新登录（菜单才会刷新）

USE degel_admin;

CREATE TABLE IF NOT EXISTS sys_shop_change (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id             BIGINT       NOT NULL COMMENT '申请店铺',
    shop_name           VARCHAR(100) DEFAULT '' COMMENT '申请时的店铺名（列表展示用）',
    before_data         TEXT COMMENT '变更前资料快照 JSON',
    after_data          TEXT COMMENT '申请变更后资料 JSON（审批通过时整体回写 sys_shop）',
    status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待审核 1=已通过 2=已驳回 3=已撤回',
    process_instance_id VARCHAR(64)  DEFAULT NULL COMMENT 'Flowable 流程实例 ID',
    apply_user_id       BIGINT       DEFAULT NULL COMMENT '申请人 sys_user.id',
    audit_user_id       BIGINT       DEFAULT NULL COMMENT '审批人 sys_user.id',
    audit_remark        VARCHAR(500) DEFAULT '' COMMENT '审批意见（驳回必填）',
    audit_time          DATETIME     DEFAULT NULL,
    create_time         DATETIME     DEFAULT NULL,
    update_time         DATETIME     DEFAULT NULL,
    del_flag            TINYINT      NOT NULL DEFAULT 0,
    KEY idx_shop_status (shop_id, status),
    KEY idx_status_create (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺资料变更申请';

-- ========== 管理台菜单：系统管理 → 资料审批 ==========
-- 不写死 id（按 path 幂等），父目录按 path='system' 查
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag)
SELECT p.id, '资料审批', 'shop-change', './System/ShopChange', 'system:shop:change', 'AuditOutlined', 'C', 5, 0, 0, 0
FROM sys_menu p
WHERE p.path = 'system' AND p.parent_id = 0 AND p.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'shop-change' AND del_flag = 0);

-- admin 角色（role_key='admin'）绑定；父目录「系统管理」admin 已有，无需补祖先
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m
WHERE r.role_key = 'admin' AND r.del_flag = 0
  AND m.path = 'shop-change' AND m.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 验证
SHOW TABLES LIKE 'sys_shop_change';
SELECT m.id, m.parent_id, m.menu_name, m.path, GROUP_CONCAT(rm.role_id) AS roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
WHERE m.path = 'shop-change' AND m.del_flag = 0
GROUP BY m.id, m.parent_id, m.menu_name, m.path;
