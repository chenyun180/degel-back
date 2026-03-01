-- ============================================================
-- 修正因连接字符集问题导致的 COMMENT 乱码
-- ============================================================

-- ========== degel_admin ==========
USE degel_admin;

ALTER TABLE sys_user
  MODIFY COLUMN username    VARCHAR(64)   NOT NULL COMMENT '用户名',
  MODIFY COLUMN password    VARCHAR(255)  NOT NULL COMMENT '密码(BCrypt)',
  MODIFY COLUMN nickname    VARCHAR(64)   DEFAULT '' COMMENT '昵称',
  MODIFY COLUMN email       VARCHAR(100)  DEFAULT '' COMMENT '邮箱',
  MODIFY COLUMN phone       VARCHAR(20)   DEFAULT '' COMMENT '手机号',
  MODIFY COLUMN avatar      VARCHAR(500)  DEFAULT '' COMMENT '头像URL',
  MODIFY COLUMN status      TINYINT       DEFAULT 0 COMMENT '0=正常 1=停用',
  MODIFY COLUMN shop_id     BIGINT        DEFAULT 0 COMMENT '所属店铺ID，0=平台',
  MODIFY COLUMN create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag    TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE sys_role
  MODIFY COLUMN role_name   VARCHAR(64)   NOT NULL COMMENT '角色名称',
  MODIFY COLUMN role_key    VARCHAR(100)  NOT NULL COMMENT '角色标识',
  MODIFY COLUMN role_type   VARCHAR(10)   DEFAULT 'platform' COMMENT 'platform=平台角色 shop=店铺角色',
  MODIFY COLUMN shop_id     BIGINT        DEFAULT 0 COMMENT '店铺ID，平台角色为0',
  MODIFY COLUMN status      TINYINT       DEFAULT 0 COMMENT '0=正常 1=停用',
  MODIFY COLUMN remark      VARCHAR(500)  DEFAULT '' COMMENT '备注',
  MODIFY COLUMN create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag    TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE sys_menu
  MODIFY COLUMN menu_name   VARCHAR(50)   NOT NULL COMMENT '菜单名称',
  MODIFY COLUMN parent_id   BIGINT        DEFAULT 0 COMMENT '父菜单ID，0=顶级',
  MODIFY COLUMN sort        INT           DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN path        VARCHAR(200)  DEFAULT '' COMMENT '路由地址',
  MODIFY COLUMN component   VARCHAR(200)  DEFAULT '' COMMENT '组件路径',
  MODIFY COLUMN perms       VARCHAR(100)  DEFAULT '' COMMENT '权限标识',
  MODIFY COLUMN icon        VARCHAR(100)  DEFAULT '' COMMENT '菜单图标',
  MODIFY COLUMN menu_type   CHAR(1)       NOT NULL COMMENT 'M=目录 C=菜单 F=按钮',
  MODIFY COLUMN visible     TINYINT       DEFAULT 0 COMMENT '0=显示 1=隐藏',
  MODIFY COLUMN status      TINYINT       DEFAULT 0 COMMENT '0=正常 1=停用',
  MODIFY COLUMN create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag    TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE sys_shop
  MODIFY COLUMN shop_name     VARCHAR(100)  NOT NULL COMMENT '店铺名称',
  MODIFY COLUMN contact_name  VARCHAR(64)   DEFAULT '' COMMENT '联系人',
  MODIFY COLUMN contact_phone VARCHAR(20)   DEFAULT '' COMMENT '联系电话',
  MODIFY COLUMN status        TINYINT       DEFAULT 0 COMMENT '0=正常 1=停用',
  MODIFY COLUMN expire_time   DATETIME      DEFAULT NULL COMMENT '有效期',
  MODIFY COLUMN create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag      TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

-- ========== degel_product ==========
USE degel_product;

