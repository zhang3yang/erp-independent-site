# 独立站部署手册（Linux / Ubuntu 24.04）

> 面向**独立站那台服务器**：前台（station-site）+ 管理台（station-console + station-console-ui）。
> ERP 不在这台机器上，也不该在——依据 [独立站与ERP的边界.md](./独立站与ERP的边界.md) 要点 1
> 「ERP 和独立站完全分开部署，独立站与 ERP 的后端没有任何互通」。

和 [独立站部署手册（Windows）.md](./独立站部署手册（Windows）.md) 的关系：那一份写的是
Windows 服务器（`C:\nginx` + nssm 注册服务，2026-09-05 在 49.234.194.194 实施过），本份是
Linux（systemd + `/etc/nginx/conf.d`）。
**设计决策以那一份和 boundary 为准，本份只管怎么在 Linux 上把它落地。**

---

## ⚠ 开始之前：不要把 ERP 侧旧配置混入独立站部署

仓库里曾有几份配置停留在「前台向 ERP 取数」的旧架构上，而那条链路已经断开（boundary §7.2，
`station-site/nuxt.config.ts` 顶部注释）：

> **2026-09-06 更新：下面这张表里的问题都处理掉了**，留着是为了记住判据。
> `nginx.kangchen.conf` 和 `start-console.bat` **已从仓库删除**（连同
> `station-console/deploy/` 下另外两份 nginx 配置）；`start-site.bat` 就地改对了。
> 现在的 nginx 模板：Windows 用
> [`deploy/nginx/station.windows.conf.template`](../deploy/nginx/station.windows.conf.template)，
> Linux 用本手册配套的 [`deploy/nginx/tongling.conf`](../deploy/nginx/tongling.conf)。

| 文件 | 过时在哪 | 处理 |
| --- | --- | --- |
| `station-site/deploy/start-site.bat` | 历史上曾设 `NUXT_PUBLIC_SITE_KEY` 和 `NUXT_API_BASE=…:8080`（ERP） | ✅ 当前不需要 SITE_KEY，`NUXT_API_BASE` 指 **8081**。Linux 上换成 systemd unit |
| ~~`station-site/deploy/nginx.kangchen.conf`~~ | `/openapi/site/` 转 `127.0.0.1:8080` | ✅ 已删。本手册的 nginx 里转 **8081** |
| ~~`station-console/deploy/start-console.bat`~~ | 设了 `ERP_BASE_URL` / `STATION_CONSOLE_SECRET`（这两项现在整个不存在） | ✅ 已删。环境变量清单见 `station-site/deploy/install-service.ps1` 的 `-Target console` 那张表，Linux 上照它写 systemd unit |

判据很简单：**这台机器上没有 8080，任何指向 8080 的配置都是错的。**

---

## 一、目标拓扑

```
                    公网
                     │
              ┌──────┴──────┐
              │   nginx     │  80 / 443
              └──┬───┬───┬──┘
     前台域名 ────┘   │   └──── file.<域名>：直接发 /data/station/upload
   console.<域名> ────┘
              │
     ┌────────┴────────┐
     │                 │
 127.0.0.1:3000    127.0.0.1:8081
 station-site      station-console
 （Node SSR）       （Spring Boot）
                        │
                 ┌──────┴──────┐
              MySQL 3306    Redis 6379
              sdsstation    （只监听 127.0.0.1）
```

> ⚠ 这张图是按「存储落在本机磁盘」那一档画的，两处要按线上事实读：
> **`file.<主域名>` 不在独立站这台机器上**——它 CNAME 到腾讯云 COS，由 COS 直接发文件，
> 所以线上没有它的 server 块（`deploy/nginx/tongling.conf:66-67`）；磁盘那一档只剩
> nginx 的 `/files/` 作为降级退路（§7.2）。另外前台在线上是**两个 host**：
> 主域是商城、`buyer.` 只放「我的东西」（§7.2），图里的「前台域名」是这两个。
> 还有一个 `data.<主域名>`，只开 `/openapi/site/**`，给第三方和调试用（§7.2）。

| 端口 | 谁 | 对外？ |
| --- | --- | --- |
| 80 / 443 | nginx | ✅ 安全组只开这两个（外加限源 IP 的 22） |
| 3000 | station-site（Nuxt SSR） | ❌ 只监听 127.0.0.1 |
| 8081 | station-console | ❌ 只监听 127.0.0.1 |
| 3306 / 6379 | MySQL / Redis | ❌ 只监听 127.0.0.1 |

⚠ **8081 一旦对外，两个前提同时塌掉**：`Secure` Cookie 那套会话防护（有人能用 http
直连绕开）、以及 `station.cors.allowed-origins` 留空所依赖的「浏览器只会同源访问」。

---

## 二、系统环境（已完成，此处留档）

| 软件 | 实测版本 | 硬要求 |
| --- | --- | --- |
| Ubuntu | 24.04.4 LTS | glibc ≥2.28，否则 Node 20+ 装不上（CentOS 7 是 2.17，不可用） |
| JDK | OpenJDK 17.0.20 | ≥17，Spring Boot 4.0.6 的基线 |
| Node | v22.23.2 | Nuxt 4.5 要求 `^20.19` 或 `>=22.12` |
| nginx | 1.24.0 | 带 http_ssl |
| MySQL | 8.0.46 | **必须 8.0+**，建库脚本用 `utf8mb4_0900_ai_ci`，5.7/MariaDB 没有这个排序规则 |
| Redis | 7.0.15 | 会话 + 询盘限流 + SSO 令牌 jti 防重放 |

