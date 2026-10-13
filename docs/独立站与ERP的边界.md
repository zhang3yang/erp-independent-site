# 独立站与 ERP 的关系（定稿）

**定稿依据**：`独立站与ERP的关系.docx`（2026-09-03 收到）。
**这一份是边界的唯一权威。** 凡是旧文档和它冲突的地方，以这一份为准，冲突清单见 §12。

> **它回答一个问题**：ERP 和独立站，哪些东西归谁、两边靠什么连着。
> **它不回答**：单个站怎么装（看 [独立站部署手册（Windows）.md](./独立站部署手册（Windows）.md)，
> Linux 上看 [独立站部署手册（Linux）.md](./独立站部署手册（Linux）.md)）。

---

## 目录

- [0. 一句话](#0-一句话)
- [1. 七条改动要点（原文）](#1-七条改动要点原文)
- [2. 全景图](#2-全景图)
- [3. 模块归属](#3-模块归属)
- [4. 唯一的联动：登录跳转](#4-唯一的联动登录跳转)
- [5. 数据边界：两边各自的库](#5-数据边界两边各自的库)
- [6. 用户体系：一站一套](#6-用户体系一站一套)
- [7. 独立站在 ERP 里的登记项](#7-独立站在-erp-里的登记项)
- [8. 灰色部分：订单拉取 / 生产进度同步](#8-灰色部分订单拉取--生产进度同步)
- [9. buyeruser / buyershop 保留不删](#9-buyeruser--buyershop-保留不删)
- [10. 一期迁移清单（逐文件）](#10-一期迁移清单逐文件)
- [11. 一期不做、但要留位置的](#11-一期不做但要留位置的)
- [12. 这一份让哪些旧结论作废](#12-这一份让哪些旧结论作废)
- [13. 验收：怎么证明「完全分离」真的成立](#13-验收怎么证明完全分离真的成立)

---

## 0. 一句话

**ERP 和独立站是两个互不认识的系统，各自有库、各自有用户、各自能独立跑起来；
它们之间只有一条线，就是「从 ERP 点一下，免密进到独立站后台」。**

---

## 1. 七条改动要点（原文）

docx 正文的七条，原样抄在这里，后面各节是对它们的展开：

1. ERP 和独立站完全分开部署，独立站与 ERP 的后端没有任何互通。
2. ERP 主要负责公司内部管理和订单交付。
3. 独立站主要负责从谷歌引流、展示商品、成交订单、客户交流等等。
4. ERP 中的订单和独立站中的订单没有任何隶属关系，独立站产生订单后，ERP 可以自动从独立站拉取订单
   （前期都可以像拼多多一样手工同步订单，后期再做对接，这样便于理解独立性），
   ERP 的生产进度可以向独立站定时同步。
5. 每套独立站自己成立用户体系，和其他独立站之间、和 ERP 之间相互独立，不再关联。
6. ERP 系统中订单和客户中的 buyeruser 和 buyershop 暂时保留不删，内容默认空串即可。
7. 在 ERP 系统中的独立站，需要登记独立站的名称、域名、服务器 IP 地址等信息，
   从 ERP 跳转到独立站的时候携带登录信息，供独立站操作用户使用。
   域名信息包括：主域名 `domain.com`；后端服务域名 `data.domain.com`；文件域名 `file.domain.com`；
   管理后台域名 `console.domain.com`；买家后台域名 `buyer.domain.com`。

**第 1 条是硬约束，其余六条都是它的推论。** 判断任何一个改动该不该做，先问：
它会不会让「两边后端没有任何互通」这句话变成假的。

---

## 2. 全景图

docx 里那张图，原样复刻（**虚线 = 图里的灰色 = 一期不做**）：

```
            ┌─────────────────────────────────────────────────────┐
            │                       ERP                           │
            │                                                     │
            │   员工管理   库存管理   采购管理   财务管理            │
            │   订单管理   生产管理   独立站管理                     │
            └─────────────────────────────────────────────────────┘
                  │                    ▲                 ┊
                  │ 登录跳转            ┊ 订单拉取         ┊ 生产进度同步
                  │ （实线，一期做）      ┊ （灰，不做）      ┊ （灰，不做）
                  ▼                    ┊                 ▼
            ┌─────────────────────────────────────────────────────┐
            │                     独立站  × N                      │
            │        （一站一台服务器、一套库、一套用户体系）           │
            │                                                     │
            │   首页管理   商品管理   博客管理   订单管理             │
            │   客户管理   营销管理   站长工具   询盘管理             │
            └─────────────────────────────────────────────────────┘
```

三条箭头在 docx 里的原始颜色，逐条核对过（这是「哪些不做」的判据，不是猜的）：

| 箭头 | 方向 | docx 里的颜色 | 一期 |
| --- | --- | --- | --- |
| **登录跳转** | ERP → 独立站 | 箭头 accent6（绿），文字默认黑 | ✅ 做 |
| 订单拉取 | 独立站 → ERP | 箭头 accent3 = `A5A5A5`，文字 `767171` | ❌ 不做（§8） |
| 生产进度同步 | ERP → 独立站 | 箭头 accent3 = `A5A5A5`，文字 `767171` | ❌ 不做（§8） |

「独立站」那个框在 docx 里是**三层叠放**的，意思是 N 个站，不是一个。
每一层都是完整的一套，互相之间也没有任何互通（§6）。

---

## 3. 模块归属

**一个模块只能属于一边。** 出现「两边都有一份」的时候，说明边界画错了，回来改这张表，
不要靠同步兜。

### 3.1 ERP（七个）

| 模块 | 归属理由 |
| --- | --- |
| 员工管理 | 公司内部人事，与买家无关 |
| 库存管理 | 实物库存在公司仓里 |
| 采购管理 | 对供应商，公司内部流程 |
| 财务管理 | 公司账 |
| 订单管理 | **公司自己的订单**。和独立站订单没有隶属关系（要点 4） |
| 生产管理 | 排产在公司 |
| 独立站管理 | 只登记「有哪些站、在哪、叫什么」，不管站里的内容（§7） |

### 3.2 独立站（八个）

| 模块 | 一期状态 | 说明 |
| --- | --- | --- |
| 首页管理 | ✅ 搬 | 现有「首页装修」，整块 JSON |
| 商品管理 | ✅ 搬 + 改 | 从「在 ERP 库存目录里挑商品上架」改成**站点自己录商品**（§5.4） |
| 询盘管理 | ✅ 搬 | 含邮件通知；「询盘转 ERP 客户」这个动作删掉（那是一条互通） |
| 订单管理 | ✅ 已做（2026-09） | 站点自己的订单，与 ERP 订单无关 |
| 客户管理 | ✅ 已做（2026-09） | 站点自己的买家档案 |
| 博客管理 | ✅ 已做（2026-09-08） | SEO 内容 |
| 营销管理 | ✅ 已做（2026-09-09） | 留位时设想的是邮件群发，实际做成了**询盘跟进看板**。为什么不做群发见 `设计稿/2026-09-09-营销管理（未采纳）.md` |
| 站长工具 | ✅ 已做（2026-09-08） | sitemap / robots / 收录状态 |

「留位置」的确切含义见 §11——**不是建空表**。

> **2026-09-18 整理时补记**：上表五个「留位置」的模块**后来全部做完了**，所以 §11 那张表里
> 除「买家后台」以外的格子都已经不成立。当年这么写的依据（也是反驳本表的原始出处）在
> `station-console-ui/src/router/index.js` 顶部注释。

---

## 4. 唯一的联动：登录跳转

### 4.1 为什么现在这套协议必须改

现在的单点登录是**一次性不透明票 + 服务端兑换**：URL 上放一串随机数，管理台后端拿着
`clientSecret` 回调 ERP 的 `POST /openapi/console/redeem` 把票换成身份
（协议原文在已删除的 `station-console-sso.md` §2 第③步，git 历史里可查）。

**那第③步就是一条后端互通**，而且是最硬的一条：它要求管理台能连到 ERP 的内网地址、
要求两边共享一个 `clientSecret`、要求 ERP 常年为境外服务器开一个入口。
要点 1 直接否掉它。

### 4.2 新协议：自包含签名令牌 + POST 表单

```
[ERP 前端] 独立站管理，点「进入管理」
   │ ① POST /site/station/{siteCode}/console-jump     带 ERP 登录态
   ▼
[ERP 后端] 校验：站点存在 / 属于本商户 / 未停用 / 有 site:station:list 权限
           用**这个站登记的跳转密钥**签一个令牌（HMAC-SHA256，有效期 60s）
           就地记一次 lastaccesstime
           返回 { action: "https://console.domain.com/sso", token: "<payload>.<sig>" }
   │ ② ERP 前端造一个隐藏 form：method=POST、action=①返回的地址、target=_blank，提交
   │    ↑ 令牌在**请求体**里，不在 URL 上
   ▼
[独立站后端 /sso]
   │ ③ 用自己那份密钥验签 → 查 exp → 查 jti 没用过（Redis，一次性）
   │    ↑ **不回调 ERP**。这一步是纯本地计算，ERP 那边不需要在线
   │ ④ 按 payload 里的用户标识匹配/落一个**本站自己的**操作用户，建本站会话
   ▼ ⑤ 302 到管理台首页
```

**为什么令牌走 POST 而不是 `?t=`**：自包含令牌只要进了 URL，就会进浏览器历史、
进书签、进 nginx access log、进任何外链的 `Referer`——而它本身就是凭据，
不像旧方案那样「捡到也得有 clientSecret 才换得动」。POST 表单让它只出现在请求体里，
这是 SAML 的 HTTP-POST binding 用了二十年的办法。

Cookie 能不能种上：跨站 POST 的**响应**里 `Set-Cookie` 是允许的（`SameSite=Lax`
限制的是「跨站请求带不带 Cookie」，不是「跨站响应能不能种 Cookie」）；
第⑤步 302 之后是 `console.domain.com` 到自己的同站顶级 GET 导航，Lax 放行。

### 4.3 令牌内容

```
payload = base64url(json)   sig = base64url(HMAC-SHA256(payload, 站点跳转密钥))
token   = payload + "." + sig
```

```json
{
  "ver":  1,
  "iss":  "erp",
  "site": "DLZ1001",              // 必须和本站档案里的站点编码一致，不一致直接拒
  "user": "zhangsan",             // ERP 的登录账号，只作为**身份标识**
  "name": "张三",
  "iat":  1756180000,
  "exp":  1756180060,             // 60 秒
  "jti":  "9f2c…"                 // 一次性，独立站侧存 Redis 防重放
}
```

**payload 里没有 `perms`。** 这是有意的：权限归独立站自己那套（§6）。
ERP 只说「这是谁」，不说「他能干什么」——后者由独立站的角色配置决定。
把 ERP 的权限码塞进来，等于两边的权限模型又绑在一起了。

#### 4.3.1 可选的 `scope`：只有一个值 `users`（2026-09-24）

```json
{ …同上…, "scope": "users" }     // 不带 = 普通的「进入管理」
```

管理台左边的「用户管理」拿掉了：那一页全是 ERP 登录名、「ERP 免密」来源，不该给海外客户
（Esva boots 站长）看。入口挪到 ERP「独立站管理」卡片上的「站点用户」按钮
（`POST /site/station/{code}/users-jump`，要 ERP 的 `site:station:edit`），签的令牌多带 `scope=users`。

- 站点侧：`JumpTokenVerifier` 只认 `users` 这一个值，别的值直接拒（不是当普通进入放行）；
  `SsoController` 给这种会话打 `ConsoleIdentity.userAdmin`，302 到 `/site-users`（不在菜单里、不走外壳）。
- 用户接口（`/api/user/**`）**只认这个标记，不认角色**：站长角色、`CONSOLE_GRANT_ALL_PERMS` 的 `*`
  都开不了。`u_s_role` 里的 `site:user:*` 因此不再起作用。
- 这和上面那条「不带 perms」的区别：`scope` 不描述这个人在站点里能干什么，只描述这一次从哪个按钮进来。
  但结果上，**「谁能管站点用户」这一件事从站点的角色表移到了 ERP 的 `site:station:edit`**——有意的取舍，
  §6「权限由站长在本站调整」对用户管理这一项不再成立，其余权限不变。
- 仍然只是登录跳转，没有新增任何后端互通（§13 第 5、6 条不受影响）。
- 外部账号的 `/station/login`（`StationLoginController`）永远签不带 `scope` 的令牌。

### 4.4 这套新协议比旧的弱在哪（明说）

旧方案里，令牌落到日志上也没用——兑换要 `clientSecret`。新方案里，
**60 秒内拿到令牌原文的人可以直接用它**。这是「后端不许互通」的代价，不是疏忽。

一期靠这四条把窗口压到可接受：

1. 令牌走 POST，正常路径下它不出现在任何 URL、日志、`Referer` 里；
2. 有效期 60 秒；
3. `jti` 一次性，独立站侧 `GETDEL` 语义消费，抢在用户前面用掉会让**真正的用户进不去**——
   这是一个会被立刻发现的失败，不是静默劫持；
4. 令牌只对**签它的那一个站**有效，密钥一站一份，泄一个不影响别的站。

**必须守住的两条**：`/sso` 只接受 POST（GET 一律回 /login 那个空页面）；签名用定长比较，不要 `equals` 短路。

### 4.5 顺带没了的东西

- ERP 侧的 `POST /openapi/console/redeem` 删掉，`clientId` / `clientSecret` 这对配置删掉。
- `STATION_CONSOLE_BASE_URL` 这个**全局**管理台地址删掉——地址现在一站一份，
  从站点档案的域名派生（§7），全局兜底反而会让 A 站的人跳到 B 站。
- ERP 的 `lastaccesstime` 改成**发令牌时**打点。语义从「进成功了」变成「点了进入管理」，
  差别得写在列注释里，不然日后有人拿它当活跃度指标会算错。
- ERP 侧那个「外部管理台没配就回退到 ERP 内置页」的分支删掉——内置页本身就要删（§10.1）。
  没登记域名的站，「进入管理」按钮就该是灰的，而不是跳到一个 ERP 自己实现的替身。

---

## 5. 数据边界：两边各自的库

### 5.1 规矩

| | ERP 服务器（国内） | 独立站服务器（一站一台，境外） |
| --- | --- | --- |
| MySQL 实例 | ERP 自己那台 | 站点自己那台 |
| 库 | `sdssys` / `sdsshop` / `sdsorder` | `sdsstation`（本站一份） |
| 谁能连 | 只有 ERP 进程 | 只有本站进程 |
| 同实例跨库查询 | 有（三个库之间，现状照旧） | 无，只有一个库 |
| 跨服务器查询 | **不存在，网络上也不通**（§13 第 6 条） | 同 |

**没有副本，没有同步，没有「以谁为准」。** 之前那套「独立站本地库只存副本、
订单库存不在本地做主」的设计整体作废——它的前提是两边有一条同步通道，
而那条通道现在不存在了。

### 5.2 `sdsstation` 整个搬到独立站服务器

现在 `sdsstation` 和 ERP 的三个库在**同一个 MySQL 实例**上，mapper XML 里用的是
**跨库限定名**（`from sdsstation.u_s_siteimage`、`join sdsshop.u_m_waregoods`），
不是多数据源——ERP 只有一个 Druid 数据源，指着 `sdssys`。

这一点决定了搬迁**没有中间形态**：

> 跨库限定名只在同一个 MySQL 实例里成立。`sdsstation` 一旦搬到另一台服务器，
> 所有带 `sdsstation.` 前缀和 `join sdsshop.` 的语句**当场失效——不是变慢，是报错**。
> 所以「先搬库、以后再改代码」这条路不存在：搬库和拆代码必须在同一步里完成。

去处：

| 表 | 去处 | 之后归谁 |
| --- | --- | --- |
| `u_s_site`（站点档案） | **移入 ERP 的 `sdsshop`** | ERP，瘦身成登记表（§7） |
| `u_s_sitegoods` / `u_s_sitegoods_lang` | 跟着库搬到站点服务器 | 站点 |
| `u_s_siteimage` / `u_s_sitevideo` | 同上 | 站点 |
| `u_s_inquiry` | 同上 | 站点 |

搬完之后的终局：

- **ERP 服务器上不再有 `sdsstation` 这个库**，最后一步 `DROP DATABASE sdsstation`。
- **每台独立站服务器上有一个 `sdsstation`**，装本站的业务数据，里面**没有** `u_s_site`。

为什么 `u_s_site` 进 `sdsshop`，而不是在 ERP 侧留一个只剩一张表的 `sdsstation`：
它是按 `shop_code` 分租户的商家资产，和 `u_m_buyer` / `u_m_waregoods` 是同一类东西，
放 `sdsshop` 才对得上分库的口径；更实际的理由是，ERP 侧留一个同名库，
下一个人看到 `sdsstation` 还在，会以为独立站的数据仍然在这边。

> **⚠ 一个最容易搬错的表**：`s_m_shop_storage`（对象存储配置）**不在 `sdsstation`，
> 在 `sdssys`**，而且 ERP 自己的文件上传也在用它（`ruoyi-common` 的 `OssStorageService`
> / `OssProperties`）。它**留在 ERP 原地不动**，站点自己新建一份。
> 它之所以看着像独立站的表，只是因为 mapper 文件被放进了 `mapper/station/` 目录。

### 5.3 站点库的表名和列

站点库**沿用 `sdsstation` 库名、沿用 `u_s_*` 表名**，迁移就是一次

```
mysqldump sdsstation --ignore-table=sdsstation.u_s_site  →  站点服务器 restore
```

不顺手改名：改名要同时改数据和每一条 SQL，而迁移这一步应当只搬东西、不改结构，
出了问题才好判断是搬坏了还是改坏了。

站点侧的 mapper **去掉库限定名**，写成 `from u_s_sitegoods`。站点后端只连一个库，
不写库名就物理上不可能再指到别的库去——这比写一句注释管用。

三处必须变的：

- **新增表**：`u_s_siteinfo`（本站档案，装从 ERP 那张表上摘下来的那批列：站点编码、
  名称、语言、币种、状态、装修 JSON，见 §7.2）、`u_s_user`（操作用户，§6）、
  `u_s_storage`（本站存储配置，替代 `sdssys.s_m_shop_storage`）。
- **`shop_code` 降级为遗留列**：一个部署一个站，租户列在这里没有意义。
  但**不删**——删它要动每一条 SQL 和每一个 domain。约束写进列注释：
  值恒为建站时那个商户码，新代码不得读它、不得按它过滤。将来清理是一次独立的动作。
- **`sitecode` 保留**：跳转令牌的 `site` 声明要和 `u_s_siteinfo.sitecode` 对齐（§4.3）。

### 5.4 商品：从「挑」变成「录」

现在的商品链路是：ERP 库存目录 → 在管理台里挑 → 写进 `u_s_sitegoods`。那张表只存
站点售价、分类、推荐位这些**站点侧属性**，名称、图片、规格、单位都还在 ERP 的
`sdsshop.u_m_waregoods` 里，取数时 `join` 回去（`StationSiteGoodsMapper.xml` 里三处）。

**这条 join 就是一条互通**，而且它是跨库的——库一搬走，它连语法上都成立不了。
所以商品必须变成站点自己的完整档案：名称、描述、图片、规格、价格、分类全落在
`u_s_sitegoods`（不够的列往上加）。代价是同一个商品在 ERP 和三个站里各录一遍，
这是要点 1 的直接成本，一期认了。

> 后期真要省这个人工，走的是「导出 / 导入一个商品包」这种**离线**方式，
> 不是重新开一条实时接口。

---

## 6. 用户体系：一站一套

要点 5：每套独立站自己成立用户体系，站与站之间、站与 ERP 之间都不关联。

一期落到代码上是三件事：

**a′) 管理台没有登录页。唯一入口是从 ERP 点「进入管理」。**

上面那一条实现过，然后**整个撤掉了**。这一节保留原文划掉，是因为它的理由不假——
撤掉是一个取舍，不是发现了一个错误，写清楚才不会有人第三次改回来。

| | 有登录页（a） | 没有登录页（a′，现在） |
| --- | --- | --- |
| ERP 挂了 / 网断了 | 站长还能用账号密码进后台 | **没有人能新进后台**。已经登进去的人不受影响（会话在 Redis 上，8 小时不活跃才失效）；前台照常卖货 |
| 本站的凭据 | 一套可以被撞库、被弱口令攻破的密码 | **没有第二套凭据**。入场只认 ERP 用一站一份的密钥签出来的一次性令牌 |

选后者的判断是：那个「ERP 挂了进不去」的窗口，代价是**这段时间没人能改商品**；
而一个常年挂在公网上、没人管密码强度的登录框，代价是**有一天站被人进去了**。
前者是可恢复的，后者不是。

落到代码上：
- 删了 `POST /auth/login`、`GET /auth/state`、`POST /api/auth/password`
  （整个 `/auth/**` 公开路径组，只剩 `/api/auth/logout`）；
- 删了「重置密码」和「新建用户」——本站建出来的账号根本进不来（免密进入按 `erpuser`
  找人），而「重置密码」当时的作用正是**在唯一入口旁边开一扇门**；
- `u_s_user.password` 那一列废弃，没有任何代码读写它；
- `/login` 这个前端路由留着，但它是一个**空页面**：直接打开什么都没有，
  只在从 ERP 跳转被拒时显示一句原因（「令牌无效或已过期」「你在本站的账号已被停用」）。
  不写「请从 ERP 进入」——那是白送给扫域名的人的情报。

> 要一个应急入口就单独做，加 IP 白名单 + 二次验证，和这条正常链路完全分开
> （§6d 末段），**不要把登录页加回来**。

**b) ERP 跳转是唯一入口，进来之后权限归本站。** 令牌验通之后，
按 `payload.user` 在本站用户表里找人：找到就用他的角色进；找不到就按「首次进入」
落一个用户，给一个默认角色，之后这个人的权限由**站长在本站**调整，与 ERP 无关。

> 这一条原来叫「ERP 跳转变成『免密进入』，不是『唯一入口』」——前半句还对
> （进来之后是本站的身份、本站的权限），后半句随 a′ 一起作废了。

**c) 买家账号也是站点自己的。** 买家账号属于站点，
不与 ERP 的 `u_m_buyer`（ERP 销售客户）发生任何关系。

> **「买家中心本身不实现」那半句作废了（2026-09-04）。** 原文是「一期只登记域名、
> 预留 nginx，买家中心本身不实现（§11）」。现在**前台买家登录做了**：
>
> - 表：`u_s_buyer` 加了 `passwordhash` / `emailverified` / `status` 等七列
>   （`sql/station/migration/2026-09-04-buyer-login.sql`）。**没有新建一张账号表**——
>   买家的身份本来就是邮箱，而那张表已经按邮箱唯一了，于是「先问过价、后来才注册」
>   的人注册时认领的就是他自己那一行，登录进去立刻看得到之前的询盘和订单；
> - 后端：`/openapi/site/account/**`（注册 / 邮箱验证 / 登录 / 退出 / 忘记密码 /
>   重置 / 改密码 / 改资料 / 收货地址簿 / 我的订单），实现在 `BuyerAuthService` +
>   `BuyerAccountController`，会话是自己一套（`BuyerSession`：不透明令牌 + Redis +
>   `BUYER_SESSION` Cookie，30 天滑动）；
> - 前台：`login` / `register` / `verify-email` / `forgot-password` /
>   `reset-password` / `account` 六页。
>
> **2026-09-18 整理时更正（原文说的是「方案定了但还没上线」，已经不对了）：**
>
> **统领站 2026-09-09 就已经两 host 上线了**：主域是商城，`buyer.` 只放「我的东西」
> （`deploy/nginx/tongling.conf:6`）。原文「⛔ 卡在主域备案上…线上现在仍然是单 host」
> 是错的——备案不是问题，当时真正缺的是证书，而证书后来也配上了
> （`deploy/nginx/tongling.conf:118-120`）。**康辰站仍是单 host**（前台整站挂在
> `buyer.` 子域），逐站的线上状态以各站的 nginx 配置为准。
>
> 这一段原来写的是「仍然没做的是那个独立子域 `buyer.domain.com`——买家中心就挂在
> 主站的 `/account` 上」，理由是「把他甩到另一个子域要么共享 Cookie（扩大会话暴露面），
> 要么让他再登一次」。
>
> 现在前台劈成了两个 host（`deploy/nginx/tongling.conf`）：
>
> | host | 放什么 |
> | --- | --- |
> | 主域 | 公开页：首页、商品、**购物车、我的询价**，以及**登录 / 注册 / 找回密码 / 重置密码 / 邮箱验证** |
> | `buyer.<主域名>` | 只有**买家中心两页**：`/account`（我的资料）、`/orders`（我的订单） |
>
> ⚠ **登录注册留在主域是刻意的**，用户 2026-09-08 专门确认过「登入也放主域」。
> 唯一的归属清单是 `station-site/shared/utils/buyerPaths.ts` 里的
> `BUYER_PATHS = ['/account', '/orders']`，由 `station-site/server/middleware/domain-split.ts`
> 做 301。**nginx 里一条路径分流正则都不要加**——2026-09-09 就是因为 nginx 抄了第二份
> 清单、和 Nuxt 那份打架，把 `/login` 转成了死循环（`deploy/nginx/tongling.conf:15-25`）。
>
> 上面那两个顾虑的答案分别是：**共享 Cookie 那条最后没选**。现在是**永远 host-only**
> （`BuyerSession.java` 明写不设 Domain），两个 host 共用登录状态靠 `BuyerHandoff`
> 那张 **60 秒一次性票**交接。当初考虑过的「放宽 `STATION_COOKIE_DOMAIN` 到主域」
> 已经被否掉：`file.<主域>` 是 CNAME 到腾讯云 COS 的，带 Domain 的 Cookie 会跟着
> 每一张图片请求发到第三方（`deploy/nginx/tongling.conf:27-33` 有完整理由）。
> 「让他再登一次」也被明确否掉了——那不是两个应用，是同一个前台的两半。
>
> ⚠ **购物车和「我的询价」留在主域**，不要照着「买家的东西都归 buyer.」去挪：
> 它们的数据在 `localStorage` 里、按源隔离，挪过去买家在主域商品页加的货那边就是空的，
> 而且页面不报错。这两页本来也不要求登录（见下一条）。
>
> ⚠ 它仍然不是 §11 说的那个「买家后台」模块——那一格指的是一套独立的买家自助系统，
> 那个还是空的。这里只是把已有的买家中心两页（`/account`、`/orders`）搬到了另一个 host 上。
>
> ⚠ **下单和询盘仍然不要求登录。** 那两个接口一个字都没改——外贸站挡一道登录会掉单。
> 账号是加分项（换设备也能看订单），不是门槛。
>
> **2026-09-08 又加了收货地址簿和「我的资料」。** 新表 `u_s_buyeraddr`
> （`sql/station/migration/2026-09-08-buyer-address.sql`），仍然是站点自己的表，
> 和 ERP 无关。要点两条：
>
> - 那张表对外用**自增 id**（不发业务码），所以每一条 SQL 的 where 里都必须带
>   `buyercode`，而 `buyercode` 只从买家会话取。少一处就是「把 id 改个数字
>   读到别人的收货地址和电话」；
> - 订单上的收货地仍然是**下单当时的快照**（`u_s_order` 那几列），不 join 地址簿。
>   买家事后改地址，已经发出去的货不能跟着变。
>
> 结账页因此多了一个「从地址簿选一条」的下拉，但**没登录照样能填、能下单**——
> 那个下拉对匿名访客不出现，仅此而已。
>
> ⚠ 别和 §6a′ 搞混：**管理台仍然没有登录页、一个密码都不存**。
> 这里加的是<u>买家</u>（`u_s_buyer`）的密码，那边是<u>操作员</u>（`u_s_user`）。
> 两拨人、两张表、两套会话、两个 Cookie 名。

---

### 6d 把本地登录关掉：具体规则清单

> 2026-09-06 从 `station-console-sso.md` §4.3 搬过来的（那一份已删除，它描述的**票据回调协议**
> 早已被 §4.2 的自包含签名令牌取代，但这一节不是协议、是**现行的安全底线**，
> 而且有十来处代码注释按节号引用它——现在改指这里）。

**这一条比整套入场协议加起来都重要。** 入口做得再严，只要管理台还留着 `/login` 和账号密码，
别人就直接走那扇门。取舍见 §6a′ 那张表，代码入口是 `station-console` 的 `SessionController`。

- **删掉（或彻底关闭）用户名密码登录页和登录接口。**
  ⚠ **连带**：「重置密码」「改自己的密码」「新建本地账号」也一起删——
  前两个是在唯一入口旁边开门，第三个建出来的账号根本进不来（免密进入按 ERP 账号名找人）。
- **用户表可以留，但不存密码**：`u_s_user` 一列密码都没有，本站的用户按令牌里的 `user`
  匹配，第一次进来时自动落一行。
  （⚠ 别和买家搞混：`u_s_buyer` 上**有** `passwordhash`，那是买家的，见 §6c 的补记。）
- 除了 `/sso`、`/login`（那个空页面）和静态资源，**其余路径一律要求会话存在**。
- **`/login` 那一页什么都不放**：不放登录表单，也不放「请从众联 ERP 进入」——
  后者等于告诉扫到这个域名的人「后面有个后台，入口在另一个系统上」。
  唯一的例外是从 `/sso` 跳转被拒时带过来的一句原因（令牌过期 / 账号被停用），
  不然那两种失败都表现成白屏，没人知道该找谁。
- **每个接口都要重新校验。** 令牌只证明「签发那一刻，这个 ERP 用户对这个站有权限」，
  它**不**证明入场之后干的每一件事。

如果运维确实需要一个应急入口，那就单独做，并且加 IP 白名单 + 强制二次验证，
和这条正常链路完全分开——但先问问是不是真的需要。

> 没带会话访问管理台内页时**返回 401 是正确行为**，不是故障；
> 实际观察到的表现见 [独立站部署手册（Windows）.md](./独立站部署手册（Windows）.md) §7.1。

---

## 7. 独立站在 ERP 里的登记项

ERP 的「独立站管理」是一张**登记表**，不是一个后台入口。它只回答「我们有哪些站、
在哪、怎么进」，不含站里的任何业务数据。

### 7.1 字段

| 登记项 | 说明 |
| --- | --- |
| 站点编码 | ERP 侧主键；同一个值也存在站点库里，跳转令牌靠它对齐 |
| 站点名称 | 要点 7 明列 |
| **主域名** | `domain.com`。要点 7 明列 |
| **服务器 IP** | 要点 7 明列。运维找机器用，ERP 不会去连它 |
| 后端服务域名 | 默认 `data.<主域名>`，可覆盖 |
| 文件域名 | 默认 `file.<主域名>`，可覆盖 |
| 管理后台域名 | 默认 `console.<主域名>`，可覆盖。**跳转地址由它拼** |
| 买家账号域名 | 默认 `buyer.<主域名>`，可覆盖。它只放**买家中心那两页**（`/account` 我的资料、`/orders` 我的订单）；**登录 / 注册 / 找回密码 / 重置密码 / 邮箱验证都在主域**（用户 2026-09-08 确认「登入也放主域」，清单见 `station-site/shared/utils/buyerPaths.ts`）。⚠ 前台地址不是它，是主域 |
| 跳转密钥 | 一站一份，和站点库里那份一致。§4 的签名用它。**必须密文存**，见下 |
| 负责人 / 到期日 / 状态 / 备注 | 管理用 |

**跳转密钥的存法照抄现成的那一套**：`s_m_shop_storage.access_key_secret`（商家对象存储密钥）
已经用 `OSS_CONFIG_SECRET` 主密钥加密存了，实现是 `ruoyi-common` 的
`utils/sign/AesUtils`（`AES/GCM/NoPadding`）。跳转密钥直接用这个工具类，
**不要再发明第二种加密方式**——两套加密就有两个主密钥要轮换，
而第二套一定是那个没人记得怎么轮换的。

这一条和列名在已删除的 `station-platform-architecture.md` §13.1① 里定过，是那份文档里最后一段
仍然有效的内容，2026-09-06 随文档删除搬到这里，DDL 直接可用：

```sql
-- ⚠ 库名是 sdsshop，不是 sdsstation：这张表 2026-09-03 已经从独立站库搬回 ERP 侧
--    （见 §5.2、§10.2）。下面这段原来写的 sdsstation 是搬库前的残留，直接执行会失败。
ALTER TABLE sdsshop.u_s_site
  ADD COLUMN consolesecret varchar(500) NULL
      COMMENT '这个站的管理台专用密钥（密文）。一站一个，泄露只影响本站',
  ADD COLUMN serverip varchar(100) NULL
      COMMENT '这个站的境外服务器公网IP，逗号分隔。nginx 白名单和审计用';
```

- `consolesecret` **必须加密存**，照抄 `OSS_CONFIG_SECRET` 那一套（理由见上面一段）。
- `serverip` 不是给程序读的，是给运维回答「这条记录对应哪台机器」用的。

四个子域**按约定派生 + 允许覆盖**：约定能覆盖绝大多数站，
但「以后每个客户带自己的品牌域名」的场景里客户不一定照我们的约定命名，
所以每一个都留一列。

### 7.2 从 `u_s_site` 上摘掉的列

`u_s_site` 这张表**移到 `sdsshop`**（§5.2）的同时要瘦身。下面这些是站里的业务数据，
去处是站点库的 `u_s_siteinfo`：

> **⚠ 2026-09-18 整理时核实：下面这六列（`contentjson` / `langs` / `currency` /
> `sitekey` / `apienabled` / `adminuser`）现在**都还留在 `sdsshop.u_s_site` 上，一列都没删**。
> 这是**有意推迟**，不是漏做——`sql/migration/20260903_station_erp_split.sql:85-88` 写着
> 「老列一列都不删…清理等那批数据确认落地之后再单独做」。所以请看的人注意：
> 代码注释里凡是说「这一列跟着删了」的（`StationSiteMapper.xml`、`StationSite.java`、
> `OpenApiController.java` 等处），说的是**目标态**，与库里的现状不符。
> 真正要删的时候，先确认站点库那份数据已经落地。

| 列 | 去哪 | 说明 |
| --- | --- | --- |
| `contentjson` | → `u_s_siteinfo` | 首页装修，纯站点内容 |
| `langs` / `currency` | → `u_s_siteinfo` | 站点前台的渲染参数 |
| `status` | **两边各一份** | ERP 那份管「这个站还在不在服务、还发不发跳转令牌」；站点那份管「前台还开不开」。两个决定，不是一个值的副本 |
| `sitekey` | **删** | 站点前端拿它调 ERP 的 `/openapi/site/**` 限定数据范围用。现在前端取自己的后端、一个部署一个站，没有范围要限定 |
| `apienabled` | **删** | 同上，开放接口不再由 ERP 提供 |
| `adminuser` | **删** | 早就标了废弃，借这次一起清 |

**语言和币种为什么必须搬走、不能两边都留**：站点前台现在向自己的后端取数，
ERP 留一份就是两份会各自被改的配置，而且没有任何机制能发现它们不一致了。
`status` 是唯一有意保留两份的列——因为它们是两个不同的开关，谁也不是谁的副本。

`sitecode` / `sitename` / `shop_code` 留在 ERP，同时 `sitecode` / `sitename` 也存进
`u_s_siteinfo`：跳转令牌的 `site` 声明要靠它对齐（§4.3），
站点必须能独立说出自己是谁，不能靠问 ERP。

---

## 8. 灰色部分：订单拉取 / 生产进度同步

**一期不做，也不预埋接口。**

不预埋这一点值得说清楚：预埋一个「暂时没人调」的同步接口，等于把互通提前建好了，
下一个赶进度的人会很自然地用上它——那时候要点 1 就已经破了，而且没有任何编译错误提醒他。

一期的替代办法就是 docx 里那句：**像拼多多一样手工同步**。
独立站出了单，人工在 ERP 里录一张单。要点 4 明说这么做「便于理解独立性」——
它不是将就，是刻意的：手工这一步的存在，让「两边订单没有隶属关系」这件事在流程上就是显式的。

后期真要做，那时候要单独定协议（谁主动、频率、幂等键、失败怎么办、跨境网络抖动怎么办），
不在这一份的范围里。

---

## 9. buyeruser / buyershop 保留不删

要点 6：ERP 订单和客户里的这两列**保留不删，内容默认空串**。

**状态：这个动作已经做完了（2026-09-18 核实）**，两个库里都已是 `NOT NULL DEFAULT ''`：

| 表 | 列 | 现在 |
| --- | --- | --- |
| `u_o_order_*`（月表，`MonthlyTableInitializer` 建） | `buyershop` / `buyeruser` | `NOT NULL DEFAULT ''` |
| `u_m_buyer` | `buyershop` / `buyeruser` | `NOT NULL DEFAULT ''` |

落点：`MonthlyTableInitializer.java:788-789` 的建表 DDL 已改成 `NOT NULL DEFAULT ''`
（注释也换成了「默认空串，见要点 6」），所以下个月新建的月表不会又变回 `NULL`。
**列本身没动，读写代码也没动**——`SalesOrder` / `Buyer` 里的 `buyerShop` / `buyerUser`
两对 getter/setter 原样留着。

> 为什么留：这两列本来是「订单是哪个买家在哪个站下的」，现在两边订单不关联了，它没有来源。
> 但删列是不可逆的，而且后期做订单拉取时它正好是落 ID 的地方。留着、空着，成本是零。

---

## 10. 一期迁移清单（逐文件）

### 10.1 ERP 侧删除

**后端** `RuoYi-Vue/ruoyi-admin/.../web/controller/station/`：

| 文件 | 行数 | 为什么删 |
| --- | --- | --- |
| `StationConsoleApiController.java` | 509 | 管理台的 S2S 数据接口，整条互通 |
| `StationOpenController.java` | 112 | 站点前台取数接口（`/openapi/site/**`），搬到站点后端 |
| `StationSiteGoodsController.java` | 128 | 商品上架，搬走 |
| `StationInquiryController.java` | 89 | 询盘，搬走 |
| `StationFileController.java` | 192 | 文件资源，搬走 |

**后端** `RuoYi-Vue/ruoyi-system/`：

- domain：`StationSiteGoods`、`StationSiteGoodsLang`、`StationInquiry`、`StationImage`、
  `StationVideo`、`ConsoleCatalogGoods`、`StationConsoleContext`、
  `StationConsoleEntry`、`StationConsoleRedeemRequest`
- service：`IStationSiteGoodsService` / `Impl`、`IStationInquiryService` / `Impl`、
  `StationInquiryNotifier`、`IStationFileService` / `Impl`、`IStationOpenService` / `Impl`
- mapper + XML：`StationSiteGoodsMapper`(237 行 XML)、`StationInquiryMapper`(92)、
  `StationImageMapper`(98)、`StationVideoMapper`(96)

**两组不能跟着删的**（都在 `mapper/station/` 目录下，看着像独立站的东西）：

> **`ShopStorage` / `IShopStorageService` / `ShopStorageServiceImpl` / `ShopStorageMapper`
> (+105 行 XML) 留着。** 它对的表是 `sdssys.s_m_shop_storage`，**不在 `sdsstation`**，
> 而且 ERP 自己的文件上传也在用（`ruoyi-common` 的 `OssStorageService` / `OssProperties`）。
> 删它会连带打断 ERP 的图片上传。要删的只是 `StationConsoleApiController` /
> `StationFileController` 里把它暴露给管理台的那几个端点。

> **`Buyer` / `Buyer2` / `IBuyerService` 不动**——那是 ERP 自己的销售客户（`u_m_buyer`）。
> 它和独立站唯一的牵连是 `StationInquiryServiceImpl` 里「询盘转客户」那个动作，
> 那个文件本身要删，牵连自然断开。

**前端** `RuoYi-Vue3/`：

- `src/views/site/console/`（`index.vue` / `overview.vue` / `decorate.vue`）— ERP 内置管理台
- `src/views/site/goods/`、`src/views/site/inquiry/`、`src/views/site/file/`
- `src/views/site/components/MediaPicker.vue`
- `src/api/site/goods.js`、`site/inquiry.js`、`site/file.js`
- `src/router/index.js` 里 `/station-console/:siteCode` 那整块路由（约 120–157 行）

**菜单 SQL**（`sql/01_schema_and_init.sql`）：删 `fn9010`（商品上架）、`fn9020`（询盘管理）、
`fn9030`（文件资源）三棵子树；`fn9001` 从「站点控制台」改名「独立站管理」。

**建库脚本**（`sql/01_schema_and_init.sql`）：删掉 `CREATE DATABASE sdsstation` 那一段
（1626 行起）连同它下面除 `u_s_site` 以外的全部建表语句；`u_s_site` 挪进 `sdsshop` 那一节。
脚本跑完之后，ERP 侧应当**建不出 `sdsstation` 这个库**——这是「拆干净了」最省事的一个判据。

**站点后端** `station-console/`：

- `erp/ErpClient.java`(240)、`ErpException`、`ErpResult`、`config/ErpProperties`(76) — 整个 S2S 客户端
- `pom.xml` 里那段「不直连数据库、读写全走 S2S」的注释要重写，它记录的是已经作废的前提

### 10.2 ERP 侧改造

| 文件 | 改成什么 |
| --- | --- |
| `StationSiteController.java` | 只剩登记的增删改查 + 发跳转令牌；删掉 `/content`、`/enter` 这些 |
| `StationSite.java` / `u_s_site` | 按 §7 加列（服务器 IP、四个子域、跳转密钥）、按 §7.2 摘列 |
| `StationSiteMapper.xml` | **9 处 `sdsstation.u_s_site` 全部改成 `sdsshop.u_s_site`**（§5.2）。这是搬库那一步在 ERP 侧唯一的代码改动，漏一处就是运行期 `Unknown database 'sdsstation'`。另外 2 处在 `StationInquiryMapper.xml` 的 `left join` 里，那个文件整体删掉，不用改 |
| `StationConsoleSsoController` / `StationConsoleSsoServiceImpl` | 改成签令牌；删 `redeem`、删回退分支、删全局 `base-url` |
| `StationConsoleProperties` / `StationConsoleTicket` | 令牌没有服务端状态，只留 `jti` 不需要存 → 大部分可删 |
| `MonthlyTableInitializer.java` | `buyershop` / `buyeruser` 默认值 `NULL` → `''`（§9） |
| `views/site/station/index.vue` / `api/site/station.js` | 登记表单加新字段；「进入管理」改成 POST 表单提交 |

### 10.3 独立站侧新建

`station-console` 从「无状态代理」变成「一个完整的后端」：

1. **pom**：加 `mybatis-spring-boot-starter` + `mysql-connector-j`；Redis 保留（会话 + `jti`）。
   数据源指本机 `sdsstation`，**只配这一个库**。
2. **建库脚本** `sql/station/01_station_schema.sql`：站点服务器上那个 `sdsstation`。
   搬过来的表（结构照抄，不改名）——
   `u_s_sitegoods` + `u_s_sitegoods_lang`（要加商品档案列，§5.4）、
   `u_s_siteimage` / `u_s_sitevideo`、`u_s_inquiry`；
   新建的表——`u_s_siteinfo`（本站档案，§7.2）、`u_s_user`（操作用户，§6）、
   `u_s_storage`（本站存储配置，替代 `sdssys.s_m_shop_storage`）。
3. **domain / mapper / service**：从 ERP 那批文件移植，去掉 RuoYi 基类依赖，
   **mapper XML 里的库限定名一律去掉**（`sdsstation.u_s_sitegoods` → `u_s_sitegoods`）。
   `shop_code` 列和字段照留，按 §5.3 降级为遗留列。
4. **`SsoController`**：改成 §4 的验签流程，只接受 POST。
5. **`u_s_user` + `SessionController`**：站点自己的用户表（§6）。⚠ 不含登录页——
   §6a 那版账号密码登录撤掉了（§6a′），这一步现在只剩「用户表 + 退出接口」。
6. **`ConsoleApiController`**：40 个转发接口逐个改成调本地 service。**对外的 URL 路径不变**，
   `station-console-ui` 不用跟着改。
7. **`OpenApiController`**：`/openapi/site/{info,products,products/{code},inquiry}`，
   从 ERP 的 `StationOpenController` 移植过来。
8. **文件存储**：落本地磁盘，由 `file.domain.com` 直接对外。

`station-site`（Nuxt）：**代码基本不用改**，把 `NUXT_API_BASE` 从 ERP 的
`127.0.0.1:8080` 指到站点后端（本机 `127.0.0.1:8081` / 对外 `data.domain.com`）即可。
`NUXT_PUBLIC_SITE_KEY` 可以去掉——一个部署一个站，站点身份由后端自己知道。

### 10.4 顺序

```
① 定稿这份文档                                        ← 现在
② 站点服务器上装 MySQL，建 sdsstation + 建表脚本
   station-console 接上数据源（只连这一个库）
③ 移植 domain/mapper/service（去库限定名），
   ConsoleApiController 40 个接口改成调本地
④ 移植 OpenApiController，station-site 换 API 地址
⑤ 站点自己的用户表 u_s_user（不含登录页，见 §6a′）
⑥ 新跳转协议：ERP 发令牌 / 站点验签       ← 两边同时改，这一步不能拆
⑦ 导数据 mysqldump --ignore-table=…u_s_site → 站点服务器 restore，验一遍
⑧ ERP 侧收尾，顺序不能反：
     a. u_s_site 搬进 sdsshop（建表 + INSERT SELECT）
     b. StationSiteMapper.xml 9 处改 sdsshop.，重启，验独立站管理页
     c. 删那批文件、路由、菜单
     d. mysqldump 全量备份，然后 DROP DATABASE sdsstation
```

**②～⑤ 之间 ERP 侧一行不动**——前五步做完，老的那套还照常能跑，
出问题随时能停下来。真正的切换点是⑥和⑧。

⑧ 里 a、b 两步之间 ERP 是**跑不通独立站管理页**的（表已经在新库、SQL 还指旧库），
所以这两步要连着做，别隔一天。⑧d 那份备份是这批数据在 ERP 侧的最后一份，
`u_s_sitegoods` 里那些人工录了几个月的商品文案没有别的地方有。

---

## 11. 一期不做、但要留位置的

> **⚠ 2026-09-18 整理时核实：下面前五个模块后来全都做完了**，只有「买家后台」还是一格空的。
> 这条原始依据是 `station-console-ui/src/router/index.js` 顶部注释（它自己就写着
> 「§11 原本列了五个，现在五个都做完了」）。以下表格保留当年的记录，看状态请以这句为准。

「留位置」= **菜单项 + 路由 + 一个说明用途的空页面**。不建表、不写 model、不留 TODO 接口。

| 模块 | 留什么 | 后来 |
| --- | --- | --- |
| 博客管理 | 菜单 + 路由 + 空页 | ✅ 2026-09-08 做完（`u_s_post`） |
| 订单管理（站点） | 同上 | ✅ 2026-09 做完（`u_s_order` / `u_s_orderitem`） |
| 客户管理（站点） | 同上 | ✅ 2026-09 做完（`u_s_buyer`） |
| 营销管理 | 同上 | ✅ 2026-09-09 做完，做的是**询盘跟进看板**（`u_s_followup`） |
| 站长工具 | 同上。`sitemap.xml` / `robots.txt` 已经在 `station-site/server/routes/` 里有了，属于这个模块，先不动 | ✅ 2026-09-08 做完（`u_s_seo`） |
| 买家后台 | 只登记 `buyer.domain.com`。⚠ 那个子域本身**已经在用了**（买家中心两页在上面，见 §6c），空着的是「一套独立的买家自助系统」这个模块 | ⬜ 仍是空的，**这一格还成立** |

为什么不顺手建表：表结构定下来就会有人往上写代码，而这五个模块的需求现在只有名字。
空表比没表更容易引出错的实现。

---

## 12. 这一份让哪些旧结论作废

| 文档 | 作废的结论 | 现在是 |
| --- | --- | --- |
| ~~station-platform-architecture.md~~（2026-09-06 已删除，§2/§3/§4/§10 并入 [独立站产品化设想（未落地）.md](./独立站产品化设想（未落地）.md) §八bis，§13.1① 搬进本份 §7.1）§0 决定 4 | 「ERP 是订单和库存的唯一真相」 | 只对 ERP 自己的订单成立；独立站订单与它无关（要点 4） |
| 同上 §0 决定 5 | 「独立站本地库只存副本，订单库存不在本地做主」 | 独立站库是自己数据的正本，没有副本这回事（§5） |
| 同上 §0 决定 6 | 「管理台只能从 ERP 点进来，永远不需要登录页」 | **结论仍然成立**，只是理由和实现变了：管理台有自己的用户表（§6），但没有登录页——§6a 加过一版，撤掉了（§6a′） |
| 同上 §5 | 「那个必须堵死的接口现在必须开」 | 那个接口整个删掉（§4.5） |
| 同上 §6 | 数据分界：什么存本地、什么回 ERP | 没有「回 ERP」这一类（§5.1） |
| 同上 §8 | 买家账号不要绑 `sys_user` | 结论仍然对，但理由变了：买家属于站点，和 ERP 没关系（§6c） |
| ~~station-console-sso.md~~（已删除，§4.3 搬成了本份 §6d）§2 第③步、§3.2 | 服务端兑换 `POST /openapi/console/redeem` | 删掉，改自包含签名令牌（§4.2） |
| 同上 §1 | 「不要把身份放 URL 上」 | 结论保留、办法升级：令牌走 POST 表单，仍然不进 URL（§4.2） |
| ~~station-console-api.md~~（已删除） | 整份是 S2S 代理的接口契约 | URL 路径保留，但实现从「转发 ERP」变成「查本地库」（§10.3.6） |
| ~~kangchen-now-what.md~~（已删除）§2、§5 | `STATION_CONSOLE_BASE_URL` 全局配 + 回退 ERP 内置页 | 两者都删；地址一站一份（§4.5、§7） |
| ~~kangchen-domain-plan.md~~（已删除，§14/§15 搬进了部署手册附录 D） | 只规划了 `console.` / `www.` / `file.` | 补 `data.` 和 `buyer.`（要点 7） |
| `station-console/pom.xml` 顶部注释 | 「不直连数据库，读写全走 S2S，依赖列表里没有 ERP 的包就是那道闸」 | 前提反了：现在的闸是「依赖列表里没有任何 HTTP 客户端指向 ERP」 |
| `sql/01_schema_and_init.sql` | `sdsstation` 建在 ERP 那个实例上，和三个库同库跨查 | 该库整个搬到站点服务器；`u_s_site` 进 `sdsshop`（§5.2） |
| ~~kangchen-deploy-runbook.md~~（已删除，被 独立站部署手册（Windows）.md 取代） | 站点服务器只装 Node + Java + Redis，数据回 ERP 取 | 还要装 **MySQL**，并建本站的 `sdsstation`（§10.3.2）；反过来，3306 一律不对外（§13 第 6 条） |

**这些文档一期不逐份重写**，但每一份顶部要加一行指回这里。

`station-platform-architecture.md` 曾经是例外——它的 §5、§6 是**主动指导别人去建互通**的，
不能只加一行指引。**2026-09-06 的处理是整份删除**：那两节连同 §13 一起丢掉，还有效的四节
（为什么一站一服务器、放哪、每台装什么、N 台机器的代价）并进了
[独立站产品化设想（未落地）.md](./独立站产品化设想（未落地）.md) §八bis。

同一天 `独立站产品化设想（未落地）.md` 也补了标注：它的 §七、§八（推订单 / 拉进度）写的是
**二期目标态**，一期按本份 §8「不做也不预埋」。那两节没删，因为二期真要做时它是起点，
但顶部现在写明了「不是现在的施工图」。

---

## 13. 验收：怎么证明「完全分离」真的成立

一期完工的判据不是「功能都能用」，是下面这七条同时成立：

1. **把 ERP 整个停掉**，独立站前台照常卖货；管理台里**已经登进来的人**照常改商品、
   看询盘（会话在本站 Redis 上，8 小时不活跃才失效）。
   用不了的只有「从 ERP 点进入管理」这一个动作——也就是**新进入**。
   > ⚠ 这一条以前写的是「管理台、登录……全部照常」，那是 §6a 有登录页的时候。
   > 现在没有登录页（§6a′），入场令牌由 ERP 签，ERP 不在就签不出来。
   > 这不影响「两边后端没有任何互通」：验签是本站纯本地计算，从不回调 ERP。
2. `station-console` 的依赖树里搜不到任何指向 ERP 的地址或 HTTP 客户端：
   ```
   grep -rn "openapi/console\|ErpClient\|erp\.base-url" station-console/src
   ```
   零结果。
3. ERP 的代码里搜不到任何站点业务表，也搜不到那个库名：
   ```
   grep -rn "u_s_sitegoods\|u_s_inquiry\|u_s_siteimage\|u_s_sitevideo" RuoYi-Vue/
   grep -rn "sdsstation" RuoYi-Vue/
   grep -rn "sdsstation" sql/ --exclude-dir=station
   ```
   三条都零结果——`u_s_site` 现在写成 `sdsshop.u_s_site`，不带旧库名。
   > ⚠ **第二条原来的写法是 `grep -rn "sdsstation" RuoYi-Vue/ sql/`，那条现在永远不可能通过。**
   > 因为 `sql/station/` 下就是我们自己要求新建的站点建库脚本（§10.3.2），它当然会提到
   > `sdsstation`（`sql/` 里共 100 多处）。所以 `sql/` 那一半必须排除 `sql/station/`。
   > ERP 侧（`RuoYi-Vue/`）那一条仍然成立，它才是这条验收真正要盯的。
4. **两个站互不可见**：拿 A 站的跳转令牌去 POST B 站的 `/sso`，签名验不过（密钥不同），
   即便同一个 ERP 用户也进不去。
5. ERP 侧不存在任何**主动连独立站**的代码——没有定时任务、没有 HTTP 客户端、
   没有配置项写着某个站的地址（`domain` / `serverIp` 是给人看的登记信息，
   不能出现在任何 `RestTemplate` / `WebClient` 的调用里）。
6. **两边的数据库在网络上互不可达**，这一条要真的去连一次验：
   ```
   # 在独立站服务器上（应当连不上，超时或拒绝）
   mysql -h <ERP服务器IP> -P 3306 -u root -p
   # 在 ERP 服务器上（同样应当连不上）
   mysql -h <站点服务器IP> -P 3306 -u root -p
   ```
   站点那台的 3306 只监听 `127.0.0.1`，安全组里不开 3306。
   这一条是 §5.1 那句「跨服务器查询不存在」的**唯一**执行手段——
   靠「大家都别写跨库 SQL」的约定守不住，靠防火墙才守得住。
7. **ERP 服务器上 `SHOW DATABASES` 里没有 `sdsstation`**；
   站点服务器上有，且里面**没有** `u_s_site`。

第 1、5、6 条是这一份的全部意义所在——前两条管代码，第 6 条管「代码之外有人手工连过去」
这种绕过。功能回归照旧跑
[独立站部署手册（Windows）.md](./独立站部署手册（Windows）.md) §9 的验收清单。
