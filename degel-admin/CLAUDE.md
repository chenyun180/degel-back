# degel-admin 模块

用户/角色/菜单/店铺管理服务（端口 9201，库 degel_admin）。仓库级约定见上级 `../CLAUDE.md`。

## 权限模型（改本模块前必读）

- 表关系：`sys_user` —(`sys_user_role`)— `sys_role` —(`sys_role_menu`)— `sys_menu`
- 菜单类型：`M` 目录 / `C` 菜单 / `F` 按钮（`Constants.MENU_TYPE_*`）
- 登录后 `/user/info` 返回 `routers`（由 `SysMenuServiceImpl.getRoutersByUserId` 按 `parent_id=0` 递归建树）+ `perms`

### 全局店铺角色（易踩坑）

- **所有店铺账号共用一个内置角色**：`role_key='shop'`、`role_type='shop'`、`shop_id=0`（当前 id=10）
- 改这个角色的菜单权限会**立即影响全部店铺账号**
- 内置角色保护（`SysRoleServiceImpl`）：`admin`/`shop` 禁止删除、禁止改 roleKey

### 菜单授权的关键约束

`sys_role_menu` 中的父目录记录**不能缺失**，否则 `getRoutersByUserId` 建树时整个子树消失（表现为"账号菜单全没了"）。三层防护已就位，别破坏：

1. 前端回显只传叶子节点给 Tree（防父子联动多勾）
2. 前端提交 checked + halfChecked
3. 后端 `assignMenus` 的 `completeAncestorMenus()` 自动补全祖先链

店铺角色只能分配 `shop:` 开头的 perms（`assignMenus` 内校验）。

## 其他要点

- `DataInitRunner`：`sys_user` 为空时自动初始化全部菜单、admin/shop 角色、admin 账号（admin/admin123）；菜单 path 必须与前端 `config/routes.ts` 一致
- 多租户隔离靠 `X-Shop-Id` header（网关注入）：`shopId>0` 的请求只能操作本店铺数据，各 Service 方法末尾的 `shopId` 参数就是这个用途
- `/user/find/{username}` 是给 `degel-auth` Feign 校验凭据用的，属内部依赖，改接口名要同步 auth 模块

## 工作流（Flowable 6.8.0，2026-10-07 接入）

- 只引 `flowable-spring-boot-starter-process`（BPMN 引擎）；**不要引 flowable-rest / flowable-ui**——其接口不认 `X-Shop-Id`，会绕过多租户隔离，一律自写 Controller
- 7.x 需 JDK17+Boot3，本项目只能 6.8.x
- 引擎表与业务表同库 `degel_admin`，`database-schema-update: true` 启动自动建；`bootstrap.yml` 两处必需配置别删：
  - datasource url 的 `nullCatalogMeansCurrent=true`（否则同实例他库有 ACT_ 表时误判已建表）
  - `spring.liquibase.enabled: false`（event-registry 传递引入 liquibase-core，Boot 自动配置会找不到 changelog 启动失败）
- 身份映射：`assignee`=sys_user.id，`candidateGroups`=sys_role.role_key，IDM 已关（无 ACT_ID_* 表）
- ⚠️ 店铺侧若将来有任务，`candidateGroups="shop"` 会让**全部店铺**都看到（共用 shop 角色），必须再按 shopId（流程变量/tenantId）过滤并在办理前校验
- 业务挂接约定：业务表存 `process_instance_id` + 状态；流程终态由 `flow/delegate/*` 在 `taskService.complete` 同一事务内回写，业务代码不直接改终态
- 流程定义放 `resources/processes/*.bpmn20.xml`，改后重启自动部署新版本，在途实例按旧版本走完
- 新增依赖后用 `./degel.sh` 启动前要删 `.cp-cache/admin.txt`（classpath 缓存不会自动刷新）
- 流程进度图通用服务 `flow/service/FlowDiagramService`：返回实例实际所用版本的 BPMN XML + 已完成节点/连线 + 当前节点 + 轨迹，**不做鉴权**，调用方先校验能否看该实例；前端组件 `degel-front/src/components/FlowDiagram`（bpmn-js NavigatedViewer）
  - 轨迹排序必须 startTime + `getTransactionOrder()`，同事务节点常同一毫秒，只按时间会乱序
  - 新业务接入：业务 Controller 加一个 `/{id}/diagram` 接口校验归属后调 `getDiagram(processInstanceId)` 即可

### 已接入：店铺资料变更审批（流程 `shopChange`）

`ShopChangeController` / `ShopChangeServiceImpl` / `sys_shop_change`；状态机见 `docs/api-map.md`。网关零改动：店铺侧路径挂 `/shop/mine/**`（命中 admin-url-excludes），平台侧挂 `/shop/change/**`（命中 admin-urls `*:/admin/shop`）。
