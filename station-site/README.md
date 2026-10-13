# station-site —— 独立站前台

海外买家看到的那个网站。数据来自同机部署的独立站后端 `station-console` 的
`/openapi/site/**`，前台自己没有数据库，也不直接访问 ERP。

配套文档：[部署手册](../docs/独立站部署手册（Windows）.md)、[ERP 与独立站的边界](../docs/独立站与ERP的边界.md)

---

## 三个决定，以及为什么

### 1. SSR，不是 SPA，也不是静态预生成

外贸独立站的流量靠 Google 收录。三种形态的取舍：

| | SEO | 内容改完多久生效 | 部署 |
|---|---|---|---|
| SPA | ❌ 爬虫拿到空壳 | 立刻 | 丢个 dist |
| 静态预生成 | ✅ | ❌ 要重新构建 | 丢个 dist |
| **SSR（选它）** | ✅ | ✅ 立刻 | 多一个 Node 进程 |

排除 SPA 是因为商品页不被收录等于把这个站存在的理由去掉一半；排除静态预生成是因为
装修页上明写着「保存后刷新站点立刻生效，没有『发布』这一步」，预生成和这句话直接冲突。

代价就是服务器上多一个要盯的进程，见下面「部署」。

### 2. 一站多语走路径前缀 `/ja/products`，不是 `?lang=ja`

查询参数会被 Google 当成同一个页面的变体，多半只收录一种语言——那多语言就白做了。
路径前缀配 `hreflang` 才能让日语页和中文页被当成两个页面分别收录、分别推给对应地区的人。

**默认语言不带前缀**：`kangchen.com/` 就是默认语言，不 302 到 `/zh/`。根地址权重最高，
让它直接承载内容比让它跳转好。

没有用 `@nuxtjs/i18n`：那个模块要求构建时就知道有哪些语言，而这里的语言是每个站自己在
站点后端配置的，运行时才从 `/openapi/site/info` 拿到。前缀路由在 `nuxt.config.ts` 的
`pages:extend` 里自己加了一层。

### 3. 一站一部署

后端没有「按域名查站点」的公开接口（`selectByDomain` 只在内部做唯一性校验），所以不做
单实例多租户。每套部署只连接本站自己的 `sdsstation`，站点身份由 `station-console`
从 `u_s_siteinfo` 自己确定，前台不再配置或传递 `siteKey`。

这也正好对应以后的形态：每个客户带自己的品牌域名，就是各自一套部署。

---

## 本地开发

```bash
cd station-site
npm install

# station-console 默认监听 8081；本地开发时先启动它、MySQL 和 Redis
NUXT_API_BASE=http://127.0.0.1:8081 npm run dev
```

打开 http://localhost:3000 。本站后端 `station-console`（8081）、MySQL 和 Redis 要先起着，
否则页面会报「站点暂时不可用」。

Windows PowerShell 里设环境变量：

```powershell
$env:NUXT_API_BASE="http://127.0.0.1:8081"; npm run dev
```

### 环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `NUXT_API_BASE` | | **服务端**取数地址，默认 `http://127.0.0.1:8081`，指向本站 `station-console` |
| `NUXT_PUBLIC_API_BASE` | | **浏览器**取数地址。留空 = 相对路径，由 nginx 转发（生产推荐这样，省一次跨域） |
| `NUXT_PUBLIC_SITE_URL` | | 站点对外地址，**填主域**，如 `https://tongling.vclinker.com`。用来拼 canonical / hreflang / sitemap 的绝对 URL。留空则从请求头推 |
| `NUXT_PUBLIC_BUYER_URL` | | 账号页那个 host，如 `https://buyer.tongling.vclinker.com`。留空 = 不分 host（本地开发就该留空）。见部署那一节「前台是两个 host」 |

> 前台没有 `siteKey` 或 ERP 密钥配置。管理台跳转使用的签名密钥由 ERP 和本站
> `u_s_siteinfo.consolesecret` 配置，前台不参与这条链路。

---

## 部署

### 1. 构建