一次装齐：

```bash
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
apt update && apt -y upgrade
apt -y install openjdk-17-jdk-headless nginx mysql-server redis-server unzip curl
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt -y install nodejs
timedatectl set-timezone Asia/Shanghai      # jdbc 串里写死了 serverTimezone=GMT+8
systemctl enable --now nginx mysql redis-server
```

---

## 三、账号、目录、数据库

### 3.1 跑服务的系统用户

```bash
useradd -r -s /usr/sbin/nologin -d /opt/station station
mkdir -p /opt/station/{console,site,console-ui}
mkdir -p /data/station/upload
mkdir -p /etc/station
chown -R station:station /opt/station /data/station
```

⚠ 不要用 root 跑这两个进程。前台是公网直接访问的 Node 服务，后端有文件上传——
这两个都是「拿到 RCE 就等于拿到整机」的位置。

### 3.2 建库

```bash
mysql -e "CREATE DATABASE IF NOT EXISTS sdsstation
          DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -e "CREATE USER IF NOT EXISTS 'station'@'127.0.0.1' IDENTIFIED BY '<DB密码>';"
mysql -e "GRANT ALL ON sdsstation.* TO 'station'@'127.0.0.1'; FLUSH PRIVILEGES;"
```

Ubuntu 的 MySQL root 走 unix socket 免密，所以上面不用 `-p`；默认
`bind-address = 127.0.0.1`，符合 boundary §5.1，不用改。

### 3.3 建表 + 初始化档案 + 增量

把 `sql/station/` 整个目录传上去，**按这个顺序**跑（全部可重复执行）：

```bash
cd /tmp/sql-station
mysql sdsstation < 01_station_schema.sql
mysql sdsstation < 02_init_siteinfo.sql
mysql sdsstation < 03_init_decorate.sql      # 可选，见下
for f in migration/*.sql; do echo ">> $f"; mysql sdsstation < "$f"; done
```

`03_init_decorate.sql` 是**可选**的一步，跑不跑首页都起得来：不跑的话
`contentjson` 留 NULL，接口回内置默认（导航四条英文、Banner 标题就是站点名称），
首页只剩一个标题加一排商品；跑了就把七种语言的 Banner／特色／能力／关于我们／
联系方式一次填满，版式对着 `kangwanjia-website-EN` 写。

> ⚠ 它是 `UPDATE`，会**整份覆盖**装修内容，和 02 的 INSERT IGNORE 不一样。
> **只在新站点初始化时跑。** 商家已经在管理台配过了，跑一遍他填的全没。
> 里面的联系方式、两张图和三个数字亮点都是样例值，上线前逐项换掉——
> 脚本头部列了清单。

跑了 03 的话还要把它引用的两张图放上去，否则 Banner 和「关于我们」是空的
（图走 `/files/`，nginx 直接 alias 磁盘目录，**不用重新构建前台**）：

```bash
# 本地：scp deploy/site-assets/deco/*.jpg ubuntu@<IP>:/tmp/
install -o station -g station -m 644 -D /tmp/home-hero.jpg     /data/station/upload/deco/home-hero.jpg
install -o station -g station -m 644 -D /tmp/about-factory.jpg /data/station/upload/deco/about-factory.jpg
```

细节见 [deploy/site-assets/README.md](../deploy/site-assets/README.md)，那里也写了
为什么**不能**把装修图片放进 `station-site/public/`（Nitro 的静态资源清单是构建时
生成的，往已部署的 `.output/public/` 里塞文件重启也是 404）。

> ⚠⚠ **改完库要 `systemctl restart station-console`。** `SiteInfoService` 把整行
> 站点档案（含 `contentjson`）缓存在进程里，只有经过管理台的写操作才 evict。
> 直接改库绕过了它，表现是「库里是新的、页面是旧的，怎么刷新都不变」——
> 这一条对**任何**手工改 `u_s_siteinfo` 的场景都成立，不只是装修内容
> （`domain`、`currency`、`langs`、`consolesecret` 同理）。

migration 下的脚本都用 information_schema 判存在性（存储过程那一套），
跑第二遍不会报错，也不会毁数据。

> **那个 `for` 循环按文件名顺序跑，别改成挑着跑。** `2026-09-04-inquiry-structured-rfq.sql`
> 会建出 `u_s_inquiry_item`，紧接着 `2026-09-06-drop-inquiry-item-and-orphans.sql`
> 又把它删掉——中间那张表一闪而过是正常的。跳着挑脚本跑会留下这张多余的表：
> 结账页现在走的是订单接口（`u_s_order` + `u_s_orderitem`），询盘明细表零引用。

### 3.4 ⚠ 02 里有三格必须改，否则上线即错

`u_s_siteinfo` 只有一行，下面这几列是开发期占位值：

| 列 | 脚本里的值 | 上线前改成 |
| --- | --- | --- |
| `domain` | `tongling.vclinker.com` | 真实主域名，**只填主机名**：不带 `https://`、不带尾斜杠 |
| `currency` | `CNY` | 外贸站通常是 `USD`，和业务确认 |
| `consolesecret` | `NULL` | 跳转密钥的**明文**（见下） |
| `sitecode` | `DLZ2049653802` | 必须和 ERP 登记的一致 |

> ⚠ `domain` 那一格原来写的是 `localhost:5174`，与脚本里的实际值不符，已按
> `sql/station/02_init_siteinfo.sql:73` 改正。这一列的约定是**存主机名**——
> 不带 `https://`、不带尾斜杠（同处 `:74-75`），而且存**主域、不是 `buyer.`**
> （`:78-80`）。已经建好的库改这一列走
> `sql/station/migration/2026-09-08-site-domain-to-apex.sql`。

