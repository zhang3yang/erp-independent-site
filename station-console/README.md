# station-console — 独立站后端

> **2026-09-06 整份重写。** 上一版写的是「验票 + 代理 ERP 数据」那一套，从
> [docs/独立站与ERP的边界.md](../docs/独立站与ERP的边界.md)（2026-09-03 定稿）
> 起整个不成立。旧版正文不是「有几处过时」，而是**在主动指导别人去建 ERP 互通**
> （`ErpClient.forward()`、`/api/**` → `/openapi/console/**`、`ERP_BASE_URL`），
> 按 boundary §12 末尾那条规矩，这种必须当场划掉、不能只加一行指引，所以直接重写。
> 旧版内容在 git 历史里。

一个站的全部后端：**前台开放接口 + 管理台接口 + 本站用户体系 + 本站的库**。

**和 ERP 没有任何互通。** 依赖列表里没有任何指向 ERP 的 HTTP 客户端
（RestTemplate / WebClient / OkHttp 一个都不引），也没有 `ruoyi-common` / `ruoyi-system`。
这是靠 `pom.xml` 保证的，不是靠自觉——想加一条「顺便去 ERP 查一下」，得先动那个文件，
那一刻会看到顶部那段注释。验收方式（必须零结果）：

```bash
grep -rn "openapi/console\|ErpClient\|erp\.base-url" station-console/src
```

唯一的联动是**从 ERP 点一下、免密进管理台**，而且是本地验签、不回调 ERP。

配套文档：
- [docs/独立站与ERP的边界.md](../docs/独立站与ERP的边界.md) —— 边界的唯一权威
- [docs/独立站部署手册（Windows）.md](../docs/独立站部署手册（Windows）.md) —— 怎么装

---

## 技术栈与结构

Spring Boot **4.0.6** / Java 17 / MyBatis / MySQL / Redis。默认端口 **8081**，只监听回环。

> ⚠ Boot 4 带进来的是 **Jackson 3**（`tools.jackson.databind`），而 RuoYi-Vue 是 Boot 3 +
> Jackson 2（`com.fasterxml.jackson.databind`）。从 ERP 往这边搬代码时包名要改，
> 症状是编译期就报，不会漏到运行时。

```
web/       7 个 Controller + AuthInterceptor + 2 个 ExceptionHandler
service/   17 个（含限流、发信、编号生成）
mapper/    14 个 + resources/mapper/ 下 14 个 XML
domain/    14 个
security/  JumpToken · JumpTokenVerifier · @RequirePerm
session/   ConsoleSession · BuyerSession · ChatVisitor · ConsoleIdentity
config/    ConsoleProperties · StationProperties · MybatisConfig · WebMvcConfig
util/      AesUtils · CosClient · RichTextCleaner · HTMLFilter
```

### 为什么不是 RuoYi-Vue 的子模块

它跟 `RuoYi-Vue` 在同一个仓库，但**是一个独立的 Maven 工程，不在那个 reactor 里**。

一旦它成为子模块，`ruoyi-system` 就在 classpath 上了，下一个赶进度的人会很自然地
`@Autowired` 一个 ERP 的 Mapper——那一刻「两边后端没有任何互通」就没了，
而且没有任何编译错误提醒他。

---

## 数据

**只有一个数据源，指着本机的 `sdsstation`。** 15 张 `u_s_*` 表，见
[`sql/station/01_station_schema.sql`](../sql/station/01_station_schema.sql)（基线）
和 [`sql/station/migration/`](../sql/station/migration/)（增量，按文件名日期顺序跑）。

mapper XML 里**一律不写库限定名**（写 `from u_s_sitegoods`，不写
`from sdsstation.u_s_sitegoods`）。不写库名就物理上不可能再指到别的库去，
这比写一句注释管用。XML 里出现的 `sdsshop.xxx` 全部在注释里，说明的是
「以前 join 过、现在删了」。

