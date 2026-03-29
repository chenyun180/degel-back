-- ====================================================================
-- 初始数据脚本：菜单 / 角色 / 用户 / 关联数据
-- 与 DataInitRunner 逻辑完全对应，使用固定 ID 方便重置数据库
-- 执行前请先执行 init.sql 建好表结构
-- 注意：DataInitRunner 启动时检测到 sys_user 有数据则自动跳过，不会重复插入
-- ====================================================================

USE degel_admin;

-- ====================================================================
-- 一、菜单数据（固定 ID）
-- 平台端：1-29   店铺端：100-149
-- ====================================================================

-- ---------- 平台端：系统管理目录 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(1,  0, '系统管理',  'system', '',              '',                   'SettingOutlined', 'M', 1, 0, 0, 0),
(2,  1, '用户管理',  'user',   './System/User', 'system:user:list',   'UserOutlined',    'C', 1, 0, 0, 0),
(3,  2, '新增',      '',       '',              'system:user:add',    '',                'F', 1, 0, 0, 0),
(4,  2, '修改',      '',       '',              'system:user:edit',   '',                'F', 2, 0, 0, 0),
(5,  2, '删除',      '',       '',              'system:user:remove', '',                'F', 3, 0, 0, 0),
(6,  1, '角色管理',  'role',   './System/Role', 'system:role:list',   'TeamOutlined',    'C', 2, 0, 0, 0),
(7,  6, '新增',      '',       '',              'system:role:add',    '',                'F', 1, 0, 0, 0),
(8,  6, '修改',      '',       '',              'system:role:edit',   '',                'F', 2, 0, 0, 0),
(9,  6, '删除',      '',       '',              'system:role:remove', '',                'F', 3, 0, 0, 0),
(10, 1, '菜单管理',  'menu',   './System/Menu', 'system:menu:list',   'MenuOutlined',    'C', 3, 0, 0, 0),
(11, 10,'新增',      '',       '',              'system:menu:add',    '',                'F', 1, 0, 0, 0),
(12, 10,'修改',      '',       '',              'system:menu:edit',   '',                'F', 2, 0, 0, 0),
(13, 10,'删除',      '',       '',              'system:menu:remove', '',                'F', 3, 0, 0, 0),
(14, 1, '店铺管理',  'shop',   './System/Shop', 'system:shop:list',   'ShopOutlined',    'C', 4, 0, 0, 0),
(15, 14,'新增',      '',       '',              'system:shop:add',    '',                'F', 1, 0, 0, 0),
(16, 14,'修改',      '',       '',              'system:shop:edit',   '',                'F', 2, 0, 0, 0),
(17, 14,'删除',      '',       '',              'system:shop:remove', '',                'F', 3, 0, 0, 0);

-- ---------- 平台端：商品管理目录 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(20, 0,  '商品管理', 'platform-product',  '',                    '',                       'ShoppingOutlined', 'M', 2, 0, 0, 0),
(21, 20, '商品审核', 'audit',    './Product/Audit',     'product:spu:audit',      'AuditOutlined',    'C', 1, 0, 0, 0),
(22, 20, '类目管理', 'category', './Product/Category',  'product:category:list',  'AppstoreOutlined', 'C', 2, 0, 0, 0),
(23, 22, '新增',     '',         '',                    'product:category:add',   '',                 'F', 1, 0, 0, 0),
(24, 22, '修改',     '',         '',                    'product:category:edit',  '',                 'F', 2, 0, 0, 0),
(25, 22, '删除',     '',         '',                    'product:category:remove','',                 'F', 3, 0, 0, 0);

-- ---------- 店铺端：店铺工作台顶级目录 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(100, 0,   '店铺工作台', 'shop-workspace',     '',                       'shop:dir:workspace', 'HomeOutlined',        'M', 10, 0, 0, 0),
(101, 100, '工作台',     'shop-dashboard',     './Shop/Dashboard',       'shop:dashboard',     'DashboardOutlined',   'C', 0,  0, 0, 0);

