-- 营销轮播图表（degel_marketing 库，平台管理端维护 / C 端首页展示）
-- id 为应用侧雪花 ID（非自增），对齐 mk_coupon 先例：实体裸 Long id + MP assign_id。
-- 定时上下架不做任务扫描：C 端查询按 status + start/end_time 现算（listActive），
-- status 仅表示人工上/下架。
CREATE TABLE IF NOT EXISTS mk_banner (
    id          BIGINT   NOT NULL COMMENT '雪花ID（应用生成）',
    title       VARCHAR(64)  NOT NULL COMMENT '标题（管理端识别用）',
    image       VARCHAR(255) NOT NULL COMMENT '图片 objectKey（公开桶）',
    link_type   TINYINT  NOT NULL DEFAULT 0 COMMENT '0=无跳转 1=内部页面 2=商品详情 3=外部链接',
    link_value  VARCHAR(255) COMMENT '跳转值：页面路径/spuId/https链接',
    sort        INT      NOT NULL DEFAULT 0 COMMENT '数字小靠前',
    status      TINYINT  NOT NULL DEFAULT 1 COMMENT '0=下架 1=上架',
    start_time  DATETIME COMMENT '生效时间（NULL=立即）',
    end_time    DATETIME COMMENT '失效时间（NULL=永久）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT  DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销轮播图';

-- ---------------------------------------------------------------------------
-- 后台菜单（degel_admin 库）：平台"营销管理"下的"轮播图管理"入口。
-- 菜单是 sys_menu 表驱动，仅加 routes.ts 不够。id=214 基于 2026-09-14 的 max(id)=213。
-- 执行后需删 Redis DB0 缓存 admin:user:info:{username} 才生效。
-- ---------------------------------------------------------------------------
-- INSERT INTO `degel_admin`.sys_menu (id, parent_id, menu_name, path, component, perms, menu_type, sort, visible, status)
-- VALUES (214, 210, '轮播图管理', 'banner', './Platform/Marketing/Banner', 'marketing:banner:list', 'C', 2, 0, 0);
-- INSERT INTO `degel_admin`.sys_role_menu (role_id, menu_id) SELECT role_id, 214 FROM `degel_admin`.sys_role_menu WHERE menu_id = 211;