⚠⚠ `consolesecret` 存**明文**，不是从 ERP 的 `sdsshop.u_s_site` 里 SELECT 出来的那个值——
那是 96 字符的 AES-GCM 密文，粘过来不报任何错，只是每次跳转都签名不匹配。
详见 `sql/station/02_init_siteinfo.sql` 里那一大段。留 NULL 是 fail-closed（SSO 一律拒绝），
比填错安全。

```sql
UPDATE u_s_siteinfo SET domain='<主域名>', currency='USD', consolesecret='<明文密钥>' WHERE id=1;
```

⚠ `u_s_user` 故意是空表。第一个管理员由「从 ERP 点进入管理」时自动落库（默认 operator），
不要往里塞种子账号。副作用要知道：**SSO 打通之前，管理台没有任何进入方式。**

---

## 四、在自己电脑（Windows）上打包

三个产物，互相独立：

```powershell
# 1) 后端 jar
cd D:\wczl-erp\station-console
mvn clean package -DskipTests
#   → target\station-console-1.0.0.jar

# 2) 前台 SSR 产物（⚠ 是 .output 不是 dist）
cd D:\wczl-erp\station-site
npm ci
npm run build
tar -czf site-output.tgz .output
#   → .output\ 是自包含的，服务器上不需要 node_modules

# 3) 管理台前端静态产物
cd D:\wczl-erp\station-console-ui
npm ci
npm run build
tar -czf console-ui-dist.tgz dist
```

> ⚠ **管理台的构建会顺手在 `dist/assets/` 下生成 `.gz` 兄弟文件**（`vite.config.js` 里的
> `gzipStatic` 插件），上面那条 `tar` 会一起打包——**别改成只挑 `.js`/`.css` 的打包方式**，
> 漏掉 `.gz` 的后果不是报错，而是服务器的 `gzip_static` 没东西可发、页面又慢回十几秒。
> 配套的 nginx 配置和实测数据见 [deploy/nginx/station/nginx-gzip.conf](../deploy/nginx/station/nginx-gzip.conf)。

Windows 10+ 自带 `tar`，直接能用。`.output` 是几千个小文件，逐个 scp 会很慢，打包传一个文件。

### 上传

```powershell
scp D:\wczl-erp\station-console\target\station-console-1.0.0.jar ubuntu@<IP>:/tmp/
scp D:\wczl-erp\station-site\site-output.tgz                     ubuntu@<IP>:/tmp/
scp D:\wczl-erp\station-console-ui\console-ui-dist.tgz           ubuntu@<IP>:/tmp/
scp -r D:\wczl-erp\sql\station                                   ubuntu@<IP>:/tmp/sql-station
```

服务器上就位（root）：

```bash
install -o station -g station -m 644 /tmp/station-console-1.0.0.jar /opt/station/console/

rm -rf /opt/station/site/.output.bak && mv /opt/station/site/.output /opt/station/site/.output.bak 2>/dev/null
tar -xzf /tmp/site-output.tgz -C /opt/station/site/

rm -rf /opt/station/console-ui/dist.bak && mv /opt/station/console-ui/dist /opt/station/console-ui/dist.bak 2>/dev/null
tar -xzf /tmp/console-ui-dist.tgz -C /opt/station/console-ui/

chown -R station:station /opt/station
```

保留 `.bak` 是为了回滚——出事时把两个目录换回来再重启，比重新打包快得多。

---

## 五、环境变量（两个文件，600 权限）

### 5.1 `/etc/station/console.env`

```ini
# ---- ⚠ 只监听本机 ----
#
# Spring Boot 默认绑 0.0.0.0，`ss -lntp` 会看到 *:8081——那时候 8081 只靠安全组挡着，
# 而 boundary 要的是「物理上不可达」。安全组被误改一次就直接裸奔，
# 而且这种暴露没有任何日志能提醒你。
SERVER_ADDRESS=127.0.0.1

# ---- 数据库（本机） ----
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=station
DB_PASSWORD=<DB密码>

# ---- Redis（本机，无密码则留空） ----
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_PASSWORD=

# ---- 上传 ----
STATION_UPLOAD_DIR=/data/station/upload
# 康辰使用腾讯云 COS，file.<主域名> 是 COS 自定义域名；公开地址填
# u_s_storage.public_url，local 模式才使用这个环境变量。
STATION_FILE_BASE_URL=

# ---- Cookie：生产必须 true ----
CONSOLE_COOKIE_SECURE=true
STATION_BUYER_COOKIE_SECURE=true

# ---- 前台 Cookie：保持 host-only ----
# BUYER_SESSION / CHAT_VISITOR 永远不设置 Domain。两个 host 之间通过
# BuyerHandoff 的 60 秒一次性票据交接同一个令牌，不要配置 STATION_COOKIE_DOMAIN。
STATION_COOKIE_DOMAIN=

# ---- 链接（只是显示用，不会被后端调用） ----
CONSOLE_HOME_URL=https://console.<主域名>
ERP_HOME_URL=https://<ERP地址>

# ---- 跳转密钥兜底：正常配在库里的 u_s_siteinfo.consolesecret，这里留空 ----
STATION_JUMP_SECRET=

# ---- 加密 u_s_storage 密钥用；一期文件落本地磁盘，用不到，随便一个长随机串 ----
STATION_CONFIG_SECRET=<随机32位>

# ---- CORS：前台和后端同域，留空即可（推荐） ----
STATION_CORS_ORIGINS=

# ---- 登录即全权。默认 true 是产品决定，不是漏配，见 application.yml 里那段 ----
CONSOLE_GRANT_ALL_PERMS=true
```

