# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Degel 商城管理系统后端 - Spring Boot 2.6 + Spring Cloud + Spring Cloud Alibaba 微服务架构的电商管理平台。

## Technology Stack

- **Language**: Java 8
- **Framework**: Spring Boot 2.6.13
- **Microservices**: Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0
- **Service Registry/Config**: Alibaba Nacos (localhost:8848)
- **API Gateway**: Spring Cloud Gateway (port 9999)
- **Service Communication**: Spring Cloud OpenFeign + LoadBalancer
- **ORM**: MyBatis Plus 3.5.3
- **Authentication**: Spring Security OAuth2 + JWT (JJWT 0.11.5)
- **Database**: MySQL (localhost:3306)
- **Cache**: Redis (localhost:6379)
- **Utilities**: Hutool 5.8.26, Lombok
- **File Storage**: AWS S3 SDK 2.42
- **Build Tool**: Maven 3.8+

## Module Structure

| Module | Responsibility | Port | Database |
|--------|---------------|------|----------|
| degel-gateway | API Gateway, JWT auth, routing | 9999 | - |
| degel-auth | OAuth2 token issuance | 9200 | - |
| degel-admin | User/Role/Menu/Shop management | 9201 | degel_admin |
| degel-product | Product SPU/SKU, categories | 9203 | degel_product |
| degel-order | Order processing | - | degel_order |
| degel-file | File upload (AWS S3) | - | - |
| degel-common | Shared entities, DTOs, Feign clients | - | - |

## Build & Run Commands

```bash
# Full build (from project root)
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Build single module
cd degel-admin && mvn clean install

# Run a service (after building degel-common first)
# Start the main class: *Application.java in each module
# Example: degel-admin/src/main/java/com/degel/admin/DegelAdminApplication.java
```

## Database Setup

Execute SQL scripts in order:
1. `sql/init.sql` - Creates degel_admin database and core tables (sys_user, sys_role, sys_menu, sys_shop, etc.)
2. `sql/product_init.sql` - Creates degel_product database and product tables
3. `sql/order_init.sql` - Creates degel_order database and order tables
4. `sql/data_init.sql` - Inserts initial data (admin user, roles, menus)

## Architecture Patterns

### Authentication Flow
1. Client → Gateway `/auth/oauth/token` (password grant)
2. Gateway routes to degel-auth service
3. degel-auth validates credentials via Feign call to degel-admin `/user/find/{username}`
4. Returns JWT access_token + refresh_token
5. Subsequent requests: Client sends `Authorization: Bearer {token}` header
6. Gateway's AuthFilter validates JWT, checks Redis blacklist, extracts claims
7. Gateway injects `X-User-Id`, `X-User-Name`, `X-Shop-Id` headers to downstream services

⚠️ **Security Issue**: JWT tokens use `JwtTokenStore` which does NOT support token revocation. The `removeAccessToken()` method is a no-op. Logout functionality relies on Redis blacklist (key: `auth:blacklist:{jti}`) checked in Gateway's AuthFilter.

### Service Communication
- Feign clients defined in `degel-common/src/main/java/com/degel/common/feign/`
- Example: `FileFeignClient` for file service calls
- Services discover each other via Nacos
- Load balancing via Spring Cloud LoadBalancer

### Configuration Management
- Each service has `bootstrap.yml` for Nacos connection
- Application configs can be centralized in Nacos Config Center
- Gateway config includes route definitions and security settings

### Common Module (degel-common)
- `R<T>` - Unified API response wrapper (code, msg, data)
- `BaseEntity` - Base entity with id, create_time, update_time, del_flag
- `Constants` - Global constants (status codes, role types, menu types)
- `core/dto/UserInfo.java` - User info DTO for service communication
- `core/exception/BusinessException.java` - Custom business exception
- `feign/` - Feign client interfaces

### Multi-Tenancy Model
- Platform (shop_id=0) vs Shop (shop_id>0) separation
- Roles: `platform` type (global) vs `shop` type (per-shop)
- Users belong to either platform or a specific shop
- Gateway extracts shop_id from JWT and passes via `X-Shop-Id` header

### Data Access Layer
- MyBatis Plus for CRUD operations
- Base mapper: `extends BaseMapper<T>`
- Service layer: `extends ServiceImpl<Mapper, Entity> implements IService`
- Logical delete via `del_flag` field (0=active, 1=deleted)

## Critical Development Notes

### Dependency Management
- **ALWAYS** rebuild `degel-common` first when modified: `cd degel-common && mvn clean install`
- All services depend on degel-common, changes won't reflect until reinstalled

### Security Considerations
- JWT secret: `degel.security.jwt-secret` (configured in bootstrap.yml)
- Gateway whitelist: `degel.security.ignore-urls` (e.g., `/auth/oauth/token`)
- Internal endpoints: `degel.security.internal-urls` (blocked from external access)
- Gateway strips `X-User-*` headers from incoming requests to prevent forgery

### Gateway Routing
- Routes defined in `degel-gateway/src/main/resources/bootstrap.yml`
- Path pattern: `/{service}/**` → `lb://degel-{service}`
- StripPrefix filter removes first path segment before forwarding

### Code Quality Requirements
When reading or describing existing code, you MUST identify and flag issues:

⚠️ **Required Annotations**:
- Empty/no-op methods (e.g., JwtTokenStore.removeAccessToken does nothing)
- Configured but unused components (e.g., Redis dependency but no caching logic)
- Non-functional features (e.g., logout endpoint that doesn't invalidate tokens)
- Security vulnerabilities (e.g., non-revocable tokens, hardcoded secrets)
- Missing functionality despite dependencies

**Format**:
> ⚠️ **Issue**: [Specific problem and actual impact]

**Placement**: Flag critical security/functionality issues at the START of your response, not buried at the end.

## Common Tasks

### Adding a New Microservice
1. Create module in root pom.xml `<modules>`
2. Add module pom.xml with degel-common dependency
3. Create `*Application.java` with `@SpringBootApplication` + `@EnableDiscoveryClient`
4. Add `bootstrap.yml` with Nacos config
5. Register route in degel-gateway's bootstrap.yml
6. Rebuild: `mvn clean install` from root

### Adding a Feign Client
1. Define interface in `degel-common/src/main/java/com/degel/common/feign/`
2. Annotate with `@FeignClient(name = "service-name")`
3. Rebuild degel-common: `cd degel-common && mvn clean install`
4. Use in consuming service by autowiring the interface

### Database Migration
1. Create migration SQL in `sql/` directory
2. Use descriptive naming: `migrate_<feature>_<date>.sql`
3. Test locally before committing
4. Document in commit message

## IDE Setup

- Install Lombok plugin (required for @Data, @RequiredArgsConstructor, etc.)
- Enable annotation processing
- Import as Maven project
- Set Java SDK to 1.8 (or compatible)

## Infrastructure Dependencies

Ensure these are running before starting services:
- Nacos Server: http://localhost:8848/nacos (default: nacos/nacos)
- MySQL: localhost:3306
- Redis: localhost:6379

## Known Issues & Limitations

⚠️ **JWT Token Revocation**: Logout does not invalidate tokens at the OAuth2 server level. Tokens remain valid until expiry (2 hours). Revocation is handled via Redis blacklist in Gateway only.

⚠️ **Client Credentials**: OAuth2 client credentials are hardcoded in `AuthorizationServerConfig` (client: degel, secret: degel_secret). Consider externalizing to Nacos config for production.

⚠️ **JWT Secret**: Shared secret key is in bootstrap.yml files. Rotate regularly and use environment variables in production.
