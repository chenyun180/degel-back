# API 地图（按需阅读）

> 由 `CLAUDE.md` 路由。查"某个接口在哪个模块/Controller"、排查涉及面广的问题时读。
> 粒度：Controller + 关键端点。方法级细节直接看代码（Controller 方法都很短）。
> 网关外部路径 = `/{service}/**` 前缀，如 `/admin/user/list`、`/product/spu/list`。

## degel-admin（/admin/**，库 degel_admin）

| Controller | 前缀 | 职责与关键端点 |
|---|---|---|
| SysUserController | /user | 用户 CRUD、`/find/{username}`(供 auth Feign 校验)、`/info`(当前用户+路由+权限)、`/resetPwd/{id}` |
| SysRoleController | /role | 角色 CRUD、`/assignMenus`(菜单授权)、`/menuIds/{roleId}`(回显) |
| SysMenuController | /menu | 菜单树 `/tree`、菜单 CRUD |
| SysShopController | /shop | 店铺 CRUD(平台侧)、`/status` 启停、`/mine`(店铺侧自查自改) |

## degel-auth（/auth/**）

| Controller | 前缀 | 职责 |
|---|---|---|
| TokenController | /token | OAuth2 端点在 `/auth/oauth/token`；本 Controller 仅 `DELETE /token` 注销(写 Redis 黑名单) |

## degel-product（/product/**，库 degel_product）

| Controller | 前缀 | 职责与关键端点 |
|---|---|---|
| ProductSpuController | /spu | SPU CRUD、`/submit/{id}` 提交审核、`/submitBatch` 批量提交审核、`/audit` 平台审核(通过/驳回)、`/toggle-status/{id}` 上下架、`/sku/stock` 改库存；`/list` 的 categoryId 支持大类筛选（含子孙类目） |
| ProductCategoryController | /category | 类目树 `/tree`、类目 CRUD（全局共享，不分店铺） |
| DashboardController | /dashboard | 店铺工作台：`/today-overview`、`/stock-warning`、`/pending-counts` |
| StatsController | /stats | 店铺统计：`/hot-sale` 热销榜、`/visitor-rank` 访客榜 |

## degel-order（/order/**，库 degel_order）

| Controller | 前缀 | 职责与关键端点 |
|---|---|---|
| OrderController | (根) | 管理端订单：`/list` 分页、`/{id}` 详情、`/deliver` 发货(状态 1→2) |
| AfterSaleController | /after-sale | 售后：`/list`、`/handle` 处理、`/confirm-receive` |
| PlatformDashboardController | /platform/dashboard | 平台看板：`/overview` 总览、`/trend` GMV 趋势（仅 admin，网关拦截 shop token） |

## degel-file（/file/**）

| Controller | 前缀 | 职责 |
|---|---|---|
| FileController | /file | `/upload`(返回 objectKey)、`/view/{bucket}/{object}` 图片直链(公开桶免鉴权)、`/presign` 预签名、`/list`、`DELETE` 删除（MinIO 兼容存储） |

## degel-app（/app/**，C 端 BFF，库 degel_app）

| Controller | 前缀 | 职责 |
|---|---|---|
| AuthController | /app/auth | `/wx-login`、`/login`（签发 app JWT） |
| ProductController | /app/product | `/category_tree`、`/list`、`/{spuId}` 商品浏览 |
| CartController | /app/cart | 购物车 |
| OrderController | /app/order | 下单、订单列表/详情、取消(status=0→4)、确认收货(2→3) |
| PayController | /app/pay | 支付发起、支付状态 |
| UserController | /app/user | 地址管理 `/user/address` 等 |
| AfterSaleController | /app/aftersale | C 端售后申请/查询 |
| InnerPayController | /app/inner/pay | 内部退款回调（InnerTokenFilter 保护，禁止暴露公网） |

## 订单状态机（order_info.status）

```
0 待付款 --支付--> 1 待发货 --发货(/order/deliver)--> 2 待收货 --确认收货--> 3 已完成
0 待付款 --取消--> 4 已取消
```

## SPU 审核流（product_spu.audit_status）

```
0 草稿 --提交审核--> 1 待审核 --平台审核--> 2 已通过 / 3 已驳回
```