```bash
npm run build          # 产物在 .output/
```

⚠ **产物是 `.output/` 不是 `dist/`**。SSR 的产物包含一个 Node 服务器，不是一堆静态文件，
所以 nginx 是**反代给它**（`proxy_pass http://127.0.0.1:3000`），不是 `root` 发文件。
配置见 [`deploy/nginx/station.windows.conf.template`](../deploy/nginx/station.windows.conf.template) §2。

⚠ **`.output/` 和 `.nuxt/` 都不进版本库**（2026-09-06 移出去的）。跑一次
`npm run build` 会动两百多个文件，跟踪着的话真正的源码改动会被埋在里面看不见。
上服务器是把 `.output/` 打包传上去，见
[docs/独立站部署手册（Windows）.md](../docs/独立站部署手册（Windows）.md) §5.2
（服务器只有 4 GB 内存，跑不动 `nuxt build`，**必须本机构建**）。

### 2. 传到服务器并注册成服务

把 `.output/` 和 `deploy/` 下的两个文件传到 `C:\wczlerp\station-site\`：

```
C:\wczlerp\station-site\
├── .output\              ← 构建产物
├── start-site.bat        ← 只用于临时验证
└── install-service.ps1
```

> `NUXT_PUBLIC_SITE_KEY` 那一项**已经没有了**：一个部署一个站，前台打的是同一台机器上
> 的站点后端，那个后端只连本站的库，没有范围要限定（boundary §7.2）。

#### ⚠ 前台是两个 host（代码已就绪，**统领站还没切**）

> ⛔ 2026-09-08：`tongling.vclinker.com` 还没备案、也没有证书，线上两个站现在都是
> **单 host**（前台整站在 `buyer.` 上）。下面写的是目标状态。
>
> 代码是**向下兼容**的：`NUXT_PUBLIC_BUYER_URL` 留空就退化成全相对路径，
> 和改造之前一模一样，所以这份代码现在直接发版是安全的。
> 上线清单见 [`deploy/nginx/tongling.conf`](../deploy/nginx/tongling.conf) 文件头。

```
<主域名>        公开页：首页、商品、购物车、我的询价 —— 买家搜到、分享出去的那个地址
buyer.<主域名>  账号页：登录 / 注册 / 找回密码 / 重置密码 / 邮箱验证 / 账户
```

**同一个 Node 进程**，分的是 host 不是应用；真正的分流在 nginx 上
（`deploy/nginx/tongling.conf` 里两条正则 `location`）。对应两个环境变量：

| 变量 | 填什么 |
| --- | --- |
| `NUXT_PUBLIC_SITE_URL` | **主域**。canonical / hreflang / sitemap 的绝对地址由它拼，收录权重要收拢到一个 host 上 |
| `NUXT_PUBLIC_BUYER_URL` | `https://buyer.<主域名>`。只影响**站内链接指哪**（顶栏那个 Sign in 直接指过去，不用白挨一次 302）。留空 = 不分 host，全用相对路径，本地开发就该留空 |

分界线是**要不要登录**，不是「像不像买家自己的东西」：

- **购物车和「我的询价」留在主域。** 数据在 `localStorage` 里、按源隔离——挪到 `buyer.`
  上，买家在主域商品页加的货那边打开就是空的，而且页面不报错，只是「东西没了」。
  这两页本来也不要求登录（匿名下单是刻意的，见 `cart/index.vue` 第 1 条）。
- `/verify-email`、`/reset-password` 在 `buyer.` 上，但**信里的链接拼的是主域**
  （后端读 `u_s_siteinfo.domain`），靠 nginx 那条 302 转过去、query 一起带走。
  所以后端不需要知道 `buyer.` 这个域名的存在。

名单和跨 host 链接的实现在 [`app/composables/useSiteHosts.ts`](app/composables/useSiteHosts.ts)
——**改那份名单要同时改 nginx 里那两条正则**，只改一边的表现很难查。

