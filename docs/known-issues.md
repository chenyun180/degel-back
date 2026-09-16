# 已知问题清单（按需阅读）

> 由 `CLAUDE.md` 路由。排查问题前先扫一眼，避免重复踩坑。修复后请把对应条目删掉或标记 FIXED。

## 安全类（生产前必须处理）

> 2026-09-15 批量加固：原安全类 5 项已全部处理（详见下方 2026-09-15 节）。生产部署仍需做的事：
> 所有 `${ENV:默认值}` 占位的密钥/凭据（JWT_SECRET、INNER_TOKEN、OAUTH_CLIENT_*、DB/REDIS/MINIO 密码、DEGEL_CORS_ORIGINS）
> **生产必须显式注入强值**——默认值只为本地开发保留（degel.sh 不带 env 启动依赖它们）。

## 2026-09-15 管理端登录/登出闭环加固（SSO 审查修复）

- ~~**logout 可被 refresh_token 绕过**~~（✅ 直接移除 refresh_token grant：前端从未使用，且 logout 只拉黑 access token 的 jti，refresh 换发的新 token 不在黑名单——砍掉 grant 即闭环。`AuthorizationServerConfig` 现仅 password 模式，access 2h）
- ~~**管理端登录无防刷**~~（✅ degel-auth 新增 `LoginRateLimitFilter`：用户名维度计**失败**次数 5 次/分钟（成功登录不占配额，e2e 不受影响）+ IP 维度计全部请求 20 次/分钟；Redis 固定窗口 fail-open，超限 429 + error_description）
- ~~**默认密钥靠"记得注入环境变量"约束**~~（✅ gateway/auth 各加 `SecurityStartupCheck`：prod profile 下命中默认值直接拒绝启动，非 prod 打 WARN 横幅。**生产部署约定：必须 `--spring.profiles.active=prod` 启动**）
- ~~**改密/禁用用户不吊销已发 token**~~（✅ 用户级 token 版本：签发时写 `token_version` claim（读 `auth:tokenver:{userId}`），网关每请求比对，admin 侧改密/改资料/改角色/删除用户/停启用店铺时 INCR——该用户全部 token 立即失效。常量在 `Constants.AUTH_TOKEN_VERSION_PREFIX`）
- ~~**网关白名单 startsWith 误放行同前缀路径**~~（✅ `AuthFilter.matchesPathPrefix` 段感知匹配：规则无尾斜杠=精确或 `rule+/` 前缀；`/auth/oauth/token-xxx` 不再被放行）
- ~~**logout 尽力而为、失败静默**~~（✅ auth 侧解析失败返回 401（过期返回 ok 无事可做）；前端 outLogin 失败 console.warn 留痕。JwtTokenStore 删除 token 仍为空操作——黑名单是唯一吊销手段，属架构约束非 bug）
- 前端：OAuth 凭据改 `UMI_APP_OAUTH_CLIENT_ID/SECRET` 构建期注入（默认值留本地）；401 踢回登录页带 `redirect`（经 isAllowedRedirect 白名单校验）；删死代码 getFakeCaptcha/mock user
- **遗留（未修，需单独评估）**：token 仍存 localStorage（XSS 可窃取），迁 HttpOnly cookie 需网关统一种 cookie + 改 CORS credentials，影响面大未纳入本轮

## 2026-09-15 安全与秒杀加固

- ~~**JWT 无法真正吊销**~~（✅ 已修复并实测：`TokenController.logout` 写 `auth:blacklist:{jti}`（TTL=剩余有效期），网关 AuthFilter 校验；注销后原 token 请求 401）
- ~~**OAuth2 client 凭据硬编码**~~（✅ 改 `degel.oauth.client-id/client-secret` 配置，`${OAUTH_CLIENT_ID:degel}` / `${OAUTH_CLIENT_SECRET:degel_secret}`，默认值保留给本地/e2e）
- ~~**密钥入库 bootstrap.yml**~~（✅ 全部 yml 的 DB/Redis/MinIO 密码改 `${ENV:默认}` 占位；生产注入见上方说明）
- ~~**Gateway CORS 全开**~~（✅ 改白名单 `degel.cors.origins`（默认 localhost:8000/10087，`DEGEL_CORS_ORIGINS` 覆盖）；实测陌生 origin 无 allow 头）
- ~~**入站 header 伪造面（绕过网关直连服务）**~~（✅ 7 个后端微服务 `server.address: 127.0.0.1` 只监听回环，LAN 直连 9200-9206 面消除；网关保持全接口供真机调试。生产等价做法：服务端口不对外、网络隔离）
- ~~**order/product 的 /inner/ 无鉴权**~~（✅ 补 InnerTokenFilter 对齐 marketing（app/order/product/marketing 四服务统一 X-Inner-Token 校验）；实测直连 9203 inner 403，正常 Feign 链路不受影响。网关侧 /inner 路径由 AuthFilter.isInternal 拦截（此前已存在））
- **新增限流**（防刷）：C 端登录手机号 5 次/分钟 + IP 20 次/分钟（40027）、reserve 单用户 5 次/10 秒（40026）、场次列表 IP 60 次/分钟（40028）；Redis 固定窗口，fail-open，`RedisRateLimiter` 组件
- **秒杀管理闭环**：编辑库存按 delta 增量同步 Redis 余量（减超钳 0，防超卖）、限购/场次时间同步 cfg；新增"重新预热"（仅启用且未开场次，防止重置放出已售量）
- **压测脚本**：`scripts/SeckillLoad.java`（100 并发验证过：恰 9 成功/9 单落库/余量归 0 不为负）

