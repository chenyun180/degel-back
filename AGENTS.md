# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Degel 商城管理系统后端 - Spring Boot 2.6 + Spring Cloud + Spring Cloud Alibaba 微服务架构的电商管理平台。

The repository now contains both the admin-side services and a C-side BFF service (`degel-app`) for applet/mobile mall flows such as login, product browsing, cart, order, payment, address, and after-sale.

## Technology Stack

- **Language**: Java 8
- **Framework**: Spring Boot 2.6.13
- **Microservices**: Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0
- **Service Registry/Config**: Alibaba Nacos
- **API Gateway**: Spring Cloud Gateway
- **Service Communication**: Spring Cloud OpenFeign + Spring Cloud LoadBalancer
- **Resilience**: Spring Cloud CircuitBreaker + Resilience4j in `degel-app`
- **ORM**: MyBatis Plus 3.5.3.1
- **Authentication**: Spring Security OAuth2 + JWT for admin-side auth; JJWT-based app token for `degel-app`
- **Database**: MySQL
- **Cache/Locking**: Redis + Redisson
- **Utilities**: Hutool 5.8.26, Lombok, Jackson
- **File Storage**: AWS S3 SDK 2.42.2 / MinIO-compatible config in file service
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
# Full build from project root
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Build single module
mvn -pl degel-app -DskipTests package
mvn -pl degel-admin -DskipTests package

# Rebuild shared module first when degel-common changes
mvn -pl degel-common clean install

