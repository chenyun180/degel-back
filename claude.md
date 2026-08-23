# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Degel 商城管理系统后端 - Spring Boot 2.6 + Spring Cloud + Spring Cloud Alibaba 微服务架构的电商管理平台。

仓库同时包含管理端（admin-side）服务和 C 端 BFF 服务（`degel-app`），后者负责小程序/移动端商城流程：登录、商品浏览、购物车、订单、支付、地址、售后。

## Technology Stack

- **Language**: Java 8
- **Framework**: Spring Boot 2.6.13
- **Microservices**: Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0
- **Service Registry/Config**: Alibaba Nacos（开发环境 `localhost:8848`）
- **API Gateway**: Spring Cloud Gateway（端口 9999）
- **Service Communication**: Spring Cloud OpenFeign + Spring Cloud LoadBalancer
- **Resilience**: Spring Cloud CircuitBreaker + Resilience4j（在 `degel-app` 中）
- **ORM**: MyBatis Plus 3.5.3.1
- **Authentication**: 管理端使用 Spring Security OAuth2 + JWT（JJWT 0.11.5）；`degel-app` 使用基于 JJWT 的 app token
- **Database**: MySQL（开发环境 `localhost:3306`）
- **Cache/Locking**: Redis（开发环境 `localhost:6379`）+ Redisson
- **Utilities**: Hutool 5.8.26, Lombok, Jackson
- **File Storage**: AWS S3 SDK 2.42.2 / file service 中的 MinIO 兼容配置
- **Build Tool**: Maven 3.8+

## Module Structure

| Module | Responsibility | Port | Database |
|--------|---------------|------|----------|
| degel-gateway | API Gateway, JWT auth, routing | 9999 | - |
| degel-auth | OAuth2 token issuance | 9200 | - |
| degel-admin | User/Role/Menu/Shop management | 9201 | degel_admin |
| degel-product | Product SPU/SKU, categories, stock | 9203 | degel_product |
| degel-order | Order processing | - | degel_order |
| degel-file | File upload/storage | - | - |
| degel-app | C-side BFF for mobile/applet mall flows | 9205 | degel_app |
| degel-common | Shared entities, DTOs, Feign clients | - | - |

## Build & Run Commands

```bash
# 从项目根目录完整构建
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 构建单个模块
mvn -pl degel-app -DskipTests package
mvn -pl degel-admin -DskipTests package

# 修改 degel-common 后，必须先重新构建 common 模块
mvn -pl degel-common clean install
# 等价写法：cd degel-common && mvn clean install

# 依赖构建完成后启动某服务：运行各模块下的 *Application.java 主类
# 例：degel-app/src/main/java/com/degel/app/DegelAppApplication.java
# 例：degel-admin/src/main/java/com/degel/admin/DegelAdminApplication.java
```

## Database Setup

初始化全新本地环境时，按顺序执行 SQL 脚本：

1. `sql/init.sql` - 创建 `degel_admin` 数据库及核心 admin 表（sys_user, sys_role, sys_menu, sys_shop 等）。
2. `sql/product_init.sql` - 创建 `degel_product` 数据库及商品表。
3. `sql/order_init.sql` - 创建 `degel_order` 数据库及订单表。
4. `sql/app_init.sql` - 创建 `degel_app` 数据库及 C 端表（`mall_user`, `mall_address`, `mall_cart`, `mall_payment_log`）。
5. `sql/data_init.sql` - 插入初始 admin/product/menu 数据。
6. 仅在需要时执行迁移脚本，例如 `sql/migrate_shop_role_simplification.sql`、`sql/role_refactor.sql`、`sql/fix_comment_charset.sql`。

## Architecture Patterns

### Admin Authentication Flow

