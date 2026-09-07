# 已知问题清单（按需阅读）

> 由 `CLAUDE.md` 路由。排查问题前先扫一眼，避免重复踩坑。修复后请把对应条目删掉或标记 FIXED。

## 安全类（生产前必须处理）

- ⚠️ **JWT 无法真正吊销**：管理端 OAuth2 用 `JwtTokenStore`，`removeAccessToken()` 是空操作。注销依赖 Gateway 的 Redis 黑名单（key: `auth:blacklist:{jti}`）。logout 后 token 在有效期内（约 2 小时）依然可用；请求若绕过 Gateway，黑名单校验也会被跳过。
- ⚠️ **OAuth2 client 凭据硬编码**在 `AuthorizationServerConfig`（`client=degel`, `secret=degel_secret`），e2e 用例也依赖它。
- ⚠️ **多个密钥与基础设施地址入库在 `bootstrap.yml`**：App JWT secret、inner token、Redis/MinIO 凭据均有默认值。生产必须用环境变量或 Nacos 提供强值，共享密钥需定期轮换。
- ⚠️ **Gateway CORS**：`addAllowedOriginPattern("*")` 与 `allowCredentials(true)` 同时启用，生产需按环境收紧来源。
- ⚠️ **入站 header 伪造面**：Gateway 会剥离外部 `X-User-*` header，但绕过网关直连服务的路径没有这层保护（内网内调用时注意）。

## 测试类

- ⚠️ **degel-app 测试未全绿**：service/集成测试存在已知失败（异常类型不匹配、Redisson lock mock、MyBatis Plus lambda cache、Java 8 API 兼容性）。不要在未重跑确认前声称测试套件通过；把它当发布门禁前需先修复。

## 历史坑（已修复，留档防复发）

- **角色分配权限丢父目录**（2026-08 已修复）：antd Tree 非严格模式 `onCheck` 不含半选父目录 + 后端先删后插 → 父目录丢失 → 前端路由整树消失。修复：前端提交 checked+halfChecked、回显只传叶子；后端 `assignMenus` 自动补全祖先。
- **全局店铺角色连坐**：所有店铺账号共用 `role_key='shop'` 的全局角色，改它影响全部店铺。已加"内置"标记+警告+禁删禁改 roleKey。

## 2026-09-06 优惠券一期新增

- **c_end token 可穿透网关 admin-urls**：AuthFilter 对 C 端令牌不执行 admin-urls 平台角色校验直接放行，`/marketing/platform/**`（平台券管理）可被 C 端 token 命中；marketing 侧 X-Shop-Id==0 校验只是弱兜底（c_end 请求该 header 缺省也是 0）。根治需改网关 AuthFilter。
- **售后退款流水死代码**：`/app/inner/pay/refund`（InnerPayController）注释称由 degel-order 审核后调用，但无任何 FeignClient 指向它——售后 agree 后退款流水从不落库，而详情页会去查。优惠券退回（2→4）不受影响（挂在 degel-order handle agree 的 MarketingFeignClient）。
- **degel-order/product 的 /inner/** 无 token 校验**（仅靠网关不路由隐式保护）；marketing 的 /inner/ 已带 InnerTokenFilter 校验，标准不统一。

## 2026-09-06 二期补充

- **/marketing/shop/**（店铺券管理）同样受 c_end token 穿透影响**：ShopCouponController 对 X-Shop-Id<=0 一律拒绝（c_end 请求该 header 缺省 0），比 platform 的兜底严格；但根治仍需网关 AuthFilter 对 c_end 令牌执行角色拦截（同 2026-09-06 一期条目）。
- **拆单回滚的子单取消**依赖 degel-order 的 updateInnerStatus（UPDATE 无 status=0 条件，仅靠 app 层 Redisson 锁互斥——一期已知问题），子单落库到回滚在同一请求内完成，无用户并发窗口，风险可控。

## 2026-09-06 三期补充

- **补贴报表售后不冲减**：平台「本月平台补贴」/ 店铺「本月店铺补贴」按已支付口径（status IN (1,2,3,5)）统计，整单退款后补贴不回冲，数值略偏高。精确对账需 mk_subsidy_ledger 流水（设计 §6 预留，未实施）。
- **分摊折扣券出资比例是近似**：基于创建端"预期减免额"拆分，每单按比例分实算优惠，非逐单精算（设计 §4.1 口径）。
- **店铺看板 today-overview 空壳（既有）**：degel-product DashboardService.getTodayOverview 的 GMV/订单数恒 0（数据在 degel_order 库）；三期补贴数走 order 新端点 /order/shop/dashboard/subsidy-summary 绕开此问题，但看板其余指标仍空——待专项重构。
