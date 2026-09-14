-- 商品收藏表（C 端用户资产，degel_app 库）
-- 取消收藏为物理删除：唯一键 uk_user_spu 与逻辑删除组合存在"取消后再收藏撞唯一键"的坑，
-- 收藏无审计需求，物理删最简（重新收藏即新记录，favoriteTime 刷新）。
CREATE TABLE IF NOT EXISTS mall_favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    spu_id      BIGINT   NOT NULL COMMENT '商品SPU ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT  DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_spu (user_id, spu_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏表';