### 5.2 `/etc/station/site.env`

```ini
# SSR 服务端取数：打**本机 8081**（station-console）。⚠ 不是 8080，那是 ERP，这里没有
NUXT_API_BASE=http://127.0.0.1:8081
# 浏览器侧留空 = 相对路径，由 nginx 转发，同源，不用 CORS
NUXT_PUBLIC_API_BASE=
# 拼 canonical / hreflang / sitemap 的绝对地址。漏了会推出 http://，Google 会当成往 http 导流
#
# ✅ 下面这两行**就是线上现在的样子**（2026-09-09 起）：SITE_URL 指主域、BUYER_URL 指 buyer.。
#    `deploy/nginx/tongling.conf:40-41` 记着这次改动（「SITE_URL ← 从 buyer. 改过来」
#    「BUYER_URL ← 新增」）。原来这里写着「现在装机 SITE_URL 填 buyer.、BUYER_URL 留空」，
#    那是切换之前的单 host 状态，已作废——照那句配等于把两个 host 的布局关掉。
#
# ⚠⚠ **两个变量必须同时存在**。`station-site/server/utils/hosts.ts` 的 `siteHosts()`
#    （`:51-56`，说明在 `:17-24`）里 `enabled` 要求 siteUrl 和 buyerUrl 都拿得到、
#    而且不相等。只填一个、或者两个填成一样，整套域名拆分**不生效**：中间件放行、
#    robots/sitemap 不按域区分——表现是 buyer. 上的公开页不再 301 回主域、
#    账号页在主域也照样打开，而页面本身看起来一切正常。
#
# ⚠ 填**主域**。前台劈成两个 host（见 §7.2）：主域放公开页，buyer. 只放「我的东西」。
#   收录权重要收拢到主域一个 host 上，canonical 就必须指主域。
#
# ⚠ 上线前确认主域在 nginx 上**真的有 server 块、而且有自己的证书**。
#   这一列一度填的是 buyer. 子域，因为那时候主域两样都没有，443 落到兜底块被 444 掉
#   ——拿一个连不上的域名当 canonical，收录会全军覆没，而页面本身看着一切正常。
#   ⚠ 缺的**一直只是证书**，不是备案：备案跟着根域 vclinker.com 下来，主域和子域
#   一起覆盖（`deploy/nginx/tongling.conf:118-120` 把原来那句「主域没备案」定为写错）。
NUXT_PUBLIC_SITE_URL=https://tongling.vclinker.com
# 「我的东西」那个 host：**只有 `/account` 和 `/orders` 两支**。
#   ⛔ 登录 / 注册 / 找回密码 / 重置密码 / 邮箱验证**不在这个 host 上，在主域**。
#      判据只有一处：`station-site/shared/utils/buyerPaths.ts` 的 `BUYER_PATHS`；
#      2026-09-09 就是把它抄成 nginx 正则，才把登录页转成死循环的（§7.2）。
#
# 只影响**站内链接指哪**（`useSiteHref()` 把 buyer 路径拼成 buyer. 的绝对地址，
# 省掉一次 301 整页跳）。真正的分流在 Nuxt 的 `server/middleware/domain-split.ts`
# 里、**不在 nginx 上**，所以漏填不会坏，只是每次点进账号页多一跳。
# 本地开发只有一个 origin，留空。
NUXT_PUBLIC_BUYER_URL=https://buyer.tongling.vclinker.com
PORT=3000
HOST=127.0.0.1
NODE_ENV=production
```

```bash
chmod 600 /etc/station/*.env
chown root:root /etc/station/*.env
```

### 5.3 询盘邮件（可选）

`spring.mail.*` 不在 application.yml 里，走一个可选导入的文件——**不配就是真的不配**
（属性缺失 → Boot 不建 JavaMailSender → 日志 INFO「只入库不发信」）。要配的话：

```bash
cp application-mail.yml.example /opt/station/console/application-mail.yml   # 填 SMTP 授权码
chown station:station /opt/station/console/application-mail.yml
chmod 600 /opt/station/console/application-mail.yml
```

⚠ 它靠 `spring.config.import: optional:file:./application-mail.yml` 加载，路径是**相对
工作目录**的——所以下面的 unit 里 `WorkingDirectory` 必须是 `/opt/station/console`。

⚠ 买家注册/找回密码依赖 SMTP（不是"通知"，是流程本身）。不配的话这两个接口直接报错。

---

## 六、systemd

### 6.1 `/etc/systemd/system/station-console.service`

```ini
[Unit]
Description=station-console (独立站后端)
After=network-online.target mysql.service redis-server.service
Wants=network-online.target

[Service]
Type=simple
User=station
Group=station
WorkingDirectory=/opt/station/console
EnvironmentFile=/etc/station/console.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -Dfile.encoding=UTF-8 \
          -jar /opt/station/console/station-console-1.0.0.jar
Restart=always
RestartSec=5
# 上传目录之外不需要写盘
ReadWritePaths=/data/station/upload
ProtectSystem=full
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

### 6.2 `/etc/systemd/system/station-site.service`

```ini
[Unit]
Description=station-site (独立站前台 Nuxt SSR)
After=network-online.target station-console.service
Wants=network-online.target

