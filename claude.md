# CLAUDE.md

Degel 商城后端 - Spring Boot 2.6 + Spring Cloud 微服务（Java 8）。管理端服务 + C 端 BFF（degel-app）。

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

认证：管理端 OAuth2+JWT（网关 `AuthFilter` 注入 `X-User-Id`/`X-Shop-Id`）；C 端独立 app JWT。多租户：平台 `shop_id=0`，店铺 `shop_id>0`。

## Build & Run（硬规则）

```bash
# 必须 JDK 8（本机默认 JDK 21 会报 Lombok 编译错误）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_333.jdk/Contents/Home

mvn clean install -DskipTests          # 完整构建
mvn -pl degel-common clean install     # ⚠️ 修改 degel-common 后必须先执行，否则下游看不到变更
```

启动：运行各模块 `*Application.java` 主类（通常由 IDE 启动，别 kill IDE 起的进程）。

基础设施：Nacos(8848)、MySQL(192.168.1.14:3306)、Redis(6379)。

## 硬性约束

- **Java 8 兼容**：禁用 Java 9+ API（如无参 `Optional.orElseThrow()`）
- 逻辑删除 `del_flag`（`@TableLogic`，selectList 自动过滤）
- 生产前必须处理的安全问题清单 → `docs/known-issues.md`

## 代码规范（诚实性）

阅读、描述或引用已有代码时，**必须同时指出其中存在的问题**，不得只做中立描述。以下情况必须用 `⚠️ **问题**：[说明]` 格式主动标注：空/无效逻辑、配置了但未使用、看起来正常但实际不生效、安全漏洞、硬编码密钥、依赖引入但功能缺失。影响安全或核心功能的问题放在回复**开头**。发现问题当场提出，不等追问。

## 按需阅读（做对应任务前先读）

- 排查认证/跨服务/网关问题、了解架构细节 → 读 `docs/architecture.md`
- 查接口在哪个 Controller、模块 API 全貌、订单状态机、审核流 → 读 `docs/api-map.md`
- 建环境、写数据库迁移、直连查数 → 读 `docs/database.md`
- 排查已知问题、生产前安全加固 → 读 `docs/known-issues.md`
- 新增微服务/Feign/迁移/C端API 的步骤手册 → 读 `docs/playbooks.md`
- 深入某个模块时，先看该模块目录下是否有自己的 CLAUDE.md（degel-admin / degel-order / degel-product 已有）

## 模块内文档

各模块私有知识（业务规则、坑）放在模块自己的 `CLAUDE.md`（进入该模块目录时会自动加载），不要写回本文件。
