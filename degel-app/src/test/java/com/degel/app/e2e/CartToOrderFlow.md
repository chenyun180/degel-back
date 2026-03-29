# E2E 流程：购物车到下单（cart-to-order）

## 流程概述

已登录用户将多个 SKU 加入购物车，勾选商品预览结算，选择收货地址，成功创建订单。

## 前置条件

- 用户已登录，持有有效 C 端 JWT（token）
- userId 已知
- 至少 2 个 SKU 库存充足（Mock ProductFeignClient）
- 用户有至少 1 条收货地址（Mock 或预插入）

## 步骤

### Step 1：加入商品 A 到购物车

**请求：**
```
POST /app/cart
Header: Authorization: Bearer {token}
Body: { "skuId": 2001, "quantity": 1 }
```

**预期：**
- HTTP 200
- mall_cart 新增记录（user_id + sku_id + quantity=1）

### Step 2：加入商品 B 到购物车

**请求：**
```
POST /app/cart
Header: Authorization: Bearer {token}
Body: { "skuId": 2002, "quantity": 3 }
```

**预期：**
- HTTP 200
- mall_cart 新增第二条记录

### Step 3：查看购物车列表

**请求：**
```
GET /app/cart
Header: Authorization: Bearer {token}
```

**预期：**
- HTTP 200
- items 列表包含 2 条记录
- 每条 subtotal = price × quantity
- invalid=false（商品在售）

### Step 4：修改商品 B 数量

**请求：**
```
PUT /app/cart/{cartItemId_B}
Header: Authorization: Bearer {token}
Body: { "quantity": 2 }
```

**预期：**
- HTTP 200
- mall_cart 中对应记录 quantity 更新为 2

### Step 5：勾选预览结算

**请求：**
```
POST /app/cart/check
Header: Authorization: Bearer {token}
Body: { "cartIds": [cartItemId_A, cartItemId_B] }
```

**预期：**
- HTTP 200
- items 列表 2 条
- totalAmount = priceA×1 + priceB×2
- freightAmount = 0
- payAmount = totalAmount

### Step 6：新增收货地址

**请求：**
```
POST /app/user/address
Header: Authorization: Bearer {token}
Body: {
  "name": "张三",
  "phone": "13800138000",
  "province": "广东省",
  "city": "深圳市",
  "district": "南山区",
  "detail": "科技园南区 8 栋 101"
}
```

**预期：**
- HTTP 200
- mall_address 新增记录，is_default=1（首条地址自动为默认）

**保存：** addressId

### Step 7：创建订单（购物车模式）

**请求：**
```
POST /app/order
Header: Authorization: Bearer {token}
Body: {
  "cartIds": [cartItemId_A, cartItemId_B],
  "addressId": {addressId},
  "remark": "请尽快发货"
}
```

**Mock：**
- ProductFeignClient.batchGetSku → 2 个 SKU status=1，库存充足
- Redisson tryLock → 成功
- ProductFeignClient.deductStock → 成功
- OrderFeignClient.createOrder → 返回 orderId

**预期：**
- HTTP 200
- 返回 orderId、orderNo、payAmount = totalAmount
- autoCancelTime ≈ now + 30min
- order_info 新增 status=0 记录
- order_item 新增 2 条记录（快照 spuName/skuSpec/price）
- mall_cart 中 cartItemId_A / cartItemId_B → del_flag=1

## 验证点汇总

| 步骤 | 数据库验证 | 响应验证 |
|------|-----------|----------|
| 加购×2 | 2 条 cart 记录 | 200 OK |
| 修改数量 | cart.quantity 更新 | 200 OK |
| 新增地址 | address 记录创建 | addressId 返回 |
| 创建订单 | order_info + 2 order_item | orderId 返回 |
| 购物车清理 | cart del_flag=1 | — |

## 异常场景

- 库存不足时（Mock deductStock 返回失败）→ 期望 40012 库存不足
- cartIds 包含其他用户的购物车项 → 期望报错或自动过滤
- addressId 不属于当前用户 → 期望 40013 收货地址不存在

## 回滚/清理

清理 mall_address / mall_cart / order_info / order_item 中的测试数据。
