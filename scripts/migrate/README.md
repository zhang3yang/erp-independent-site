# 独立站整机迁移：129.211.4.32 → 124.221.220.89

源机 `129.211.4.32`（Ubuntu 24.04）跑 4 个东西：`station-console`（Spring Boot jar，8081）、
`station-site`（Nuxt SSR，Node，3000）、nginx（三个子域反代）、MySQL + Redis。
目标机 `124.221.220.89`，实例 `Ubuntu24.04-Docker29-Elpx`，**同为 Ubuntu 24.04，无版本落差**。

总体量很小：应用 90MB、库 22 表 0.9MB、上传 192KB，打包后 36MB。

> ⚠ `43.110.39.238` 不是目标机。那台是 **Windows**，上面跑着 nginx 1.23.3 和生产站
> `myriad-families.com`（DNS 正解析、证书有效到 2026-10-15）。别在那台上跑这些脚本。

## 三个脚本

| 脚本 | 在哪跑 | 干什么 |
|---|---|---|
| （已跑完）打包 | 源机 | 产出 `/tmp/station-migrate.tar.gz`，36MB |
| `01-precheck.sh` | 目标机 | **只读**。看系统、端口占用、Docker 容器、MySQL 现状、冲突目录 |
| `02-install-deps.sh` | 目标机 | 装 MySQL / JDK17 / Node 22.23.2 / Nginx / Redis，装过的跳过 |
| `03-restore.sh` | 目标机 | 解包、建号建库、导数据、铺文件、起服务、自检 |

迁移包里有什么：jar、`.output`、`console-ui/dist`、`/data/station/upload`、
两个 `.env`、`tongling.conf` + `station-proxy.conf`、三套证书、两个 systemd unit、
`sdsstation` 全量 dump。**含明文密码和证书私钥，只在两台服务器之间走，别外发。**

## 执行顺序

```bash
# 1) 源机把包推到目标机（需要 22 端口对 129.211.4.32 放行）
scp /tmp/station-migrate.tar.gz  ubuntu@124.221.220.89:/tmp/

# 2) 脚本送过去
scp 0*.sh  ubuntu@124.221.220.89:/tmp/

# 3) 目标机上依次跑
bash /tmp/01-precheck.sh        # 先看，有冲突先解决
bash /tmp/02-install-deps.sh
bash /tmp/03-restore.sh
```

## 已知的坎

- **别被「端口探不通」误导**：实例是运行中的（4核8G / 120GB / 上海）。80/443 已对全网放行
  但探不通，是因为机器上还没有任何服务在监听 —— 干净机器，符合预期。3389 不通是因为
  Linux 没有 RDP，那条防火墙规则是模板带的，无意义。**唯一真正的阻塞是 22 的白名单。**
- **SSH 白名单**：22 端口只放行了 `60.27.27.37`。要加 `111.31.210.27`（本地开发机）
  和 `129.211.4.32`（源机，传包用）。
- **MySQL 未必存在**：那台是 Docker 镜像，`02` 会检测并按需安装。已有实例不动数据。
  apt 装出来的 root 走 `auth_socket`，`sudo mysql` 免密直进，`03` 依赖这一点。
- **镜像自带 Docker 29**：若有容器占了 80/443，nginx 起不来。`01` 会把容器和端口列出来。
- **`/usr/bin/node` 是硬编码**：systemd unit 里写死的。`02` 装完会软链过去，
  别换成 nvm 之类的路径。
- **station 用户**：源机 uid 999 / gid 988。目标机上 999 常被 docker 占，
  脚本不强求 uid，按用户名 chown，不影响。
- **踩到过：`sites-enabled/default` 冲突**。apt 装的 nginx 自带这个文件，也声明
  `listen 80 default_server`，跟 `tongling.conf` 的 444 兜底段撞车，`nginx -t` 报
  "duplicate default server"。源机上早删了，新机又带回来。**已补进 `03`，会自动删。**
- **`tongling.conf` 带 default_server 兜底**（`return 444`）。目标机 nginx 是新装的、
  没有别的站，所以不冲突。**但如果以后这台机器要放第二个站，记得这条会抢兜底权。**
- **DNS 要单独切**：三个子域现在解析到 129.211.4.32。脚本跑完服务是活的，
  但外部访问还走老机器。**这是最后一步，本机自检通过再切。**
- **证书 2026-12-04 到期**，手工续期，跟包一起搬过去了，域名不变就能用。
- **备案**：主域 `tongling.vclinker.com` 没备案（nginx 里 444 兜底）。换 IP 后
  备案主体如果绑的是旧 IP，子域可能被拦，切 DNS 前确认。
- `file.*` 子域 CNAME 到腾讯云 COS，跟机器无关，不用动。

## 回滚

DNS 没切之前老机器一直在跑，回滚就是「不切 DNS」。
切了之后要回滚，把 A 记录改回 129.211.4.32 即可 —— 老机器不要急着关。
