CREATE DATABASE IF NOT EXISTS degel_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE degel_product;

-- ========== 商品类目表 ==========
CREATE TABLE IF NOT EXISTS product_category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       DEFAULT 0 COMMENT '父类目ID，0=顶级',
    name        VARCHAR(50)  NOT NULL COMMENT '类目名称',
    sort        INT          DEFAULT 0 COMMENT '排序',
    icon        VARCHAR(255) DEFAULT '' COMMENT '类目图标URL',
    status      TINYINT      DEFAULT 0 COMMENT '0=启用 1=停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT      DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品类目表';

-- ========== 商品SPU表 ==========
CREATE TABLE IF NOT EXISTS product_spu (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id       BIGINT        NOT NULL COMMENT '所属店铺ID',
    category_id   BIGINT        NOT NULL COMMENT '类目ID',
    name          VARCHAR(200)  NOT NULL COMMENT '商品名称',
    description   TEXT          COMMENT '商品描述',
    main_image    VARCHAR(500)  DEFAULT '' COMMENT '主图URL',
    images        VARCHAR(2000) DEFAULT '' COMMENT '轮播图URLs，逗号分隔',
    audit_status  TINYINT       DEFAULT 0 COMMENT '0=草稿 1=待审核 2=审核通过 3=已驳回',
    reject_reason VARCHAR(500)  DEFAULT '' COMMENT '驳回理由',
    auditor_id    BIGINT        DEFAULT NULL COMMENT '审核人ID',
    audit_time    DATETIME      DEFAULT NULL COMMENT '审核时间',
    status        TINYINT       DEFAULT 0 COMMENT '上下架：0=下架 1=上架',
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag      TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    KEY idx_shop_id (shop_id),
    KEY idx_category_id (category_id),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SPU表';

-- ========== SPU 字段补充（迁移，如已存在请跳过） ==========
ALTER TABLE product_spu
  ADD COLUMN subtitle VARCHAR(200) DEFAULT '' COMMENT '副标题' AFTER name,
  ADD COLUMN detail_content LONGTEXT COMMENT '商品详情富文本' AFTER images,
  ADD COLUMN sale_count INT DEFAULT 0 COMMENT '累计销量' AFTER detail_content,
  ADD COLUMN keyword VARCHAR(200) DEFAULT '' COMMENT '搜索关键词' AFTER sale_count,
  ADD COLUMN view_count INT DEFAULT 0 COMMENT '浏览次数' AFTER keyword;

-- ========== 商品SKU表 ==========
CREATE TABLE IF NOT EXISTS product_sku (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT        NOT NULL COMMENT '所属SPU',
    sku_code        VARCHAR(100)  DEFAULT '' COMMENT '商家货号',
    spec_data       JSON          COMMENT '规格JSON',
    price           DECIMAL(10,2) NOT NULL COMMENT '销售价',
    original_price  DECIMAL(10,2) DEFAULT NULL COMMENT '划线价',
    cost_price      DECIMAL(10,2) DEFAULT NULL COMMENT '成本价',
    stock           INT           NOT NULL DEFAULT 0 COMMENT '库存',
    stock_warning   INT           DEFAULT 0 COMMENT '库存预警阈值',
    weight          DECIMAL(10,3) DEFAULT 0 COMMENT '重量(克)',
    image           VARCHAR(500)  DEFAULT '' COMMENT 'SKU图片',
    status          TINYINT       DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';