---

## 三块 API，三种身份

| 前缀 | 谁在用 | 会话 | Controller |
|---|---|---|---|
| `/openapi/site/**` | 前台，匿名或买家 | `BUYER_SESSION`（30 天）/ `CHAT_VISITOR` / 匿名 | `OpenApiController`、`BuyerAccountController`、`SiteChatController` |
| `/api/**` | 管理台 | `CONSOLE_SESSION`（Redis，8h） | `ConsoleApiController`、`ConsoleChatController` |
| `POST /sso` | 从 ERP 跳转 | 建立管理台会话 | `SsoController` |

`/api/**` 每个端点都挂 `@RequirePerm("site:goods:edit")` 这样的权限点，
判定在 `web/AuthInterceptor` + `service/SiteUserService#permsOf`。

### `POST /sso` 是进管理台的唯一入口

**没有登录页**，这是设计不是缺失。`/login` 是前端的一个空页面（保留只因为几处硬编码了这个路径），
`SessionController` 只剩一个 `POST /api/auth/logout`。中间有一版加过账号密码登录，撤掉了，
取舍见 boundary §6a′ 和 `web/SessionController` 的类注释。

协议是**自包含签名令牌 + POST 表单**：ERP 用该站登记的密钥签 HMAC-SHA256（60 秒有效），
前端造一个隐藏 form 提交过来，这边纯本地验签 + Redis 查 `jti` 没用过——**不回调 ERP**，
ERP 整个停掉也能进。细节在 `security/JumpTokenVerifier` 和 boundary §4。

只收 POST 不收 GET：令牌本身就是凭据，进了 URL 就会进浏览器历史、书签、nginx access log
和外链的 `Referer`。**不要为了调试方便加一个 `?t=` 的 GET 分支。**

跳转密钥的正位是库里的 `u_s_siteinfo.consolesecret`（**明文**，43 字符，由 ERP 生成、
只显示一次）。⚠ ERP 库里存的是 AES-GCM 密文，把那串 96 字符粘过来不报错，
只是每次验签都不匹配。

---

## 跑起来

前置：**MySQL**（本机 `sdsstation`）+ **Redis**（会话、jti、限流都在里面）。ERP 不需要在。

```bash
# 开发（本地 http，所以要关掉 Cookie 的 Secure）
CONSOLE_COOKIE_SECURE=false \
STATION_BUYER_COOKIE_SECURE=false \
DB_PASSWORD=你的密码 \
ERP_HOME_URL=http://localhost \
mvn spring-boot:run

# 打包
mvn clean package -DskipTests
java -jar target/station-console-1.0.0.jar
```

本地要进管理台看看，手工往 `u_s_user` 插一个账号是没用的（没有登录页）。
要么用密钥自签一个令牌 POST 到 `/sso`，要么把 `u_s_siteinfo.consolesecret` 设成一个已知值
再自签。

### 环境变量

`application.yml` 里全部带默认值，下面这些是**生产上必须显式给**的：

| 变量 | 说明 |
|---|---|
| `DB_PASSWORD` | 数据库口令。账号默认 `station`，只授权 `sdsstation` |
| `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` | ⚠ **漏了 SSO 会「验签成功但登不进去」**：后端在 nginx 后面看到的 scheme 是 http，302 会掉到 `http://`，Secure Cookie 在那一跳上不发送。顺带它也让 `getRemoteAddr()` 返回真实 IP，询盘按 IP 限流才准 |
| `STATION_UPLOAD_DIR` / `STATION_FILE_BASE_URL` | 落盘目录和对外前缀。⚠ 后者会被**完整拼进** `imageurl`/`videourl` 存库，一旦有人上传过就改不动了 |
| `CONSOLE_HOME_URL` | 管理台自己的对外地址，询盘通知邮件里「去处理」的链接用它拼 |
| `ERP_HOME_URL` | 顶栏上一个**显示用**的链接，后端不会去调它 |
| `STATION_CONFIG_SECRET` | `u_s_storage` 里的 COS SecretKey 用它加密存（`util/AesUtils`）。换了它已存的密钥全部解不开 |

