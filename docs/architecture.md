# 架构详解（按需阅读）

> 由 `CLAUDE.md` 路由。排查认证/跨服务/网关问题、了解整体架构时先读本文。

## 技术栈

- Java 8 / Spring Boot 2.6.13 / Spring Cloud 2021.0.5 + Alibaba 2021.0.5.0
- Nacos（注册/配置中心，开发环境 localhost:8848）
- Spring Cloud Gateway（端口 9999）+ OpenFeign + LoadBalancer
- MyBatis Plus 3.5.3.1、Redis + Redisson、Hutool 5.8.26、JJWT 0.11.5
- AWS S3 SDK 2.42.2（file 服务 MinIO 兼容）
- C 端韧性：Spring Cloud CircuitBreaker + Resilience4j（degel-app）

## 管理端认证流程

1. Client → Gateway `/auth/oauth/token`（password grant）
2. Gateway 路由到 `degel-auth`
3. `degel-auth` 通过 Feign 调用 `degel-admin` `/user/find/{username}` 校验凭据
4. 返回 JWT access_token + refresh_token（有效期约 2 小时）
5. 后续请求：`Authorization: Bearer {token}`
6. Gateway `AuthFilter` 校验 JWT → 检查 Redis 黑名单 → 提取 claims
7. Gateway 向下游注入 `X-User-Id`、`X-User-Name`、`X-Shop-Id` header

## C 端（degel-app）认证流程

1. Client 调用 `/app/auth/wx-login` 或 `/app/auth/login`
2. `degel-app` 用 `degel.app.jwt.secret` 签发 app JWT
3. `AppSecurityFilter` 处理 app 端请求认证
4. 认证后的用户数据存放在 `UserContext`
5. 内部回调（如 `/app/inner/pay/refund`）由 `InnerTokenFilter` + `degel.inner.token` 守护

## 服务通信

- Feign 客户端定义在所属模块或 `degel-common/src/main/java/com/degel/common/feign/`（共享时）
- 示例：`FileFeignClient` 用于文件服务调用
- 服务通过 Nacos 互相发现，负载均衡由 Spring Cloud LoadBalancer 提供
- **指向同一服务的 Feign 客户端必须使用不同的 `contextId`**，避免 Spring bean 冲突
- degel-app 的 Feign：`ProductFeignClient`、`StockFeignClient`、`OrderFeignClient`（path=/inner/order，带 fallback）

## 网关路由

- 路由定义在 `degel-gateway/src/main/resources/bootstrap.yml`
- 主路由模式：`/{service}/**` → `lb://degel-{service}`，按需配置 `StripPrefix=1`
- `degel-app` 通过 `Path=/app/**` → `lb://degel-app` 暴露
- 不要对外暴露 `/inner/**` 端点；Gateway `degel.security.internal-urls` 阻止内部端点被公网访问
- Gateway 在 auth 流程中会**剥离**用户 header，不要信任来自外部 Client 的入站 `X-User-*` header

## C 端 BFF（degel-app）职责

面向移动/小程序端编排，Controller 一律在 `/app/**` 下，编排逻辑放 service 不放 controller：

- 认证：`/app/auth/*`
- 商品浏览：`/app/product/category_tree|list|{spuId}`
- 购物车：`/app/cart`
- 订单：`/app/order`
- 支付：`/app/pay`；退款回调：`/app/inner/pay/refund`
- 地址：`/app/user/address`；售后：`/app/aftersale`
- C 端用户 ID 通过 `UserContext` 获取

## 文件存储 URL 模型（2026-08 重构）

- **库里只存 objectKey**（`bucket/objectName`，如 `degel-public/uuid_1.png`），**绝不存含 host 的绝对 URL**——host 是环境信息，落库后换环境全部裂图（历史教训）
- 管理端前端：`fileUrl(key)` 工具（`degel-front/src/utils/fileUrl.ts`）→ 相对路径 `/file/view/{key}` 经网关访问，同源零 host
- C 端（degel-app）：`ProductServiceImpl.fileUrl()` 按配置 `degel.app.file-base-url`（默认 `http://localhost:9999`，生产改公网域名/CDN）拼绝对 URL
- 网关白名单：`/file/view/degel-public/` 免鉴权（`<img>` 不带 token）；私有桶仍需 token 或 `/file/presign`
- ⚠️ MinIO endpoint（内网上传地址）与浏览器访问地址是两回事，生产部署时后者必须可达

## 多租户模型

- 平台用户 `shop_id=0`；店铺用户 `shop_id>0`
- Gateway 从 admin JWT 提取 `shop_id`，通过 `X-Shop-Id` header 转发
- Controller 层普遍用 `@RequestHeader(value="X-Shop-Id", defaultValue="0") Long shopId` 做数据隔离
- `degel_app.mall_user` 的 C 端用户与管理端 `sys_user` 相互独立

## 配置管理

- 每个服务有 `bootstrap.yml`（Nacos、datasource、Redis、自身设置）
- 环境相关配置优先用环境变量或 Nacos 配置中心
- 密钥类：`degel.security.jwt-secret`（admin JWT）、`degel.app.jwt.secret`（app JWT）、`degel.inner.token`（内部服务）

## 数据访问约定

- MyBatis Plus CRUD；Mapper `extends BaseMapper<T>`；Service `extends ServiceImpl<Mapper, Entity> implements IService`
- 逻辑删除：`del_flag`（`0=active`, `1=deleted`），BaseEntity 上有 `@TableLogic`，`selectList` 自动过滤
- XML mapper 位于各模块 `src/main/resources/mapper/`

## IDE / 基础设施

- Lombok 插件 + 注解处理必需；Java SDK 设为 1.8
- 本地依赖：Nacos(8848, nacos/nacos)、MySQL(3306)、Redis(6379)；file 服务需 MinIO
