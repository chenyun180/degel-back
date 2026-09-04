# 操作手册（按需阅读）

> 由 `CLAUDE.md` 路由。做对应类型任务前先读，照步骤走。

## 新增一个微服务

1. 根 `pom.xml` 的 `<modules>` 添加模块
2. 添加模块 `pom.xml`，含 `degel-common` 依赖
3. 创建 `*Application.java`：`@SpringBootApplication` + `@EnableDiscoveryClient`
4. 创建 `bootstrap.yml`：Nacos、按需 datasource/Redis、服务自身设置
5. `degel-gateway/src/main/resources/bootstrap.yml` 注册路由
6. 从根目录 `mvn clean install -DskipTests`，或显式构建改动模块

## 新增 Feign 客户端

1. 接口定义在所属模块；跨模块共享放 `degel-common/src/main/java/com/degel/common/feign/`
2. `@FeignClient(name = "服务名")`；**同一 name 的多个客户端必须加唯一 `contextId`**
3. 按需添加 fallback 类
4. 共享客户端从 degel-common 引用时，先 `mvn -pl degel-common clean install`

## 新增数据库迁移

1. `sql/` 下创建脚本，命名 `migrate_<feature>_<date>.sql`
2. 提交前本地测试
3. commit/PR 中记录迁移及执行顺序
4. 涉及 `sys_role_menu` 的补数，记得带祖先目录菜单（见 docs/database.md）

## 更新 C 端（degel-app）API

1. Controller 路由保持在 `/app/**` 下
2. 编排逻辑放 service 实现，不放 controller
3. 用 `UserContext` 获取已认证的 C 端用户 ID
4. 调 product/order 服务用现有 Feign 客户端
5. 内部端点保持在 `/app/inner/**` 下，由 `InnerTokenFilter` + `degel.inner.token` 保护

## 构建/验证检查

```bash
git diff --check
mvn -pl degel-app -DskipTests package
mvn clean install -DskipTests
```