[Service]
Type=simple
User=station
Group=station
WorkingDirectory=/opt/station/site
EnvironmentFile=/etc/station/site.env
ExecStart=/usr/bin/node /opt/station/site/.output/server/index.mjs
Restart=always
RestartSec=5
ProtectSystem=full
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

⚠ `After=station-console` 只影响**启动顺序**，不代表依赖：后端没起来时前台照样能起，
只是页面取不到数。这是对的——前台自己重启不该被后端拖住。

```bash
systemctl daemon-reload
systemctl enable --now station-console station-site
systemctl status station-console station-site --no-pager
journalctl -u station-console -n 50 --no-pager      # 看启动日志
```

日志走 journald，不用配 logrotate。要看实时：`journalctl -u station-site -f`。

---

## 七、nginx

Ubuntu 的 nginx 会同时 include `/etc/nginx/conf.d/*.conf` 和 `/etc/nginx/sites-enabled/*`。
**先把自带的默认站删掉**，否则它是 80 上的 default_server，会截走所有匹配不上的 Host：

```bash
rm -f /etc/nginx/sites-enabled/default
```

### 7.1 阶段一：还没有域名时的预部署

域名/证书没到位不用干等——打包、数据库、服务能不能起来，这些风险都能提前暴露。
`/etc/nginx/conf.d/station-predeploy.conf`：

```nginx
# 临时配置。域名到位后整个文件删掉，正式配置照 deploy/nginx/tongling.conf 落地（见 §7.2）。
# ⚠ 这是本手册里**唯一**还内嵌的 server 配置块：它是临时的预部署件，线上没有对应物，
#   所以没有可指的权威文件。正式配置（§7.2）一律不在这里复制。
server {
    listen 80 default_server;
    server_name _;
    client_max_body_size 220M;

    # 管理台前端放在 :8080 上单独开一个口（它的路由 base 是 /，塞不进子路径）
    location /openapi/site/ { proxy_pass http://127.0.0.1:8081; include /etc/nginx/station-proxy.conf; }
    location /files/        { alias /data/station/upload/; }
    location /              { proxy_pass http://127.0.0.1:3000; include /etc/nginx/station-proxy.conf; }
}

server {
    listen 8080;                       # 只从公网 IP 临时访问管理台用，验完关掉
    server_name _;
    root /opt/station/console-ui/dist;
    index index.html;
    location / { try_files $uri $uri/ /index.html; }
    location /api/ { proxy_pass http://127.0.0.1:8081; include /etc/nginx/station-proxy.conf; }
    location /sso  { proxy_pass http://127.0.0.1:8081; include /etc/nginx/station-proxy.conf; }
}
```

预部署期间同时把这两项改掉，否则会遇到「登录成功了但立刻又是未登录」（http 页面
静默丢弃 `Secure` Cookie）：

```ini
CONSOLE_COOKIE_SECURE=false
STATION_BUYER_COOKIE_SECURE=false
STATION_FILE_BASE_URL=http://<公网IP>/files
```

⚠ **上线前必须改回 true**，并把 8080 从安全组撤掉。这三行是本手册里最容易跟着上线的东西。

### 7.2 阶段二：正式配置

> ### ⚠ 本站 nginx 配置以 [`deploy/nginx/tongling.conf`](../deploy/nginx/tongling.conf) 为准
>
> **本手册不再复制 server 配置体。** 线上那份放在 `/etc/nginx/conf.d/tongling.conf`，
> 是从线上实配反向生成、文件头注明了「改线上之前先比对这份」的；要照抄、要核对线上，
> 都以它为唯一出处。
>
> 这里原来内嵌了**第二份**配置体——它就是本手册和线上分叉的根因。2026-09-09 照旧清单
> 加的那两条账号页正则（主域 302 过去、`buyer.` 301 回来）把登录页转成了
> `ERR_TOO_MANY_REQUESTS` / `ERR_TOO_MANY_REDIRECTS` 死循环，经过记在
> `tongling.conf:15-25`。**⛔ 路径分流一条正则都不要加回来**（`tongling.conf:367-381`）。
>
> 两 host 布局要动的东西记在 `tongling.conf` 文件头的「线上环境变量（改动过的三项）」
> 和「还没做的」两节。（原来这里写「六步上线清单在 conf 文件头，那里是唯一的出处」——
> 文件头里并没有这份清单，只有上面那两节，已改成指实际存在的小节。）
>
> ### ✅ 横幅更正：两 host 布局 2026-09-09 已上线，主域就是商城
>
> 主域 `tongling.vclinker.com` 是商城（公开页 + 登录注册），`buyer.` 只放「我的东西」。
> 原来这里的横幅写的是「还没切、主域没备案、没证书，线上还是单 host」——**与事实相反**：
> `tongling.conf:6` 记的是「✅ 两 host 布局 2026-09-09 已上线，主域是商城，
> buyer. 只放『我的东西』」。备案不是问题（跟着根域下来），真正一度缺的是主域证书，
> 而那份配置里主域证书已经在用（`tongling.conf:225-226`）。机器也换了，见 §11。

前台是**两个 host**。跑的是同一个 Node 进程，分的是 host 不是应用。

分界线是**要不要登录**：购物车和「我的询价」看着像买家自己的东西，但它们的数据在
`localStorage` 里、按源隔离，挪到 `buyer.` 上买家在主域加的货那边就是空的（页面还不报错），
而且这两页本来就不要求登录。所以它们留在主域。