ALTER TABLE product_spu
  MODIFY COLUMN shop_id        BIGINT        NOT NULL COMMENT '所属店铺',
  MODIFY COLUMN category_id    BIGINT        NOT NULL COMMENT '分类ID',
  MODIFY COLUMN name           VARCHAR(200)  NOT NULL COMMENT '商品名称',
  MODIFY COLUMN subtitle       VARCHAR(200)  DEFAULT '' COMMENT '副标题',
  MODIFY COLUMN description    TEXT          COMMENT '商品简介',
  MODIFY COLUMN main_image     VARCHAR(500)  DEFAULT '' COMMENT '主图URL',
  MODIFY COLUMN images         VARCHAR(2000) DEFAULT '' COMMENT '轮播图，逗号分隔',
  MODIFY COLUMN detail_content LONGTEXT      COMMENT '商品详情富文本',
  MODIFY COLUMN sale_count     INT           DEFAULT 0 COMMENT '累计销量',
  MODIFY COLUMN keyword        VARCHAR(200)  DEFAULT '' COMMENT '搜索关键词',
  MODIFY COLUMN view_count     INT           DEFAULT 0 COMMENT '浏览次数',
  MODIFY COLUMN audit_status   TINYINT       DEFAULT 0 COMMENT '0=待审核 1=审核通过 2=审核拒绝',
  MODIFY COLUMN reject_reason  VARCHAR(500)  DEFAULT '' COMMENT '审核拒绝原因',
  MODIFY COLUMN auditor_id     BIGINT        DEFAULT NULL COMMENT '审核人ID',
  MODIFY COLUMN audit_time     DATETIME      DEFAULT NULL COMMENT '审核时间',
  MODIFY COLUMN status         TINYINT       DEFAULT 0 COMMENT '0=下架 1=上架',
  MODIFY COLUMN create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag       TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE product_sku
  MODIFY COLUMN spu_id         BIGINT        NOT NULL COMMENT '所属SPU',
  MODIFY COLUMN sku_code       VARCHAR(100)  DEFAULT '' COMMENT '商家货号',
  MODIFY COLUMN spec_data      JSON          COMMENT '规格JSON，如{"颜色":"黑色","容量":"256G"}',
  MODIFY COLUMN price          DECIMAL(10,2) NOT NULL COMMENT '销售价',
  MODIFY COLUMN original_price DECIMAL(10,2) DEFAULT NULL COMMENT '划线价',
  MODIFY COLUMN cost_price     DECIMAL(10,2) DEFAULT NULL COMMENT '成本价',
  MODIFY COLUMN stock          INT           NOT NULL DEFAULT 0 COMMENT '库存',
  MODIFY COLUMN stock_warning  INT           DEFAULT 0 COMMENT '库存预警阈值',
  MODIFY COLUMN weight         DECIMAL(10,3) DEFAULT 0 COMMENT '重量(克)',
  MODIFY COLUMN image          VARCHAR(500)  DEFAULT '' COMMENT 'SKU图片',
  MODIFY COLUMN status         TINYINT       DEFAULT 1 COMMENT '0=禁用 1=启用',
  MODIFY COLUMN create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag       TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE product_category
  MODIFY COLUMN parent_id  BIGINT        DEFAULT 0 COMMENT '父分类ID，0=顶级',
  MODIFY COLUMN name       VARCHAR(50)   NOT NULL COMMENT '分类名称',
  MODIFY COLUMN sort       INT           DEFAULT 0 COMMENT '排序',
  MODIFY COLUMN icon       VARCHAR(255)  DEFAULT '' COMMENT '分类图标',
  MODIFY COLUMN status     TINYINT       DEFAULT 0 COMMENT '0=禁用 1=启用',
  MODIFY COLUMN create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag   TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

-- ========== degel_order ==========
USE degel_order;

