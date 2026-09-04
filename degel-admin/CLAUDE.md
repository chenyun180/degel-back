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