1. Client → Gateway `/auth/oauth/token`（password grant）
2. Gateway 路由到 `degel-auth`
3. `degel-auth` 通过 Feign 调用 `degel-admin` `/user/find/{username}` 校验凭据
4. 返回 JWT access_token + refresh_token
5. 后续请求：Client 发送 `Authorization: Bearer {token}` header
6. Gateway `AuthFilter` 校验 JWT，检查 Redis 黑名单，提取 claims
7. Gateway 向下游服务注入 `X-User-Id`、`X-User-Name`、`X-Shop-Id` header

> ⚠️ **问题**：JWT token 使用 `JwtTokenStore`，在 OAuth2 token store 层面无法吊销 token，其 `removeAccessToken()` 是空操作，什么都不做。注销依赖 Gateway 的 Redis 黑名单（key: `auth:blacklist:{jti}`）。若请求绕过 Gateway，黑名单校验会被跳过；且 logout 后 token 在有效期内（约 2 小时）依然可用，注销功能实际不完整。

### C-Side App Authentication Flow

1. Client 调用 `/app/auth/wx-login` 或 `/app/auth/login`。
2. `degel-app` 使用 `degel.app.jwt.secret` 签发 app JWT。
3. `AppSecurityFilter` 处理 app 端请求认证。
4. 认证后的用户数据存放在 `UserContext`。
5. 内部回调（如 `/app/inner/pay/refund`）由 `InnerTokenFilter` + `degel.inner.token` 守护。

> ⚠️ **问题**：App JWT secret 与 inner-service token 当前在 `bootstrap.yml` 中有默认值。生产部署必须通过环境变量或 Nacos 配置提供强值。

### Service Communication

- Feign 客户端定义在所属模块或 `degel-common/src/main/java/com/degel/common/feign/`（共享时）。
- 示例：`FileFeignClient` 用于文件服务调用。
- 服务通过 Nacos 互相发现，负载均衡由 Spring Cloud LoadBalancer 提供。

### Gateway Routing

- 路由定义在 `degel-gateway/src/main/resources/bootstrap.yml`。
- 主路由模式：`/{service}/**` → `lb://degel-{service}`，按需配置 `StripPrefix=1`。
- `degel-app` 通过 Gateway 路由暴露：
  - `Path=/app/**`
  - `uri=lb://degel-app`
- 不要对外暴露 `/inner/**` 端点。Gateway `degel.security.internal-urls` 应阻止内部端点被公网访问。
- Gateway 在 auth 流程中会剥离用户 header，不要信任来自外部 Client 的入站 `X-User-*` header。

### C-Side BFF Responsibilities

`degel-app` 负责面向移动/小程序端的编排：

- 认证：`/app/auth/wx-login`、`/app/auth/login`
- 商品浏览：`/app/product/category_tree`、`/app/product/list`、`/app/product/{spuId}`
- 购物车：`/app/cart`
- 订单：`/app/order`
- 支付：`/app/pay`
- 地址：`/app/user/address`
- 售后：`/app/aftersale`
- 内部支付退款回调：`/app/inner/pay/refund`

`degel-app` 通过 Feign 客户端调用 product/order 服务：

- `ProductFeignClient`
- `StockFeignClient`
- `OrderFeignClient`

指向同一服务的 Feign 客户端必须使用不同的 `contextId`，以避免 Spring bean 冲突。

### Configuration Management

- 每个服务都有 `bootstrap.yml`，含 Nacos、datasource、Redis 及服务自身设置。
- 环境相关配置优先使用环境变量或 Nacos 配置中心。
- 本地默认值应仅适用于开发。

> ⚠️ **问题**：多个 `bootstrap.yml` 当前包含内网 IP 与明文 Redis/MinIO 凭据。不要再新增硬编码密钥，生产前必须迁移到环境变量或 Nacos。

### Common Module (degel-common)

- `R<T>` - 统一 API 响应包装（`code`, `msg`, `data`）
- `BaseEntity` - 基础实体，含 id, create_time, update_time, del_flag 等公共审计/删除字段
- `Constants` - 全局常量（状态码、角色类型 platform/shop、菜单类型等）
- `core/dto/UserInfo.java` - 服务间通信的用户信息 DTO
- `core/exception/BusinessException.java` - 共享业务异常
- `feign/` - 共享 Feign 客户端接口