⚠ 还有第三个必须一起配的东西，在**后端**：`STATION_COOKIE_DOMAIN` 要填两个 host 的
公共父域（也就是主域名）。登录发生在 `buyer.` 上，而顶栏判断有没有登录、
按身份拉订单、把单挂到买家名下都在主域上——漏了它的表现是
「登录成功了，回到主域还是显示 Sign in」，不报任何错。

⚠ 康辰那台（Windows）现在还是**单 host 布局**：前台整站在 `buyer.` 上，主域名在
nginx 里没有 server 块也没有证书。要迁过去的话四件事一起改，
见 `deploy/nginx/station.windows.conf.template` 文件头那一段。

**临时验证**用 `start-site.bat`（双击即可）。**生产必须注册成服务** —— 双击起的进程挂在
那个登录会话上，关掉远程桌面就没了，服务器重启也不会自己起来：

```powershell
# 管理员 PowerShell
.\install-service.ps1 -Target site
nssm edit wczl-station-site        # 核对 NUXT_PUBLIC_SITE_URL / NUXT_PUBLIC_BUYER_URL（见上面「前台是两个 host」）
Start-Service wczl-station-site
```

脚本顺带配好了：开机自启、崩溃自动拉起（退避 30 秒，避免起来就崩时刷爆日志）、
stdout/stderr 落盘并按 10MB 轮转。

> 管理台后端（8081）用同一个脚本：`.\install-service.ps1 -Target console`。
> 两个目标的服务名、环境变量差异全在脚本里那张表上。

默认监听 3000。**3000 绝不放行到公网**，安全组只开 80/443，和 8081 一个道理。

#### 为什么是 nssm 不是 Docker

服务器是 Windows，上面是 nginx + Java，没有容器运行时。为一个 Node 进程装 Docker Desktop
是把运维复杂度整个抬一级（镜像、数据卷、Hyper-V 还是 WSL2 后端）。nssm 是单个 exe，
做的正好是「把命令行进程包成 Windows 服务」这一件事，和现有环境一路。

以后真要上容器，那是 `station-console` + `station-console-ui` + `station-site` 整套一起迁的事，
不应把前台单独改造成依赖 ERP 的部署。

### 3. 改 nginx

用 [`deploy/nginx/station.windows.conf.template`](../deploy/nginx/station.windows.conf.template)
（Windows）或 [`deploy/nginx/tongling.conf`](../deploy/nginx/tongling.conf)（Linux）。
Windows 那份放到 `C:\nginx\conf\conf.d\station.conf`，把 `DOMAIN` 全文替换成主域名即可。

⚠ 线上 `nginx.conf` 有 `stream {}` 和 `http {}` 两个顶层块，`include conf.d/*.conf`
要在 `http {}` 里；粘进 `stream {}` 会报 `"server" directive is not allowed here`。

> 这里原来指的是 `deploy/nginx.kangchen.conf`，那份**已经删了**：它把
> `/openapi/site/` 反代到 `127.0.0.1:8080`，而 8080 是 ERP 的端口；当前应反代到本站
> `station-console` 的 8081，前台不该认识 ERP
> （boundary 要点 1）。同批删掉的还有 `station-console/deploy/` 下那几份。

### 4. 验收

**最关键的一条**：

```powershell
curl -s https://buyer.kangchen.vclinker.com/ | findstr /C:"<title>"
```

必须看到真实标题。如果返回的 HTML 里只有一个空的 `<div id="__nuxt">`，说明退化成客户端
渲染了，SEO 等于白做——这是 SSR 唯一必须盯住的东西。

其余验收项见 [docs/独立站部署手册（Windows）.md](../docs/独立站部署手册（Windows）.md) §9。

---

## 结构