-- ---------- 店铺端：商品管理 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(110, 100, '商品管理',   'shop-product-dir',    '',                        'shop:dir:product',    'ShoppingOutlined', 'M', 1, 0, 0, 0),
(111, 110, '商品列表',   'shop-product-list',   './Shop/Product/List',     'shop:product:list',   '',                 'C', 1, 0, 0, 0),
(112, 111, '新增',       '',                    '',                        'shop:product:add',    '',                 'F', 1, 0, 0, 0),
(113, 111, '修改',       '',                    '',                        'shop:product:edit',   '',                 'F', 2, 0, 0, 0),
(114, 111, '删除',       '',                    '',                        'shop:product:remove', '',                 'F', 3, 0, 0, 0),
(115, 111, '提交审核',   '',                    '',                        'shop:product:submit', '',                 'F', 4, 0, 0, 0),
(116, 111, '上下架',     '',                    '',                        'shop:product:onoff',  '',                 'F', 5, 0, 0, 0),
(117, 110, '发布商品',   'shop-product-create', './Shop/Product/Create',   'shop:product:add',    '',                 'C', 2, 1, 0, 0),
(118, 110, '商品分类',   'shop-category',       './Shop/Category',         'shop:category:list',  '',                 'C', 3, 0, 0, 0);

-- ---------- 店铺端：订单管理 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(120, 100, '订单管理',   'shop-order-dir',    '',                       'shop:dir:order',      'OrderedListOutlined', 'M', 2, 0, 0, 0),
(121, 120, '全部订单',   'shop-order-list',   './Shop/Order/List',      'shop:order:list',     '',                    'C', 1, 0, 0, 0),
(122, 121, '详情',       '',                  '',                       'shop:order:detail',   '',                    'F', 1, 0, 0, 0),
(123, 121, '发货',       '',                  '',                       'shop:order:deliver',  '',                    'F', 2, 0, 0, 0),
(124, 121, '导出',       '',                  '',                       'shop:order:export',   '',                    'F', 3, 0, 0, 0),
(125, 120, '待发货',     'shop-order-ship',   './Shop/Order/Ship',      'shop:order:ship',     '',                    'C', 2, 0, 0, 0),
(126, 120, '售后管理',   'shop-aftersale',    './Shop/AfterSale',       'shop:aftersale:list', '',                    'C', 3, 0, 0, 0),
(127, 126, '处理',       '',                  '',                       'shop:aftersale:handle','',                   'F', 1, 0, 0, 0);

-- ---------- 店铺端：数据统计 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(130, 100, '数据统计',   'shop-stats-dir',     '',                       'shop:dir:stats',     'BarChartOutlined', 'M', 3, 0, 0, 0),
(131, 130, '热销榜',     'shop-stats-hot',     './Shop/Stats/Hot',       'shop:stats:hot',     '',                 'C', 1, 0, 0, 0),
(132, 130, '访客榜',     'shop-stats-visitor', './Shop/Stats/Visitor',   'shop:stats:visitor', '',                 'C', 2, 0, 0, 0);

-- ---------- 店铺端：店铺设置 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, status, del_flag) VALUES
(140, 100, '店铺设置',   'shop-setting', '',               'shop:dir:setting', 'SettingOutlined', 'M', 4, 0, 0, 0),
(141, 140, '店铺信息',   'shop-info',    './Shop/Info',    'shop:setting:info','',                'C', 1, 0, 0, 0);

-- 重置自增值（确保后续 DataInitRunner 不与固定 ID 冲突，如果仍使用 Runner 请删除此行）
ALTER TABLE sys_menu AUTO_INCREMENT = 200;


-- ====================================================================
-- 二、角色数据
-- ====================================================================

INSERT INTO sys_role (id, role_name, role_key, role_type, shop_id, sort, status, remark, del_flag) VALUES
(1, '超级管理员', 'admin',    'platform', 0, 1, 0, '拥有所有权限',               0),
(2, '平台运营',   'operator', 'platform', 0, 2, 0, '商品审核与类目管理',          0),
(3, '普通用户',   'common',   'platform', 0, 3, 0, '基本权限',                   0),
(4, '店铺',       'shop',     'shop',     0, 1, 0, '店铺账号，拥有商品管理与店铺信息权限', 0);

ALTER TABLE sys_role AUTO_INCREMENT = 10;


-- ====================================================================
-- 三、角色菜单关联
-- ====================================================================