# Run a service after dependencies are built
# Start the main class: *Application.java in each module
# Example: degel-app/src/main/java/com/degel/app/DegelAppApplication.java
```

## Database Setup

Execute SQL scripts in order when initializing a fresh local environment:

1. `sql/init.sql` - Creates `degel_admin` database and core admin tables.
2. `sql/product_init.sql` - Creates `degel_product` database and product tables.
3. `sql/order_init.sql` - Creates `degel_order` database and order tables.
4. `sql/app_init.sql` - Creates `degel_app` database and C-side tables (`mall_user`, `mall_address`, `mall_cart`, `mall_payment_log`).
5. `sql/data_init.sql` - Inserts initial admin/product/menu data.
6. Apply migration scripts only when needed, for example `sql/migrate_shop_role_simplification.sql`, `sql/role_refactor.sql`, and `sql/fix_comment_charset.sql`.

## Architecture Patterns

### Admin Authentication Flow

1. Client -> Gateway `/auth/oauth/token` (password grant)
2. Gateway routes to `degel-auth`
3. `degel-auth` validates credentials via Feign call to `degel-admin` `/user/find/{username}`
4. Returns JWT access_token + refresh_token
5. Subsequent requests: Client sends `Authorization: Bearer {token}` header
6. Gateway `AuthFilter` validates JWT, checks Redis blacklist, extracts claims
7. Gateway injects `X-User-Id`, `X-User-Name`, `X-Shop-Id` headers to downstream services

> ⚠️ **Issue**: JWT tokens use `JwtTokenStore`, which does not revoke tokens at the OAuth2 token store level. Logout depends on the Gateway Redis blacklist key pattern `auth:blacklist:{jti}`. If a request bypasses Gateway, blacklist enforcement may be skipped.

### C-Side App Authentication Flow

1. Client calls `/app/auth/wx-login` or `/app/auth/login`.
2. `degel-app` issues an app JWT using `degel.app.jwt.secret`.
3. `AppSecurityFilter` handles app-side request authentication.
4. Authenticated user data is held in `UserContext`.
5. Internal callbacks such as `/app/inner/pay/refund` are guarded by `InnerTokenFilter` and `degel.inner.token`.

> ⚠️ **Issue**: App JWT secret and inner-service token currently have defaults in `bootstrap.yml`. Production deployments must provide strong values through environment variables or Nacos config.

### Gateway Routing

- Gateway routes are defined in `degel-gateway/src/main/resources/bootstrap.yml`.
- Main route pattern: `/{service}/**` -> `lb://degel-{service}` with `StripPrefix=1` where configured.
- `degel-app` is exposed through the Gateway route:
  - `Path=/app/**`
  - `uri=lb://degel-app`
- Do not expose `/inner/**` endpoints externally. Gateway `degel.security.internal-urls` should block internal endpoints from public access.

### C-Side BFF Responsibilities

`degel-app` owns mobile/applet-facing orchestration:

- Auth: `/app/auth/wx-login`, `/app/auth/login`
- Product browsing: `/app/product/category/tree`, `/app/product/list`, `/app/product/{spuId}`
- Cart: `/app/cart`
- Order: `/app/order`
- Payment: `/app/pay`
- Address: `/app/user/address`
- After-sale: `/app/aftersale`
- Internal payment refund callback: `/app/inner/pay/refund`

`degel-app` calls product/order services with Feign clients:

- `ProductFeignClient`
- `StockFeignClient`
- `OrderFeignClient`

Feign clients that target the same service must use distinct `contextId` values to avoid Spring bean collisions.

### Configuration Management

- Each service has `bootstrap.yml` for Nacos, datasource, Redis, and service settings.
- Prefer environment variables or Nacos Config Center for environment-specific values.
- Keep local defaults suitable for development only.

> ⚠️ **Issue**: Several `bootstrap.yml` files currently contain intranet IPs and plain-text Redis/MinIO credentials. Do not add more hardcoded secrets. Move credentials to environment variables or Nacos before production use.

### Common Module (`degel-common`)

- `R<T>` - Unified API response wrapper (`code`, `msg`, `data`)
- `BaseEntity` - Base entity with common audit/delete fields
- `Constants` - Global constants
- `core/dto/UserInfo.java` - User info DTO for service communication
- `core/exception/BusinessException.java` - Shared business exception
- `feign/` - Shared Feign client interfaces

### Multi-Tenancy Model

- Platform users use `shop_id=0`; shop users use `shop_id>0`.
- Roles are separated by role type (`platform` / `shop`) in the simplified shop-role model.
- Gateway extracts `shop_id` from admin JWT and forwards it via `X-Shop-Id`.
- C-side users in `degel_app.mall_user` are separate from admin users.

### Data Access Layer

- MyBatis Plus for CRUD operations.
- Mapper pattern: `extends BaseMapper<T>`.
- Service pattern: `extends ServiceImpl<Mapper, Entity> implements IService`.
- Logical delete uses `del_flag` / `delFlag` with `0=active`, `1=deleted`.
- XML mappers live under each module's `src/main/resources/mapper/`.

## Critical Development Notes

### Dependency Management

- **Always rebuild `degel-common` first when modified**:

```bash
mvn -pl degel-common clean install
```

- All services depend on `degel-common`; downstream builds may not see changes until the common module is installed.
- `degel-app` uses Redisson 3.23.4 with `redisson-spring-data-26`; keep this aligned with Spring Boot 2.6.x.

### Java 8 Compatibility

- The project declares `java.version=1.8`; avoid APIs unavailable in Java 8.
- Do not use Java 10+ convenience APIs such as no-arg `Optional.orElseThrow()`.
- Keep test and production code Java 8 compatible.

### Security Considerations

- Admin JWT secret: `degel.security.jwt-secret`
- App JWT secret: `degel.app.jwt.secret`
- Inner service token: `degel.inner.token`
- Gateway whitelist: `degel.security.ignore-urls`
- Gateway internal URL denylist: `degel.security.internal-urls`
- Gateway strips user headers in auth flow; do not trust inbound `X-User-*` headers from external clients.
- Avoid `addAllowedOriginPattern("*")` with `allowCredentials(true)` in production.

> ⚠️ **Issue**: Gateway CORS currently allows broad origins while credentials are enabled. This is convenient for local testing but risky for production. Restrict origins per environment.

### Redis Usage

- Admin/auth/gateway use Redis for token blacklist and shared infrastructure.
- `degel-app` uses Redis for product/category caching and Redisson for payment/order idempotency locks.
- `degel-app` uses Redis database `1` to isolate C-side cache from admin-side data.

### File Storage

- `degel-file` has MinIO-compatible endpoint/access-key/secret-key configuration.
- Do not commit real object-storage credentials.

## Testing Notes

Useful checks:

```bash
git diff --check
mvn -pl degel-app -DskipTests package
mvn -pl degel-app test
mvn clean install -DskipTests
```

Known current test status:

> ⚠️ **Issue**: `mvn -pl degel-app test` has known failures in service/integration tests, including exception type mismatches, Redisson lock mock setup, MyBatis Plus lambda cache setup, and Java 8 API compatibility in tests. Do not claim the full test suite passes unless you have rerun it and verified zero failures.

When adding or fixing behavior, prefer focused tests in the touched module. If a test failure is unrelated and pre-existing, call it out explicitly.

## Code Quality Requirements

When reading or describing existing code, identify and flag issues clearly.

Required annotations:

- Empty/no-op methods
- Configured but unused components
- Non-functional features
- Security vulnerabilities
- Hardcoded secrets or environment-specific infrastructure values
- Missing functionality despite dependencies
- Test failures or known broken verification commands

Format:

> ⚠️ **Issue**: Specific problem and actual impact.

Place critical security/functionality issues at the start of the response, not buried at the end.

## Common Tasks

### Adding a New Microservice

1. Add the module to root `pom.xml` `<modules>`.
2. Add a module `pom.xml` with `degel-common` dependency.
3. Create `*Application.java` with `@SpringBootApplication` and `@EnableDiscoveryClient`.
4. Add `bootstrap.yml` with Nacos, datasource/Redis if needed, and service-specific settings.
5. Register a route in `degel-gateway/src/main/resources/bootstrap.yml`.
6. Rebuild from root with `mvn clean install -DskipTests`, or build the changed modules explicitly.

### Adding a Feign Client

1. Define the interface in the owning module or `degel-common` if shared.
2. Annotate with `@FeignClient(name = "service-name")`.
3. Add a unique `contextId` when multiple Feign clients use the same `name`.
4. Add fallback classes where needed.
5. Rebuild `degel-common` first if the client is shared from that module.

### Adding a Database Migration

1. Create SQL in `sql/`.
2. Use descriptive naming such as `migrate_<feature>_<date>.sql`.
3. Test locally before committing.
4. Document the migration and execution order in the commit or PR.

### Updating App-Side APIs

1. Keep controller routes under `/app/**`.
2. Put orchestration in service implementations, not controllers.
3. Use `UserContext` for authenticated C-side user IDs.
4. Use Feign clients for product/order service calls.
5. Keep internal app endpoints under `/app/inner/**` and protect them with `InnerTokenFilter`.

## IDE Setup

- Install Lombok plugin.
- Enable annotation processing.
- Import as Maven project.
- Set Java SDK to 1.8 or ensure source/target compatibility with Java 8.

## Infrastructure Dependencies

Ensure these are running before starting services:

- Nacos Server
- MySQL
- Redis
- MinIO or compatible object storage when testing file service

Current checked-in config points to an intranet host for some services. For local development, override with environment variables or local config rather than committing machine-specific values.

## Known Issues & Limitations

> ⚠️ **Issue**: JWT token revocation is Gateway-blacklist based. OAuth2 `JwtTokenStore` does not revoke issued JWTs by itself.

> ⚠️ **Issue**: OAuth2 client credentials are hardcoded in `AuthorizationServerConfig` (`client=degel`, `secret=degel_secret`). Externalize before production.

> ⚠️ **Issue**: Several secrets and infrastructure addresses are checked into `bootstrap.yml`. Move them to Nacos or environment variables before production use.

> ⚠️ **Issue**: Full `degel-app` tests are not currently green. Verify and fix tests before relying on them as a release gate.