```
app/
├── app.vue                     壳
├── error.vue                   404 / 503
├── assets/site.css             全部样式（没引 UI 框架，见下）
├── composables/
│   ├── useSite.ts              四个后端接口 + 语言解析 + 路径前缀
│   ├── useSeo.ts               title / description / canonical / hreflang / og
│   ├── useShopCopy.ts          询价流程的六语文案表
│   ├── useCart.ts              询价清单（localStorage）
│   └── useOrders.ts            询价单台账（localStorage）
├── lib/
│   ├── shop.ts                 询价单模型、状态词表、金额与日期格式化
│   └── product3d/              3D 展示台的取景计算 + 替身模型 + 视角预设
├── components/
│   ├── SiteHeader.vue          Logo、导航、语言切换、询价清单角标
│   ├── SiteFooter.vue          联系方式
│   ├── ProductCard.vue         商品卡片（带「加入清单」）
│   ├── InquiryForm.vue         询盘表单（含蜜罐）
│   ├── GlobalReach.vue         首页世界地图（d3-geo，SSR 出静态 SVG）
│   ├── OrderStatusPill.vue     询价单状态标签
│   └── Product3DViewer.client.vue   WebGL 展示台（three.js，进视口才加载）
└── pages/
    ├── index.vue               首页：Banner / 特色 / 能力 / 主推商品 / 关于 / 地图 / 询盘
    ├── products/
    │   ├── index.vue           列表：搜索、分类、分页
    │   └── [code].vue          详情：图库、规格、富文本、结构化数据、3D、加入清单、询盘
    ├── cart/index.vue          询价清单
    ├── checkout/index.vue      填联系方式和目的地 → 发询盘
    ├── orders/index.vue        我的询价（本机记录）
    ├── orders/[id].vue         询价单详情：进度、明细、存一份、再询一次
    └── 3d-demo.vue             3D 展示台沙盒（不依赖商品数据）
server/routes/
├── robots.txt.ts               顺带 Disallow 掉 cart / checkout / orders
├── sitemap.xml.ts              全部商品 × 全部语言，带 hreflang
└── openapi/site/[...path].ts   把浏览器侧的取数转给本站 station-console（本地开发没有 nginx 时靠它）
```

**没有引 UI 框架**：这是个内容型站点，组件就这么几个，多一个框架等于给每个海外访客多下
几百 KB——外贸站的访客网络条件差异很大，首屏时间直接影响跳出率。

### 界面文案 vs 装修内容

| | 谁维护 | 在哪 |
|---|---|---|
| 「发送询盘」「上一页」「最小起订量」这类**功能性文字** | 开发 | 各组件里的 `COPY` 表，按语言 |
| 公司名、标语、Banner、关于我们、导航、联系方式 | 商家 | ERP 管理台 → 首页装修 |

分开是因为让商家去翻译「提交」两个字没有意义。`COPY` 表目前有
**en / zh / ja / es / pt / ru / it / ko 八种**，站点语言不在表里就退回英文（外贸站的默认受众）。

表分散在七个地方，加语言要**七处一起补**，漏一处那一页就会在那种语言下变回英文：

| 文件 | 表 | 管什么 |
|---|---|---|
| `app/composables/useShopCopy.ts` | `EN`/`ZH`/… 八个块，各 140 个键 | 询价清单、结账、我的询价、3D 展示台 |
| `app/pages/index.vue` | `COPY` | 首页几个小标题 |
| `app/pages/products/index.vue` | `COPY` | 列表页搜索、分页 |
| `app/pages/products/[code].vue` | `COPY` | 详情页面包屑、规格标签 |
| `app/components/InquiryForm.vue` | `COPY` | 询盘表单 |
| `app/components/GlobalReach.vue` | `COPY` | 世界地图标题、图例 |
| `app/components/ProductCard.vue` | `FEATURED` | 卡片上的「主推」角标 |

另外 `app/components/SiteHeader.vue` 的 `NAMES` 是**语言切换器上的语言名**（用各语言自己的
写法），那张表已经覆盖 18 种，一般不用动。

补的时候两件事容易错，都会静默出问题：

- **占位符要跟着抄**。`'{n} 件产品'` 里的 `{n}`、`'咨询「{name}」'` 里的 `{name}` 少写一个，
  页面上就是一句没有数字/没有商品名的话，不报错。
- **`useShopCopy.ts` 里 `d3Title` 在日语和韩语下是空串，那是故意的**，不是漏翻，
  理由写在那个键上面的注释里。