### Multi-Tenancy Model

- 平台用户使用 `shop_id=0`；店铺用户使用 `shop_id>0`。
- 在简化的店铺-角色模型中，按角色类型（`platform` / `shop`）区分角色。
- Gateway 从 admin JWT 提取 `shop_id`，通过 `X-Shop-Id` header 转发。
- `degel_app.mall_user` 中的 C 端用户与管理端用户相互独立。

### Data Access Layer

- 使用 MyBatis Plus 进行 CRUD。
- Mapper 模式：`extends BaseMapper<T>`。
- Service 模式：`extends ServiceImpl<Mapper, Entity> implements IService`。
- 逻辑删除使用 `del_flag` / `delFlag`，`0=active`, `1=deleted`。
- XML mapper 位于各模块 `src/main/resources/mapper/`。

## Critical Development Notes

### Dependency Management

- **修改 `degel-common` 后必须先重新构建**：

```bash
mvn -pl degel-common clean install
```

- 所有服务依赖 `degel-common`；未重新 install 前，下游构建看不到变更。
- `degel-app` 使用 Redisson 3.23.4 与 `redisson-spring-data-26`；需与 Spring Boot 2.6.x 保持对齐。

### Java 8 Compatibility

- 项目声明 `java.version=1.8`；避免使用 Java 8 没有的 API。
- 不要使用 Java 10+ 的便捷 API，例如无参 `Optional.orElseThrow()`。
- 测试与生产代码均需保持 Java 8 兼容。

### Security Considerations

- Admin JWT secret: `degel.security.jwt-secret`
- App JWT secret: `degel.app.jwt.secret`
- Inner service token: `degel.inner.token`
- Gateway 白名单: `degel.security.ignore-urls`（如 `/auth/oauth/token`）
- Gateway 内部 URL 黑名单: `degel.security.internal-urls`（阻止外部访问）
- Gateway 在 auth 流程中剥离用户 header，不要信任来自外部 Client 的入站 `X-User-*` header。
- 生产环境避免 `addAllowedOriginPattern("*")` 与 `allowCredentials(true)` 同时使用。

> ⚠️ **问题**：Gateway CORS 当前在启用 credentials 的同时放开了宽泛来源。本地调试方便但生产有风险，应按环境收紧来源。

### Redis Usage

- Admin/auth/gateway 用 Redis 做 token 黑名单与共享基础设施。
- `degel-app` 用 Redis 做商品/分类缓存，用 Redisson 做支付/订单幂等锁。
- `degel-app` 使用 Redis database `1`，将 C 端缓存与管理端数据隔离。

### File Storage

- `degel-file` 含 MinIO 兼容的 endpoint/access-key/secret-key 配置。
- 不要提交真实的对象存储凭据。

## Testing Notes

常用检查：

```bash
git diff --check
mvn -pl degel-app -DskipTests package
mvn -pl degel-app test
mvn clean install -DskipTests
```

当前已知测试状态：

> ⚠️ **问题**：`mvn -pl degel-app test` 在 service/集成测试中存在已知失败，包括异常类型不匹配、Redisson lock mock 设置、MyBatis Plus lambda cache 设置、以及测试中的 Java 8 API 兼容性问题。除非已重新运行并确认零失败，否则不要声称完整测试套件通过。

新增或修复行为时，优先在所改模块做聚焦测试。若失败与本次无关且为既有问题，需明确指出。

## Code Quality Requirements

阅读、描述或引用已有代码时，**必须同时指出其中存在的问题**，不得只做中立描述。

必须主动标注的情况：

- 空/无效方法（如 `JwtTokenStore.removeAccessToken()` 什么都不做）
- 配置了但实际未使用的组件（如引入 Redis 依赖却没有缓存逻辑）
- 看起来正常但实际不生效的功能（如 logout 后 token 仍有效）
- 安全漏洞（如无法吊销的 token、硬编码密钥、伪造的用户上下文）
- 硬编码密钥或环境相关的基础设施值
- 依赖已引入但功能缺失
- 测试失败或已知失效的校验命令