⛔ **归属判据只有一处，nginx 里不做分流**：`station-site/shared/utils/buyerPaths.ts`
的 `BUYER_PATHS = ['/account', '/orders']`，由
`station-site/server/middleware/domain-split.ts` 做 301。
**登录、注册、找回密码、重置密码、邮箱验证都在主域**——这几页不登录也能看，
用户 2026-09-08 专门确认过「登入也放主域」（`buyerPaths.ts:30-38`）。
往 nginx 里再抄一份清单，就是上面那个死循环的复现路径：同一条正则在两边各写一遍，
两边各自变旧，读者照抄就把登录页转回死循环。

⚠ 两个 host 各要一张证书（加上 `console.`、`data.`，线上这台机器一共四个域四张，
见 §7.3）。腾讯云免费 DV 是**严格单域名**，`buyer.` 那张不覆盖主域；
主域缺证书就别推这份配置——nginx 启动直接 `cannot load certificate`，整站起不来。

⚠ 备案跟着根域下来，主域和子域一起覆盖，这件事不是拦路的。原来这里写过一句
「主域还没备案」，那是写错了——**真正缺的一直是证书**（`tongling.conf:118-120`）。

⚠ nginx 写法定死的一条：`http2` 只能写在 `listen` 行上（`listen 443 ssl http2;`）。
Ubuntu 24.04 自带 nginx 1.24.0，独立的 `http2 on;` 报 `unknown directive "http2"`、
nginx 直接起不来——看着像模块没装，其实是版本不够。踩过，见 §11② 和
`tongling.conf:216-221`。

线上除了这四个域，还有几处容易漏，核对 `tongling.conf` 时别只看域名块：

- 两个 **444 兜底块**（`:163-167` 的 80、`:190-203` 的 443）：Host 对不上的一律断连。
  443 那个必须同时给证书和 `http2`，否则 nginx 启动就报错，理由写在 `:169-189`。
- `data.<主域名>` —— 对外开放接口，**只开 `/openapi/site/**`**，给第三方和调试用；
  自家两个前台 host 都走自己的同源地址，不打它（`tongling.conf:442-476`）。
- `file.<主域名>` —— **不在这台机器上**：它 CNAME 到腾讯云 COS，由 COS 直接发文件
  （`tongling.conf:66-67`）。所以这里**没有** `file.` 的 server 块，也不要为它签证书。

⚠ `/files/` 的 location 在**主域和 `buyer.` 两个 host 上都有**，少一个装修图片就是死链
（`tongling.conf:264-276,335-347`；理由见
[deploy/site-assets/README.md](../deploy/site-assets/README.md)）。
现在是 COS 那一档在发文件，这一段的用途是「存储切回本机磁盘 / COS 出问题要临时降级」
那条退路，别顺手删掉。

最后是公共 proxy 片段 `/etc/nginx/station-proxy.conf`。**这一段本手册写全**：
线上它在 `/etc/nginx/station-proxy.conf`，而仓库里没有副本
（`scripts/migrate/` 只是打包时把它一起搬走），所以这里是它唯一的出处：

```nginx
proxy_http_version 1.1;
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
# ⚠ 这一行不能省。询盘限流按 IP 算（每分钟 5 条/IP），漏了它所有访客都是 127.0.0.1，
#   会退化成**全站每分钟 5 条**，第 6 个客户就提交不了询盘
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
# ⚠ Nuxt 靠它决定 canonical / hreflang / sitemap 拼 https 还是 http
proxy_set_header X-Forwarded-Proto $scheme;
proxy_read_timeout 75s;
```

```bash
nginx -t && systemctl reload nginx
```

### 7.3 证书

```bash
apt -y install certbot python3-certbot-nginx
# 这台机器上要证书的是下面这四个域（两个前台 host + 管理台 + 开放接口域）
certbot --nginx -d <主域名> -d buyer.<主域名> -d console.<主域名> -d data.<主域名>
systemctl status certbot.timer      # 自动续期，装完就有
```

⚠ 用 certbot 的话它会自己改上面的 conf 并填证书路径，不用手工写 `ssl_certificate`。
云厂商签发的 DV 证书则手动传到 `/etc/nginx/cert/<域名>/` 下。

⛔ **不要为 `file.<主域名>` 签证书，也别把它写进 `-d`。** 那个域名不在这台机器上：
它 CNAME 到腾讯云 COS、由 COS 直接发文件（`deploy/nginx/tongling.conf:66-67`），
证书要传到 COS 那边。原来这条命令里有 `-d file.<主域名>`，与线上分工不符，已删。

⚠ 统领站线上实际用的是腾讯云签发的**四张单域名 DV，手工续、没开自动续期**
（`deploy/nginx/tongling.conf:54`）；上面这条 certbot 命令是拿不到云厂商证书时
从零签的那条路。

---

## 八、验收

### 8.1 在服务器上（不依赖域名和证书）

```bash
# 后端活着
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/openapi/site/info    # 200
# 前台活着，而且是**真 SSR**
curl -s http://127.0.0.1:3000/ | grep -o '<title>[^<]*</title>'
```

⚠ **最后那条是整个前台的核心验收**：返回的 HTML 里必须有真实标题和内容。
如果只有一个空的 `<div id="__nuxt">`，说明退化成客户端渲染了，Google 收录不到，
这个站存在的理由去掉了一半。

```bash
# 会话真的进了 Redis（不是静默退回内存会话）——登录一次之后：
redis-cli keys 'station:console:session*'
# 一条都没有 = 又退回内存了，去看 pom.xml 里 spring-boot-starter-session-data-redis 那段
```

### 8.2 在自己电脑上（域名到位后）