---

## 从 kangwanjia-website-EN 搬过来的四块

`kangwanjia-website-EN` 是一个纯前端的品牌站（Nuxt 3 + `@nuxtjs/i18n`，商品和订单
都是写死的假数据）。这个站从它那里搬了四样东西，**只搬视觉和交互，不搬假数据**。

| 搬了什么 | 落在哪 | 主要改动 |
|---|---|---|
| 视觉主题（紫色调、Fraunces 标题、胶囊按钮、大圆角） | `app/assets/site.css` | 旧变量名 `--brand` / `--line` / `--ink` 保留成别名，不改每个 scoped style |
| 世界地图 | `components/GlobalReach.vue` | 地点和数字变 props，文案跟站点语言走，投影中心跟着总部走 |
| 3D 展示台 | `components/Product3DViewer.client.vue` + `lib/product3d/` | 状态文案变 props；商品有 `.glb` 才渲染；进视口才下 three.js |
| 购物流程 | `pages/cart` `checkout` `orders` + `composables/useCart|useOrders` | 见下面三条 |

### 购物流程的三条改动，每一条都别改回去

**1. 结账不是支付，是发询价单。** 上游那一步是假的：选支付宝/微信、画一个装饰性的
二维码、转 1.5 秒圈、订单变「已付款」。这个站会真上线、真有海外买家进来，
一个看着能付款其实什么都没发生的页面就是骗人。

所以第三步改成收「运输和付款倾向」，提交走**现有的询盘接口**
（`POST /openapi/site/inquiry`，明细拼进 `message`），商家收到带明细的邮件，人工回报价。
后端**一行没改**，也没有新增任何接口——独立站的订单模块按
[边界文档 §3.2](../docs/独立站与ERP的边界.md) 是一期不做的。

**2. 没有登录门。** 上游要求登录才能结账。这个站没有买家账号
（[边界文档 §6c](../docs/独立站与ERP的边界.md)：买家中心一期不实现），
加一道门等于把流程堵死。

**3. 清单和台账都在 localStorage，页面上明说了。** 「我的询价」是这台浏览器上发过的
记录，不是账号历史。上游把这页做得像账号中心，买家换台电脑发现全没了会更恼火。

### 后端有了订单接口之后要改什么

要动的是 `composables/useOrders.ts` 的读写（`record` 改成 POST、`sorted` 改成按买家 GET）。
`lib/shop.ts` 里的模型、状态词表、时间轴照用——`confirmed` / `shipped` / `delivered`
这三个本地永远出现不了的状态就是给那时候留的。页面除了去掉「只存在本浏览器」那句提示，
基本不用动。

### 三个刻意没搬的东西

- **`user-select: none`**。上游给整个 body 禁了选中（防抄图）。外贸站的买家最常做的两件事
  就是复制商品编码和复制邮箱，禁掉等于自伤。
- **购物车预置示例商品 / 订单预置示例历史**。演示站可以，真站上是替买家做决定和伪造记录。
- **替身 3D 模型当默认**。`lib/product3d/placeholderModel.ts` 里那台按摩仪只在 `/3d-demo`
  出现。给一个阀门商品配一台按摩仪的模型，比不显示 3D 糟得多。

---

## 两个不能动的地方

**商品详情的 `v-html`**（`pages/products/[code].vue`）。那段 HTML 是商家在管理台录的，
写入时经过服务层的 `RichTextCleaner` 白名单过滤（script / iframe / 事件属性 /
`javascript:` 全剥掉）。**渲染这里是安全的，前提是那一层过滤还在**——
把服务层的过滤去掉，这里立刻变成一个存储型 XSS 出口。

**询盘表单里的隐藏 `website` 字段**（`components/InquiryForm.vue`）。那是蜜罐，
后端看到它非空就当机器人、装作成功但不入库。看着像多余字段，别删。
用 CSS 挪出视口而不是 `display:none`，是因为稍好一点的机器人会跳过后者。
