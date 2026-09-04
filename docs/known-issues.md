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