## 测试类

- ~~**degel-app 测试未全绿**~~（✅ 2026-09-15 已修复：68/68 三遍全绿，`mvn -pl degel-app test -o` 可当发布门禁。根因多为测试过时（mock 的旧接口/旧状态机）与 MyBatis-Plus lambda cache 缺初始化；详见当日 commit。**修复过程中发现并修掉一个真实主代码 bug**：`OrderServiceImpl.createOrder` 曾把 skuId 在 `deductStock` 之前加入回滚列表，锁成功但扣减失败时会 `restoreStock` 一笔从未扣过的库存 → 库存虚增，现已移到扣减成功之后并入队）

## 历史坑（已修复，留档防复发）

- **角色分配权限丢父目录**（2026-08 已修复）：antd Tree 非严格模式 `onCheck` 不含半选父目录 + 后端先删后插 → 父目录丢失 → 前端路由整树消失。修复：前端提交 checked+halfChecked、回显只传叶子；后端 `assignMenus` 自动补全祖先。
- **全局店铺角色连坐**：所有店铺账号共用 `role_key='shop'` 的全局角色，改它影响全部店铺。已加"内置"标记+警告+禁删禁改 roleKey。

## 2026-09-06 优惠券一期新增

- ~~**c_end token 可穿透网关 admin-urls**~~（✅ 2026-09-11 已根治：AuthFilter 对 c_end 令牌限制仅可访问 `/app/**`，其余路径 403；AuthFilterTest 新增 4 个用例覆盖）
- ~~**售后退款流水死代码**~~（✅ 2026-09-16 已修复并实测：degel-order 新增 `PayFeignClient`，仅退款在 handle agree、退货退款在 confirmReceive 时调 `/app/inner/pay/refund` 落 `mall_payment_log`（direction=refund，best-effort 同退券语义）。**根因比原记录深一层**：除了无调用方，`AppSecurityFilter` 也把 `/app/inner/**` 按 C 端流量 401（无 Bearer token），InnerTokenFilter 轮不到——已把 `/app/inner/` 加入其放行清单，鉴权由 InnerTokenFilter 承担（无/错 token 403 已实测））
- **degel-order/product 的 /inner/** 无 token 校验**（仅靠网关不路由隐式保护）；marketing 的 /inner/ 已带 InnerTokenFilter 校验，标准不统一。

## 2026-09-06 二期补充

- ~~**/marketing/shop/**（店铺券管理）受 c_end token 穿透影响~~（✅ 2026-09-11 已随 `/app/**` 白名单化一并根治，见上）
- **拆单回滚的子单取消**依赖 degel-order 的 updateInnerStatus（UPDATE 无 status=0 条件，仅靠 app 层 Redisson 锁互斥——一期已知问题），子单落库到回滚在同一请求内完成，无用户并发窗口，风险可控。

## 2026-09-06 三期补充

- **补贴报表售后不冲减**：平台「本月平台补贴」/ 店铺「本月店铺补贴」按已支付口径（status IN (1,2,3,5)）统计，整单退款后补贴不回冲，数值略偏高。精确对账需 mk_subsidy_ledger 流水（设计 §6 预留，未实施）。
- **分摊折扣券出资比例是近似**：基于创建端"预期减免额"拆分，每单按比例分实算优惠，非逐单精算（设计 §4.1 口径）。
- **店铺看板 today-overview 空壳（既有）**：degel-product DashboardService.getTodayOverview 的 GMV/订单数恒 0（数据在 degel_order 库）；三期补贴数走 order 新端点 /order/shop/dashboard/subsidy-summary 绕开此问题，但看板其余指标仍空——待专项重构。