SMTP 走 jar 同目录的 `application-mail.yml`（模板 `application-mail.yml.example`），
不走环境变量——里面有密码，而 nssm 的环境变量在注册表里是明文。
⚠ 那个文件的优先级**高于** `application.yml`，所以里面只放 `spring.mail.*` 和
`station.notify-from`，**不要**再写一份 `console.home-url` 去顶掉环境变量。

**`STATION_CONSOLE_SECRET` 和 `ERP_BASE_URL` 这两项已经不存在了。** 前者是票据兑换的凭据、
后者是回调 ERP 的地址，那条回调整个删了（boundary §4.1）。看到哪份文档还在教你配它们，
那份是分家之前的。

---

## 几个容易改坏的地方

**`console.grant-all-perms` 默认是 `true`。**
也就是**任何从 ERP 进来的人都拿到全部权限**，`u_s_role` 不参与判定。站点只有一两个操作员时
这样最省事，但一旦要区分角色，必须显式设成 `false`，否则 `@RequirePerm` 形同虚设。
启动日志里有一条 WARN 说明当前模式。

**Cookie 的 `same-site` 必须是 `lax`，不能改成 `strict`。**
进管理台的第一跳是从 ERP 域发起的顶层导航，浏览器把整条重定向链的发起方都算成最初那个跨站页面。
`strict` 的 Cookie 不会被带到 `/sso` 之后那一跳上——表现是「从 ERP 点进来立刻被弹回空页面」，
而且本地手动刷新一切正常，非常难查。

**`ConsoleSession.start()` 里那句 `invalidate()` 不能删。**
防会话固定攻击：攻击者先在受害者浏览器里种一个自己知道的 session id，等验签成功后那个 id
就成了有效会话。换一个全新的 session 是唯一的解法。

**下单接口不信前端传的价格。**
`POST /openapi/site/order` 的请求体里只有 `goodsCode` 和 `qty` 会被采用，价格、商品名、单位
全部由服务端从 `u_s_sitegoods` 现查，小计也在服务端算。前台那份清单在 localStorage 里，
改一个数字就能把单价变成 0.01。

**询盘和下单共用同一个限流额度。**
对一个买家来说「问一批货」和「提一张清单」是同一件事，分两个额度等于把限额放宽一倍。

### 两个「不报错但不生效」的坑

都是 Spring Boot 4 拆模块引起的，各自在代码里写了完整说明：

- **MyBatis 自动配置不生效** → `config/MybatisConfig`
  症状：`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`，
  看着像漏配一项，其实是整套自动配置被静默跳过。
- **会话没进 Redis** → `pom.xml` 里 `spring-boot-starter-session-data-redis` 那段
  症状：什么都正常，只是后端一重启所有人掉线。
  判据是 `redis-cli keys 'station:console:session*'` 有没有 key。

---

## 部署

前端静态文件（`station-console-ui` 的 dist）和这个服务放在**同一个域名**下。
同源不是省事，是设计的一部分：跨域的话 `SameSite=Lax` 的会话 Cookie 带不过去，
还得额外处理 CORS（`station.cors.allowed-origins` 就能一直留空）。

- nginx：[`deploy/nginx/station.windows.conf.template`](../deploy/nginx/station.windows.conf.template)
  （Windows，从线上实配反向生成）或 [`deploy/nginx/tongling.conf`](../deploy/nginx/tongling.conf)（Linux）
- 注册成服务：[`station-site/deploy/install-service.ps1`](../station-site/deploy/install-service.ps1)
  ——`-Target console` 就是这个服务，`-Target site` 是前台
- 完整步骤：[docs/独立站部署手册（Windows）.md](../docs/独立站部署手册（Windows）.md)