标注格式：

> ⚠️ **问题**：[具体问题及实际影响]

影响安全或核心功能的问题，放在回复**开头**，不要埋在末尾。

### 反例（禁止）
描述 `TokenController.logout` 时只说：
> "退出登录时调用了 `tokenStore.removeAccessToken(accessToken)`"

### 正例（要求）
> "`TokenController.logout` 调用了 `tokenStore.removeAccessToken(accessToken)`"
> ⚠️ **问题**：当前使用的是 `JwtTokenStore`，其 `removeAccessToken` 是空操作，什么都不做。logout 后 token 在有效期内依然可以正常使用，注销功能实际无效。

## Common Tasks

### Adding a New Microservice

1. 在根 `pom.xml` 的 `<modules>` 中添加模块。
2. 添加模块 `pom.xml`，含 `degel-common` 依赖。
3. 创建 `*Application.java`，标注 `@SpringBootApplication` + `@EnableDiscoveryClient`。
4. 添加 `bootstrap.yml`，含 Nacos、按需的 datasource/Redis 及服务自身设置。
5. 在 `degel-gateway/src/main/resources/bootstrap.yml` 注册路由。
6. 从根目录 `mvn clean install -DskipTests`，或显式构建改动模块。

### Adding a Feign Client

1. 在所属模块定义接口；共享则放在 `degel-common`。
2. 标注 `@FeignClient(name = "service-name")`。
3. 多个 Feign 客户端使用同一 `name` 时，添加唯一 `contextId`。
4. 按需添加 fallback 类。
5. 若客户端从 `degel-common` 共享，先重新构建 `degel-common`。

### Adding a Database Migration

1. 在 `sql/` 创建 SQL。
2. 使用描述性命名，如 `migrate_<feature>_<date>.sql`。
3. 提交前本地测试。
4. 在 commit 或 PR 中记录迁移及执行顺序。

### Updating App-Side APIs

1. Controller 路由保持在 `/app/**` 下。
2. 编排逻辑放在 service 实现，不要放 controller。
3. 使用 `UserContext` 获取已认证的 C 端用户 ID。
4. 调用 product/order 服务使用 Feign 客户端。
5. 内部 app 端点保持在 `/app/inner/**` 下，用 `InnerTokenFilter` 保护。

## IDE Setup

- 安装 Lombok 插件（@Data, @RequiredArgsConstructor 等必需）。
- 启用注解处理（annotation processing）。
- 作为 Maven 项目导入。
- Java SDK 设为 1.8（或保证 source/target 与 Java 8 兼容）。

## Infrastructure Dependencies

启动服务前确保以下组件在运行：

- Nacos Server: http://localhost:8848/nacos（默认 nacos/nacos）
- MySQL: localhost:3306
- Redis: localhost:6379
- 测试 file service 时需要 MinIO 或兼容的对象存储

当前已入库的配置部分指向内网主机。本地开发请用环境变量或本地配置覆盖，不要提交机器相关值。

## Known Issues & Limitations

> ⚠️ **问题**：JWT token 吊销基于 Gateway 黑名单。OAuth2 `JwtTokenStore` 本身不会吊销已签发的 JWT，logout 后 token 在有效期内（约 2 小时）依然有效。

> ⚠️ **问题**：OAuth2 client 凭据硬编码在 `AuthorizationServerConfig`（`client=degel`, `secret=degel_secret`）。生产前需外置。

> ⚠️ **问题**：多个密钥与基础设施地址入库在 `bootstrap.yml`。生产前需迁移到 Nacos 或环境变量。JWT 共享密钥应定期轮换并在生产使用环境变量。

> ⚠️ **问题**：完整 `degel-app` 测试当前未全绿。将其作为发布门禁前需先验证并修复。
