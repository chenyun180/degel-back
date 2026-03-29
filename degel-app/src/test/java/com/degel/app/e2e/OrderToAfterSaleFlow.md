# E2E 流程：订单到售后（order-to-aftersale）

## 流程概述

用户完成支付并确认收货后，发起售后退款申请，商家审核通过，系统完成退款流水写入并更新订单状态为已退款。

## 前置条件

- 用户已登录，持有有效 C 端 JWT（token）
- 存在一笔已完成的订单（status=3）
  - order_info: orderId=5001, status=3, pay_amount=299.00
  - mall_payment_log: direction=pay, orderId=5001, amount=299.00
- OrderFeignClient / InnerPayController 可用

## 步骤

### Step 1：查看订单详情（确认已完成）

**请求：**
```
GET /app/order/5001
Header: Authorization: Bearer {token}
```

**预期：**
- HTTP 200
- status = 3（已完成）
- payAmount = 299.00

### Step 2：申请退款（售后）

**请求：**
```
POST /app/aftersale
Header: Authorization: Bearer {token}
Body: { "orderId": 5001, "reason": "商品质量问题，与描述不符" }
```

**预期：**
- HTTP 200
- order_after_sale 新增记录：
  - orderId=5001, userId=当前用户, status=0（待审核）
  - type=1（仅退款）, refundAmount=299.00
  - reason="商品质量问题，与描述不符"

**保存：** afterSaleId

### Step 3：查看退款列表

**请求：**
```
GET /app/aftersale?page=1&size=10
Header: Authorization: Bearer {token}
```

**预期：**
- HTTP 200
- 列表中包含 afterSaleId 对应的记录
- status=0（待审核）
- orderNo 正确关联

### Step 4：查看退款详情

**请求：**
```
GET /app/aftersale/{afterSaleId}
Header: Authorization: Bearer {token}
```

**预期：**
- HTTP 200
- status=0（待审核）
- refundAmount=299.00
- paymentLog 字段为空（status != 1，不查退款流水）

### Step 5：商家审核通过（内部接口调用模拟）

**说明：** 商家端在 degel-order 服务执行，通过 Feign 调用 degel-app 内部接口完成退款。
此步骤模拟 Feign 调用链：
1. degel-order: PUT /order/admin/aftersale/{id}/handle（action=agree）
2. degel-order → degel-app: POST /app/inner/pay/refund（X-Inner-Token 鉴权）
3. degel-order → degel-order: UPDATE order_info.status=5

**直接测试内部退款接口：**
```
POST /app/inner/pay/refund
Header: X-Inner-Token: degel-inner-service-token-2024
Body: {
  "userId": {userId},
  "orderId": 5001,
  "orderNo": "ORD2024010100001",
  "amount": 299.00
}
```

**预期：**
- HTTP 200
- 返回 refundLogId（Long）
- mall_payment_log 新增记录：direction=refund, amount=299.00, status=0

**模拟 order_after_sale 状态更新：**
- status 更新为 1（已同意）
- order_info.status 更新为