```powershell
curl -s https://<主域名>/ | findstr /C:"<title>"
curl -s https://<主域名>/robots.txt          # 应含 Sitemap:
curl -s https://<主域名>/sitemap.xml | findstr hreflang
curl -i https://console.<主域名>/api/site    # 未登录应当是 401，不能是 200
```

### 8.3 边界自查（boundary §13）

```bash
# 这台机器上不应该有任何**指向 ERP 8080** 的转发
#
# ⚠ 原来这条是 `grep -rn "8080" /etc/station /etc/nginx/conf.d/station*.conf`，
#   它**永远不可能零结果**，所以这条验收等于没有：
#     · §7.1 的 station-predeploy.conf 自己就写着 `listen 8080;`（临时把管理台开在
#       8080 上，域名到位后整个文件删掉）；
#     · §5.2 的 site.env 里还有一句注释「⚠ 不是 8080，那是 ERP，这里没有」。
#   两处都不是「指向 ERP」，所以这里排除预部署文件、只找 8080 作为**转发目标**的写法。
grep -rn '127.0.0.1:8080\|:8080;\|:8080/' --exclude=station-predeploy.conf \
    /etc/nginx/conf.d/station*.conf /etc/station/*.env           # 应当零结果
ss -lntp | grep -E ':(3000|8081|3306|6379)'                      # 全部 127.0.0.1
```

---

## 九、常见故障

| 现象 | 多半是 | 怎么查 |
| --- | --- | --- |
| 访问首页 502 | Node 没起来 | `journalctl -u station-site -n 50` |
| 管理台"登录成功了但立刻又是未登录" | http 下 `Secure` Cookie 被浏览器静默丢弃 | 证书是否生效；`CONSOLE_COOKIE_SECURE` |
| 从 ERP 点"进入管理"被弹回登录页 | 跳转密钥不对 / 存成了密文 | 后端日志"签名不对"；`u_s_siteinfo.consolesecret` 必须是**明文** |
| 管理台/前台「打开一片空白、转十几秒」 | 静态资源没压缩（管理台入口 JS 有 1.43 MB，裸发时冷加载要十几秒，而那份 index.html 当时没有加载动画，所以那十几秒是纯白） | ① `dist/assets/*.gz` 在不在；② nginx 有没有 `gzip_static on;` + `gzip_types`。配置与实测数据见 [deploy/nginx/station/nginx-gzip.conf](../deploy/nginx/station/nginx-gzip.conf) |
| 启动报「本站档案还没建」 | `02_init_siteinfo.sql` 没跑 | `select * from u_s_siteinfo` |
| 图片上传成功但全是死链 | `STATION_FILE_BASE_URL` 与 file 子域对不上 | 库里存的是完整 URL，改了要同时刷历史数据 |
| 第 6 个客户提交不了询盘 | nginx 漏了 `X-Forwarded-For`，限流退化成全站 | `station-proxy.conf` 是否被 include |
| 首页 HTML 是空壳 | SSR 没生效 | 8.1 那条 title 验收 |
| 启动报 `Name for argument of type [String] not specified` | 打包时 `-parameters` 没开 | 见 pom.xml 里 maven-compiler-plugin 那段 |

---

## 十、更新与回滚

> ⚠ **重建之前，先确认 `/root/build/<模块>` 下的源码就是仓库版本。**
>
> 2026-09-23 抓到过一次反例：那台法国机的 `station-console-ui` 源码里**没有 `i18n/`**
> （而线上管理台是法语界面），也就是说那份源码比线上 dist 旧——**那时候跑一遍
> `/root/rebuild.sh` 会把管理台退回没有多语言的旧版**。同类偏差还有 Java 的
> `model3d`/`SiteModel`、`station-shared` 的 `ModelPreview.vue` 等。
>
> 判据很简单：在那台机器上 `cd /root/build/station-console-ui && npm run build`，
> 产物里的入口包名应当与线上 `ls /opt/station/console-ui/dist/assets/index-*.js` **一致**。
>
> 同步方式：把仓库对应目录打包 scp 过去覆盖（`tar --exclude=node_modules --exclude=dist`），
> **但 `station-site/.env` 和 `station-console/application-local.yml` 不要覆盖**——
> 那里面是线上地址和库密码，和仓库里的本地值不是一回事。

**更新**（改代码之后）：

```bash
# 后端
systemctl stop station-console
cp /opt/station/console/station-console-1.0.0.jar{,.bak}
install -o station -g station -m 644 /tmp/station-console-1.0.0.jar /opt/station/console/
systemctl start station-console

# 前台
systemctl stop station-site
rm -rf /opt/station/site/.output.bak && mv /opt/station/site/.output /opt/station/site/.output.bak
tar -xzf /tmp/site-output.tgz -C /opt/station/site/ && chown -R station:station /opt/station/site
systemctl start station-site
```

**回滚**：把 `.bak` 换回来，重启对应服务。数据库改动走 `sql/station/migration/`，
那些脚本只加不删，回滚旧版本代码时旧列还在，不影响。

---

## 十一、第一次落地记录（2026-09-05，铜陵站）

本手册的第一次实际执行。域名和配置都在
[deploy/nginx/tongling.conf](../deploy/nginx/tongling.conf)，这里只记**踩到的坑**
和**手册没覆盖的判断**。

> ⚠ **下面这张表里的服务器和证书张数已经变过（2026-09-09），按当前值读，别按旧值部署。**
> 原来的服务器 129.211.4.32 已于 2026-09-09 整机迁走，**老机只作历史快照，
> 不要再往它上面部署**（`deploy/nginx/tongling.conf:3,8`）。

