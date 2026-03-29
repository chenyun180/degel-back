# E2E 测试规划：微信登录 → 支付全流程

**流程 ID**：E2E-WX-PAY
**覆盖接口**：微信登录 → 浏览商品 → 添加购物车 → 直购下单 → 支付
**系统边界**：degel-app（C端）+ degel-product + degel-order + 微信 jscode2session 接口
**测试类型**：端到端黑盒验证 + 关键断言

---

## 前置条件

| 条件 | 说明 |
|------|------|
| 环境 | 测试环境（test profile），Nacos/MySQL/Redis 均正常 |
| 微信 Mock | 使用 WireMock 拦截 `wx.token-url`，模拟 jscode2session 返回固定 openid |
| 测试数据 | 预置 1 个上架商品（status=1，stock>=5），价格 99.00 元 |
| 用户状态 | 测试 openid 对应用户不存在（测试自动注册路径）或预置已存在用户 |
| 网关 | 测试中绕过网关直接调用 degel-app（或启动 gateway 做完整链路） |

---

## 测试步骤

### Step 1：微信登录（测试自动注册新用户）

**请求**
```
POST /app/auth/wx-login
Content-Type: application/json

{
  "code": "mock-wx-code-001",
  "nickname": "E2E测试用户",
  "avatar": "https://example.com/avatar.png"
}
```

**WireMock 拦截配置**（模拟微信返回）
```json
{
  "openid": "test-openid-e2e-001",
  "session_key": "mock-session-key"
}
```

**断言**
- [ ] HTTP 200
- [ ] `data.token` 不为空，可用 JJWT 解析
- [ ] `data.token` payload 中 `type == "c_end"`
- [ ] `data.token` payload 中 `sub` 为新注册用户的 userId（数字字符串）
- [ ] `data.userId` 与 token sub 一致
- [ ] `data.nickname == "E2E测试用户"`
- [ ] DB 中 `mall_user` 新增一条 openid = "test-openid-e2e-001" 的记录（status=0）

**存储变量**
- `$token`：后续步骤请求头使用
- `$userId`：用于订单归属验证

---

### Step 2：验证 JWT 在网关透传（X-User-Id 注入）

**请求**（通过完整链路，带 token）
```
GET /app/order
Authorization: Bearer $token
```

**断言**
- [ ] HTTP 200（说明网关解析 JWT 成功，X-User-Id 注入，AppSecurityFilter 设置 UserContext）
- [ ] 返回当前用户的订单列表（新用户应为空列表 `records=[]`）

---

### Step 3：查询目标 SKU（确认商品上架且有库存）

**请求**
```
GET /app/product/sku/{skuId}
Authorization: Bearer $token
```

**断言**
- [ ] HTTP 200
- [ ] `data.status == 1`（上架）
- [ ] `data.stock >= 1`
- [ ] `data.price == 99.00`

**存储变量**
- `$skuId`：目标 SKU ID
- `$initialStock`：下单前库存数量（用于 Step 5 库存验证）

---

### Step 4：直购下单

**请求**
```
POST /app/order
Authorization: Bearer $token
Content-Type: application/json

{
  "skuId": $skuId,
  "quantity": 1,
  "addressId": $addressId
}
```

> 前置：需先创建收货地址（`POST /app/address`），存储 `$addressId`

**断言**
- [ ] HTTP 200
- [ ] `data.orderId` 不为空（Long 类型）
- [ ] `data.orderNo` 格式符合 `yyyyMMddHHmmss + userId末4位 + 4位随机数`（22位）
- [ ] `data.payAmount == 99.00`（单件）
- [ ] `data.autoCancelTime` 约为当前时间 + 30 分钟（允许 ±10秒误差）
- [ ] DB `product_sku.stock` 减少 1（`$initialStock - 1`）
- [ ] DB `order_info` 新增一条 status=0 的订单记录

**存储变量**
- `$orderId`：目标订单 ID
- `$orderNo`：订单号（支付流水关联）

---

### Step 5：发起模拟支付（幂等第一次）

**请求**
```
POST /app/pay/$orderId
Authorization: Bearer $token
```

**断言**
- [ ] HTTP 200
- [ ] `data.payLogId` 不为空（Long 类型）
- [ ] `data.orderNo == $orderNo`
- [ ] `data.amount == 99.00`
- [ ] `data.payTime` 不为空，在当前时间 ±5秒内
- [ ] DB `mall_payment_log` 新增一条 direction="pay", status=0, orderId=$orderId 的记录
- [ ] DB `order_info.status` 更新为 1（待发货）
- [ ] DB `order_info.pay_time` 不为空

**存储变量**
- `$payLogId`：支付流水 ID

---

### Step 6：幂等校验 —— 重复支付

**请求**（与 Step 5 完全相同）
```
POST /app/pay/$orderId
Authorization: Bearer $token
```

**断言**
- [ ] HTTP 返回业务错误（code=40018）
- [ ] `msg` 包含 "已支付"
- [ ] DB `mall_payment_log` 中 orderId=$orderId, direction="pay" 的记录数仍为 1（无新增）
- [ ] DB `order_info.status` 仍为 1（未变化）

---

### Step 7：查询支付流水

**请求**
```
GET /app/pay/log?direction=pay&page=1&size=10
Authorization: Bearer $token
```

**断言**
- [ ] HTTP 200
- [ ] `data.records` 至少包含 1 条 direction="pay" 的流水
- [ ] 找到 `id == $payLogId` 的记录，`orderId == $orderId`，`amount == 99.00`

---

## 异常路径测试

### E-01：使用失效/伪造 token 访问

```
GET /app/order
Authorization: Bearer invalid.jwt.token
```
- [ ] HTTP 401 或 403（网关拦截）

### E-02：封禁用户无法登录

- 前置：DB 中设置 `mall_user.status = 1`
- 重新调用 Step 1（相同 openid）
- [ ] HTTP 返回 code=40002（账号已封禁）

### E-03：越权支付他人订单

```
POST /app/pay/$otherUserId_orderId
Authorization: Bearer $token   # 当前用户 token
```
- [ ] HTTP 返回 code=40016（无权操作该订单）

---

## 清理步骤

- 删除测试用户（`DELETE FROM mall_user WHERE openid='test-openid-e2e-001'`）
- 恢复 SKU 库存（`UPDATE product_sku SET stock=$initialStock WHERE id=$skuId`）
- 删除测试订单及流水记录

---

## 测试覆盖率矩阵

| 功能点 | 覆盖 |
|--------|------|
| 微信 jscode2session 调用 | ✅ Step 1 |
| 新用户自动注册 | ✅ Step 1 |
| JWT 签发（type=c_end） | ✅ Step 2 |
| AppSecurityFilter ThreadLocal 注入 | ✅ Step 2 |
| Redisson 锁 + 库存扣减 | ✅ Step 4 |
| 先写 payment_log 再更新订单 | ✅ Step 5 |
| 幂等支付校验 | ✅ Step 6 |
| 越权防护 | ✅ E-03 |
