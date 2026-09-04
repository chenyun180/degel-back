# ES 商品搜索集群（192.168.1.14，Windows + Docker Desktop/WSL2）

3 节点单集群：容器 `es-1`/`es-2`/`es-3`，ES 7.17.29，集群名 `degel-es`。
索引默认 1 主 1 副本 → 任一节点宕机数据不丢、服务不断（2026-09-03 实测：停 es-1 后集群
yellow 但可查可写，es-1 回归自动恢复 green）。

宿主机端口映射：`9200→es-1`、`9201→es-2`、`9202→es-3`。
⚠️ 客户端应配多个 uris（9200+9201+9202）：仅连 9200 时，es-1 宕机连查询入口都没了。

## 部署 / 日常操作（在 192.168.1.14 上）

```powershell
cd C:\Users\Admin\degel-es
docker compose up -d      # 启动
docker compose down       # 停止（数据在命名卷 es-data01/02/03，不丢）
```

Mac 远程操作：`ssh admin@192.168.1.14`（公钥 cloud-mac-degel 已装入 administrators_authorized_keys），
或直接跑 `C:\Users\Admin\degel-es\deploy-es.bat`。

## 关键配置

- ES 堆 512MB×3：WSL2 虚拟机仅 7.6GB（同机还有 mysql/nacos/redis/minio），资源权衡；扩内存后调大
- `vm.max_map_count=262144`：已写入 docker-desktop 发行版 `/etc/sysctl.conf`
  ⚠️ Docker Desktop 重启/升级可能重建 WSL 发行版导致失效，若 ES 起不来先重设：
  `wsl -d docker-desktop -u root sysctl -w vm.max_map_count=262144`
- ⚠️ SSH 会话里 `docker pull` 必失败（Docker 29 CLI 强制走凭据助手，非交互会话无凭据库）。
  拉新镜像要在 Windows 桌面交互会话里执行；`compose up`（镜像已在本地）不受影响

## degel-product 接入

`spring-boot-starter-data-elasticsearch`（Boot 2.6.13 托管 7.17.x），
`spring.elasticsearch.uris: http://192.168.1.14:9200,http://192.168.1.14:9201,http://192.168.1.14:9202`

## 已知限制（诚实记录）

- ⚠️ `xpack.security` 关闭：同网段任何人可读写 9200-9202。仅限学习环境，生产必须开 TLS+认证
- ⚠️ 三节点同机部署：防容器崩溃、不防机器宕机。真高可用需跨机器
- ⚠️ 7.17 是维护线，未来 Spring Boot 升 3.x 时应同步迁 8.x/9.x
