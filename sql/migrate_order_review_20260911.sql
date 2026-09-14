-- ====================================================================
-- Migration: 订单评价系统
-- 描述：
--   1. degel_order 新增 order_review 评价表（订单已完成 status=3 后可评，
--      每个 order_item 仅可评一次，uk_order_item 唯一约束兜底）
--   2. degel_product.product_spu 新增评分冗余列（rating_avg/rating_count，
--      评价写入后由 degel-order 重算并经 /inner/spu/rating 回写，列表/详情零聚合成本）
-- 回滚：DROP TABLE degel_order.order_review;
--       ALTER TABLE degel_product.product_spu DROP COLUMN rating_avg, DROP COLUMN rating_count;
-- ====================================================================

USE degel_order;

CREATE TABLE IF NOT EXISTS order_review (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL COMMENT '订单ID',
    order_item_id BIGINT       NOT NULL COMMENT '订单明细ID（一明细一评）',
    user_id       BIGINT       NOT NULL COMMENT '评价人（mall_user.id）',
    shop_id       BIGINT       NOT NULL COMMENT '店铺ID（下单时冗余，店铺回复/过滤用）',
    spu_id        BIGINT       NOT NULL COMMENT '商品SPU（下单时快照）',
    sku_id        BIGINT       DEFAULT NULL COMMENT '商品SKU',
    spu_name      VARCHAR(200) DEFAULT '' COMMENT '商品名快照（商品后续改名不影响评价展示）',
    sku_spec      VARCHAR(200) DEFAULT '' COMMENT '规格快照',
    star          TINYINT      NOT NULL COMMENT '星级 1-5',
    content       VARCHAR(500) DEFAULT '' COMMENT '评价内容',
    images        VARCHAR(2000) DEFAULT '' COMMENT '晒图URL JSON数组（一期未开放上传，预留）',
    reply         VARCHAR(500) DEFAULT NULL COMMENT '商家回复',
    reply_time    DATETIME     DEFAULT NULL COMMENT '回复时间',
    status        TINYINT      DEFAULT 0 COMMENT '0=已发布 1=已隐藏（预留平台屏蔽）',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag      TINYINT      DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item (order_item_id),
    KEY idx_spu (spu_id, status, create_time),
    KEY idx_shop (shop_id, create_time),
    KEY idx_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单评价';

USE degel_product;

ALTER TABLE product_spu
    ADD COLUMN rating_avg   DECIMAL(2,1) DEFAULT NULL COMMENT '平均评分 0.0-5.0（评价写入后冗余回写）',
    ADD COLUMN rating_count INT          DEFAULT 0    COMMENT '评价数';