| | |
| --- | --- |
| 服务器 | **124.221.220.89**（Ubuntu 24.04，`deploy/nginx/tongling.conf:3`）。原 129.211.4.32 已迁走 |
| 域名 | 主域 `tongling.vclinker.com` 前台公开页 / `buyer.` 前台账号页 / `console.` 管理台 / `data.` 对外接口 |
| 证书 | 腾讯云免费 DV（TrustAsia），**四张**（主域 / `buyer.` / `console.` / `data.`），2026-12-04 到期，**手工续、没开自动续期**（`deploy/nginx/tongling.conf:54`） |
| 耗时 | 约 3 小时，其中一半花在重装系统上 |

**① 原来的服务器是 CentOS 7.8，装不了。** glibc 2.17，而 Node 18+ 的官方二进制要 2.28。
`nvm` 救不了（下的是同一份二进制）。CentOS 7 又已 EOL、官方源全下线。
**结论：重装成 Ubuntu 24.04**，机器是空的时候这么做成本为零。
选镜像时别选最新的 26.04——NodeSource 那类第三方源要几个月才跟上新版代号。

**② `http2 on;` 起不来。** 那是 nginx 1.25.1+ 的写法，Ubuntu 24.04 自带 1.24.0，
报 `unknown directive "http2"`——看着像模块没装，其实是版本不够。
写成 `listen 443 ssl http2;`（1.25 之后标为 deprecated 但仍有效）。

**③ Spring Boot 默认绑 0.0.0.0。** `ss -lntp` 看到 `*:8081` 时，8081 只靠安全组挡着，
而 boundary 要的是物理上不可达。加 `SERVER_ADDRESS=127.0.0.1`（见 §5.1）。

**④ 安全组默认有一条 `0.0.0.0/0 + ALL + 允许`。** 腾讯云新实例自带的"放通全部端口"。
真正救了这台机器的是**进程只绑本机**，不是安全组。收紧成 22/80/443 三条——
**先加规则、确认 SSH 还通，再删那条全放通的**，顺序反了会把自己锁在外面。

**⑤ 接口域名的分工是想清楚之后才定的。** 原计划是「两个前端都打 `data.*`」，
不成立：管理台靠 `CONSOLE_SESSION` Cookie，而 `SsoController` 是 `return "redirect:/"`，
`/sso` 只能挂在 `console.*` 上。最终方案见那份 conf 的文件头——
`data.*` 存在且可用，但只开 `/openapi/site/**`，自家页面走同源。

**⑥ 跳转密钥的明文只出现一次。** ERP 库里存的是 96 字符 AES 密文，
SELECT 出来粘过去不报错、只是每次签名不匹配。正确做法是在 ERP 的站点卡片上点
【重置】，弹窗里那串 43 字符的才是明文。站点服务器上那份是 NULL 时重置零风险。

**⑦ 一个站只能认一台 ERP 作为入口，换 ERP 要同步两样东西。**
先用本机 ERP 调通、后来改从生产 ERP 进入，结果是「进入管理」一直被拒。
原因是跳转要对上两份**由 ERP 登记时生成**的材料，而每台 ERP 各自生成、互不相通
（ERP 侧存密文、明文只显示一次；`sitecode` 来自各自的发号器）：

| | 站点侧 | ERP 侧 |
| --- | --- | --- |
| 签名密钥 | `u_s_siteinfo.consolesecret`（明文 43 位） | `u_s_site.consolesecret`（AES 密文 96 位） |
| 站点身份 | `u_s_siteinfo.sitecode` | `u_s_site.sitecode` |

⚠ **排查时会「修一个才冒出下一个」**——`JumpTokenVerifier` 是顺序短路的
（解析 → 有效期 → **验签名** → **核对 sitecode** → jti），签名不过根本走不到 sitecode 那一步。
所以先看到「令牌签名不匹配」，同步密钥之后才露出「令牌里的站点是 X，本站是 Y」。
这个顺序是刻意的：先证明是可信签发方，再谈要进哪个站。

换 ERP 的完整动作：新 ERP 上点【重置】拿明文 → 写进站点库 → 按日志里报的 X 对齐 `sitecode`
→ 顺手刷业务表（有 `sitecode` 列的九张表，趁没数据时是零成本）：

```bash
mysql sdsstation -N -e "SELECT CONCAT('UPDATE \`',table_name,'\` SET sitecode=''<新的>'';') \
  FROM information_schema.columns WHERE table_schema='sdsstation' AND column_name='sitecode';" | mysql sdsstation
```

**⑧ 终端粘贴多行命令会翻车。** heredoc 的 `EOF` 被加了缩进就匹配不上（shell 卡在 `>`），
`\` 续行同理。给别人的命令尽量写成单行，或者用 `printf '%s\n' 'a' 'b' > file`。

---

## 十二、不在本手册范围内

- **ERP 那一侧的配置**（站点档案里填管理后台域名、`STATION_CONSOLE_SECRET`）——
  在 ERP 那台机器上做，见 [独立站部署手册（Windows）.md](./独立站部署手册（Windows）.md) §7。
- **域名注册与备案**——走云厂商控制台的流程，本仓库不再有对应文档（原 `kangchen-domain-plan.md`
  已于 2026-09-06 删除；它 §14/§15 里和部署顺序有关的几条搬进了现行手册的附录 D）。
- **为什么前台是 SSR、为什么两边不互通**——见 `station-site/README.md`
  和 [独立站与ERP的边界.md](./独立站与ERP的边界.md)，本手册不重复。
