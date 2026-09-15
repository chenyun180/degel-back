-- ============================================================================
-- 秒杀功能迁移（2026-09-15）
-- 1) degel_marketing：秒杀场次 + 场次秒杀商品两表
-- 2) degel_order：order_info 加 order_type（0=普通 1=秒杀）
-- 3) degel_admin：平台"营销管理"下加"秒杀场次管理"菜单（见尾部注释，单独执行）
-- id 均为应用侧雪花 ID（非自增），对齐 mk_banner/mk_coupon 先例：
-- 实体裸 Long id + MP assign_id。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. degel_marketing 库：秒杀场次 / 场次秒杀商品
-- 场次 status 仅表示人工启/停；是否"当前场次"由 C 端查询按 start/end_time 现算。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mk_seckill_session (
    id          BIGINT      NOT NULL COMMENT '雪花ID（应用生成）',
    name        VARCHAR(64) NOT NULL COMMENT '场次名称，如 20:00 场',
    start_time  DATETIME    NOT NULL COMMENT '开始时间',
    end_time    DATETIME    NOT NULL COMMENT '结束时间',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0=停用 1=启用',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '数字小靠前',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT  DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_time (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀场次';

CREATE TABLE IF NOT EXISTS mk_seckill_product (
    id             BIGINT       NOT NULL COMMENT '雪花ID',
    session_id     BIGINT       NOT NULL COMMENT '所属场次',
    spu_id         BIGINT       NOT NULL COMMENT '商品 spuId（展示冗余）',
    sku_id         BIGINT       NOT NULL COMMENT 'SKU id',
    seckill_price  DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    seckill_stock  INT          NOT NULL COMMENT '秒杀库存',
    per_limit      INT          NOT NULL DEFAULT 1 COMMENT '每人限购',
    sort           INT          NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT  DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_session (session_id),
    KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场次秒杀商品';

-- ---------------------------------------------------------------------------
-- 2. degel_order 库：订单类型（幂等：先查 information_schema 确认列不存在再执行）
-- ---------------------------------------------------------------------------
-- ALTER TABLE order_info ADD COLUMN order_type TINYINT NOT NULL DEFAULT 0
--     COMMENT '0=普通 1=秒杀' AFTER shop_id;

-- ---------------------------------------------------------------------------
-- 3. degel_admin 库菜单：平台"营销管理"下的"秒杀场次管理"入口。
-- 菜单是 sys_menu 表驱动，仅加 routes.ts 不够。id=215 基于 banner 用了 214（执行前
-- 复核 MAX(id)，若被占则用 max+1）。执行后需删 Redis DB0 缓存 admin:user:info:{username}。
-- ---------------------------------------------------------------------------
-- INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, menu_type, sort, visible, status)
-- VALUES (215, 210, '秒杀场次管理', 'seckill', './Platform/Marketing/Seckill', 'marketing:seckill:list', 'C', 3, 0, 0);
-- INSERT INTO sys_role_menu (role_id, menu_id) SELECT role_id, 215 FROM sys_role_menu WHERE menu_id = 211;
