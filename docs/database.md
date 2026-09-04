# 数据库（按需阅读）

> 由 `CLAUDE.md` 路由。搭建环境、写迁移脚本、直连查数时先读本文。

## 连接信息（开发环境）

- MySQL：`192.168.1.14:3306`，root / 123456
- Redis：localhost:6379（admin/auth/gateway 用作 token 黑名单；degel-app 用 **database 1** 隔离 C 端缓存）
- 本机没有 mysql 客户端，用 JDBC 单文件直连：

```bash
# 驱动
DRIVER=~/.m2/repository/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar
# 写一个含 main 的 Java 文件后：
java -cp ".:$DRIVER" Xxx.java
```

## 库与核心表

| 库 | 归属服务 | 核心表 |
|---|---|---|
| degel_admin | degel-admin | sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu, sys_shop |
| degel_product | degel-product | product_spu, product_sku, product_category |
| degel_order | degel-order | order_info, order_item, order_after_sale |
| degel_app | degel-app | mall_user, mall_address, mall_cart, mall_payment_log |

## 全新环境初始化顺序

1. `sql/init.sql` — degel_admin 库 + 核心表
2. `sql/product_init.sql` — degel_product 库
3. `sql/order_init.sql` — degel_order 库
4. `sql/app_init.sql` — degel_app 库
5. `sql/data_init.sql` — 初始 admin/product/menu 数据

⚠️ 注意：`degel-admin` 的 `DataInitRunner`（ApplicationRunner）在 `sys_user` 为空时会自动初始化菜单、角色（admin/shop）和 admin 账号。菜单 path 必须与前端 `config/routes.ts` 及 `data_init.sql` 保持一致。

## 迁移脚本

仅在需要时执行，如：`migrate_shop_role_simplification.sql`、`role_refactor.sql`、`fix_comment_charset.sql`、`migrate_fix_menu_mismatch.sql`、`migrate_order_item_columns.sql`、`migrate_platform_dashboard.sql`、`migrate_remove_operator_common.sql`。新迁移用描述性命名 `migrate_<feature>_<date>.sql`。

## 写迁移/改数据的注意事项

- 手工补 `sys_role_menu` 数据时，必须连同**祖先目录菜单**一起插入（父目录缺失会导致前端路由整棵子树消失，`assignMenus` 已做后端兜底）
- 所有表几乎都有 `del_flag` 逻辑删除，排查"删了还在"的问题先查 del_flag