ALTER TABLE order_info
  MODIFY COLUMN order_no         VARCHAR(32)   NOT NULL COMMENT '订单号',
  MODIFY COLUMN user_id          BIGINT        NOT NULL COMMENT '买家ID',
  MODIFY COLUMN shop_id          BIGINT        NOT NULL COMMENT '店铺ID',
  MODIFY COLUMN total_amount     DECIMAL(10,2) NOT NULL COMMENT '商品总金额',
  MODIFY COLUMN freight_amount   DECIMAL(10,2) DEFAULT 0 COMMENT '运费',
  MODIFY COLUMN discount_amount  DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
  MODIFY COLUMN pay_amount       DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  MODIFY COLUMN status           TINYINT       DEFAULT 0 COMMENT '0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=售后中',
  MODIFY COLUMN pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
  MODIFY COLUMN ship_time        DATETIME      DEFAULT NULL COMMENT '发货时间',
  MODIFY COLUMN receive_time     DATETIME      DEFAULT NULL COMMENT '收货时间',
  MODIFY COLUMN cancel_time      DATETIME      DEFAULT NULL COMMENT '取消时间',
  MODIFY COLUMN receiver_name    VARCHAR(50)   DEFAULT '' COMMENT '收货人姓名',
  MODIFY COLUMN receiver_phone   VARCHAR(20)   DEFAULT '' COMMENT '收货人电话',
  MODIFY COLUMN receiver_address VARCHAR(500)  DEFAULT '' COMMENT '收货地址',
  MODIFY COLUMN remark           VARCHAR(500)  DEFAULT '' COMMENT '买家备注',
  MODIFY COLUMN express_company  VARCHAR(50)   DEFAULT '' COMMENT '快递公司',
  MODIFY COLUMN express_no       VARCHAR(50)   DEFAULT '' COMMENT '快递单号',
  MODIFY COLUMN auto_cancel_time DATETIME      DEFAULT NULL COMMENT '自动取消时间',
  MODIFY COLUMN create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag         TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';

ALTER TABLE order_item
  MODIFY COLUMN order_id    BIGINT        NOT NULL COMMENT '订单ID',
  MODIFY COLUMN spu_id      BIGINT        NOT NULL COMMENT '商品SPU ID',
  MODIFY COLUMN sku_id      BIGINT        NOT NULL COMMENT '商品SKU ID',
  MODIFY COLUMN spu_name    VARCHAR(200)  DEFAULT '' COMMENT '商品名称（快照）',
  MODIFY COLUMN sku_spec    VARCHAR(500)  DEFAULT '' COMMENT 'SKU规格描述（快照）',
  MODIFY COLUMN sku_image   VARCHAR(500)  DEFAULT '' COMMENT '商品图片（快照）',
  MODIFY COLUMN price       DECIMAL(10,2) NOT NULL COMMENT '单价（快照）',
  MODIFY COLUMN quantity    INT           NOT NULL COMMENT '购买数量',
  MODIFY COLUMN total_amount DECIMAL(10,2) NOT NULL COMMENT '小计',
  MODIFY COLUMN create_time DATETIME      DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE order_after_sale
  MODIFY COLUMN order_id       BIGINT        NOT NULL COMMENT '订单ID',
  MODIFY COLUMN order_item_id  BIGINT        DEFAULT NULL COMMENT '订单项ID，NULL=整单退',
  MODIFY COLUMN user_id        BIGINT        NOT NULL COMMENT '申请用户ID',
  MODIFY COLUMN shop_id        BIGINT        NOT NULL COMMENT '店铺ID',
  MODIFY COLUMN type           TINYINT       NOT NULL COMMENT '1=仅退款 2=退货退款',
  MODIFY COLUMN status         TINYINT       DEFAULT 0 COMMENT '0=待处理 1=同意 2=拒绝 3=已完成',
  MODIFY COLUMN reason         VARCHAR(500)  DEFAULT '' COMMENT '退款原因',
  MODIFY COLUMN refund_amount  DECIMAL(10,2) DEFAULT 0 COMMENT '申请退款金额',
  MODIFY COLUMN express_company VARCHAR(50)  DEFAULT '' COMMENT '退货快递公司',
  MODIFY COLUMN express_no     VARCHAR(50)   DEFAULT '' COMMENT '退货快递单号',
  MODIFY COLUMN merchant_remark VARCHAR(500) DEFAULT '' COMMENT '商家回复',
  MODIFY COLUMN create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  MODIFY COLUMN update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  MODIFY COLUMN del_flag       TINYINT       DEFAULT 0 COMMENT '0=未删除 1=已删除';