-- 超级管理员（id=1）：全部平台菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1),(1, 2),(1, 3),(1, 4),(1, 5),
(1, 6),(1, 7),(1, 8),(1, 9),
(1, 10),(1, 11),(1, 12),(1, 13),
(1, 14),(1, 15),(1, 16),(1, 17),
(1, 20),(1, 21),(1, 22),(1, 23),(1, 24),(1, 25);

-- 平台运营（id=2）：商品管理目录 + 审核 + 类目
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 20),(2, 21),(2, 22),(2, 23),(2, 24),(2, 25);

-- 普通用户（id=3）：无菜单权限

-- 店铺角色（id=4）：全部店铺端菜单（商品管理 + 订单管理 + 数据统计 + 店铺信息）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 100),(4, 101),
(4, 110),(4, 111),(4, 112),(4, 113),(4, 114),(4, 115),(4, 116),(4, 117),(4, 118),
(4, 120),(4, 121),(4, 122),(4, 123),(4, 124),(4, 125),(4, 126),(4, 127),
(4, 130),(4, 131),(4, 132),
(4, 140),(4, 141);


-- ====================================================================
-- 四、初始用户（超级管理员）
-- 密码：admin123（BCrypt 加密）
-- ====================================================================

INSERT INTO sys_user (id, username, password, nickname, status, shop_id, del_flag) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超级管理员', 0, 0, 0);

ALTER TABLE sys_user AUTO_INCREMENT = 10;

-- 超级管理员绑定角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ====================================================================
-- 五、商品类目初始数据（服装类，三级）
-- ====================================================================

USE degel_product;

INSERT INTO product_category (id, parent_id, name, sort, icon, status) VALUES
-- 一级：服装
(1, 0, '服装', 1, 'SkinOutlined', 0),
-- 二级
(10, 1, '男装', 1, 'ManOutlined',   0),
(11, 1, '女装', 2, 'WomanOutlined', 0),
(12, 1, '童装', 3, 'SmileOutlined', 0),
(13, 1, '内衣', 4, 'HeartOutlined', 0),
-- 三级：男装
(100, 10, 'T恤',        1, 'TagOutlined', 0),
(101, 10, '衬衫',       2, 'TagOutlined', 0),
(102, 10, '卫衣/套头衫', 3, 'TagOutlined', 0),
(103, 10, '夹克/外套',  4, 'TagOutlined', 0),
(104, 10, '牛仔裤',     5, 'TagOutlined', 0),
(105, 10, '休闲裤',     6, 'TagOutlined', 0),
(106, 10, '西装/正装',  7, 'TagOutlined', 0),
(107, 10, '羽绒服',     8, 'TagOutlined', 0),
-- 三级：女装
(110, 11, '连衣裙',     1, 'TagOutlined', 0),
(111, 11, 'T恤/上衣',   2, 'TagOutlined', 0),
(112, 11, '衬衫/雪纺衫', 3, 'TagOutlined', 0),
(113, 11, '卫衣/绒衫',  4, 'TagOutlined', 0),
(114, 11, '牛仔裤',     5, 'TagOutlined', 0),
(115, 11, '半身裙',     6, 'TagOutlined', 0),
(116, 11, '风衣/外套',  7, 'TagOutlined', 0),
(117, 11, '羽绒服',     8, 'TagOutlined', 0),
(118, 11, '毛衣/针织衫', 9, 'TagOutlined', 0),
-- 三级：童装
(120, 12, '男童上衣',   1, 'TagOutlined', 0),
(121, 12, '女童上衣',   2, 'TagOutlined', 0),
(122, 12, '男童裤子',   3, 'TagOutlined', 0),
(123, 12, '女童裙子',   4, 'TagOutlined', 0),
(124, 12, '儿童外套',   5, 'TagOutlined', 0),
(125, 12, '婴儿服',     6, 'TagOutlined', 0),
-- 三级：内衣
(130, 13, '文胸',       1, 'TagOutlined', 0),
(131, 13, '男士内裤',   2, 'TagOutlined', 0),
(132, 13, '女士内裤',   3, 'TagOutlined', 0),
(133, 13, '保暖内衣',   4, 'TagOutlined', 0),
(134, 13, '家居服/睡衣', 5, 'TagOutlined', 0);

ALTER TABLE product_category AUTO_INCREMENT = 1000;
