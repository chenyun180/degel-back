# degel-order 模块

订单/售后/平台看板服务（库 degel_order）。仓库级约定见上级 `../CLAUDE.md`。

## 订单状态机（order_info.status）

```
0 待付款 --支付--> 1 待发货 --发货(PUT /deliver)--> 2 待收货 --确认收货--> 3 已完成
0 待付款 --取消--> 4 已取消（仅 status=0 可取消）
```

- 状态变更的实际发起方多在 `degel-app`（C 端支付/取消/收货，走 `OrderFeignClient` path=`/inner/order`）；本模块的 `/deliver` 是管理端(店铺)发货 1→2
- 时间字段随状态落库：payTime / shipTime / receiveTime / cancelTime

## 核心表

- `order_info`：订单主表（orderNo、userId、shopId、totalAmount/freightAmount/discountAmount/payAmount、status）
- `order_item`：明细（快照 spu_id/sku_id/spu_name/sku_spec/price/quantity，**下单时快照，不随商品修改变化**）
- `order_after_sale`：售后单

## 接口速查

| Controller | 前缀 | 端点 |
|---|---|---|
| OrderController | (根) | GET /list、GET /{id}、PUT /deliver |
| AfterSaleController | /after-sale | GET /list、PUT /handle、PUT /confirm-receive |
| PlatformDashboardController | /platform/dashboard | GET /overview、GET /trend（仅 admin token，网关 admin-urls 拦截 shop token，e2e 有用例覆盖） |
| ShopReviewController | /review | GET /list、PUT /reply（店铺评价管理，X-Shop-Id 归属校验，一评仅一回复） |
| InnerReviewController | /inner/review | POST、GET /spu/{id}、GET /mine、GET /reviewed-item-ids（C 端 BFF 专用） |

## 评价（order_review，2026-09-12）

- 评价挂 order_item 维度（uk_order_item 一明细一评），仅订单 status=3 可评；spu_name/sku_spec 为快照
- 评分冗余：写入后全量重算该 SPU 的 avg/count，经 Feign 回写 degel-product `/inner/spu/rating`（product_spu.rating_avg/rating_count）；回写失败仅记日志，下次评价自动重算覆盖
- 自动确认收货：`PUT /inner/order/auto-receive?shipDays=7`（幂等 UPDATE WHERE status=2），由 degel-app 的 OrderAutoReceiveTask 每小时触发

## 注意事项

- 平台看板（PlatformDashboard*/OrderStatsMapper）是管理端数据聚合，与店铺工作台看板（在 degel-product 的 /dashboard）**不是同一套**
- e2e 曾造过 `order_no LIKE 'TEST%'` 的假订单（已清理）；再看到可疑数据先查 order_no 前缀和 spu_id 是否存在
