# degel-product 模块

商品 SPU/SKU、类目、库存、店铺统计服务（端口 9203，库 degel_product）。仓库级约定见上级 `../CLAUDE.md`。

## SPU 审核流（product_spu.audit_status）

```
0 草稿 --提交审核(PUT /spu/submit/{id})--> 1 待审核 --平台审核(PUT /spu/audit)--> 2 已通过 / 3 已驳回
```

- 提交/上下架是**店铺侧**动作（校验 X-Shop-Id 归属）；audit 是**平台侧**动作（驳回必填 rejectReason）
- 上下架 `/toggle-status/{id}` 与审核状态是两个维度：已通过(2)的商品才能上架

## 核心表

- `product_spu`：标准产品单元（shopId、minPrice、auditStatus、rejectReason）
- `product_sku`：规格单元（specData JSON、price/originalPrice/costPrice、stock、stockWarning）
- `product_category`：类目树，**全局共享不分店铺**（34 条初始数据）

## 接口速查

| Controller | 前缀 | 端点 |
|---|---|---|
| ProductSpuController | /spu | /list、/page（C 端 ES 搜索，降级 MySQL）、/reindex（平台全量重建）、/{id}、POST、PUT、DELETE、/submit/{id}、/audit、/toggle-status/{id}、/sku/stock |
| ProductCategoryController | /category | /tree、/list、CRUD |
| DashboardController | /dashboard | /today-overview、/stock-warning、/pending-counts（店铺工作台） |
| StatsController | /stats | /hot-sale、/visitor-rank（店铺统计） |

## ES 商品搜索（product_spu 索引）

- C 端搜索走 `/spu/page`（ES，IK 分词；ES 故障自动降级 MySQL LIKE，调用方无感知）
- **只索引可见商品**（status=1 且 auditStatus=2）：不可见即从索引删除，可见性内建于数据
- 同步：SPU/SKU 增删改（含订单扣减/回补库存）→ 事务提交后异步同步（`SpuIndexListener`）；
  ES 宕机期间丢的更新靠 `POST /spu/reindex`（平台权限）兜底，`?recreate=true` 删索引重建（换 mapping/分词器用）
- ES 集群 192.168.1.14:9200/9201/9202（部署见 `../docker/es/README.md`）
- ⚠️ 超时预算：ES 连接 1s×3 节点 + 读 2s ≈ 4s，必须小于 degel-app Feign 读超时 5s——改超时前先想清楚这条链

## 注意事项

- 店铺工作台看板（本模块 /dashboard）与平台数据看板（degel-order 的 /platform/dashboard）是两套，别混
- degel-app 通过 `ProductFeignClient`/`StockFeignClient` 调本模块查商品/扣库存，内部端点在 `/inner/**`
- 类目被 degel-app 的 `/app/product/category_tree` 直接复用，改类目结构影响 C 端首页
