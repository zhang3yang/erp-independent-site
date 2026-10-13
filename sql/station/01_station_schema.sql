-- =====================================================================
-- 独立站服务器上的 sdsstation 库：表结构基线
-- =====================================================================
--
-- ⚠ 这个脚本跑在**独立站那台服务器**上，不是 ERP 那台。
--
-- 边界依据：docs/独立站与ERP的边界.md（要点 1「ERP 和独立站完全分开部署，
-- 独立站与 ERP 的后端没有任何互通」）。这条是硬约束，下面每一个设计决定都是它的推论。
--
-- 和 ERP 那个 sql/01_schema_and_init.sql 的关系：
--   · 库名沿用 `sdsstation`，表名沿用 `u_s_*`——搬迁这一步只搬东西、不改结构，
--     出了问题才好判断是搬坏了还是改坏了（boundary §5.3）；
--   · **没有 u_s_site**。站点档案留在 ERP 的 sdsshop 里当登记表，
--     本站自己那份档案是下面的 u_s_siteinfo（boundary §5.2、§7.2）;
--   · 多了三张 ERP 侧没有的表：u_s_siteinfo / u_s_user / u_s_storage。
--
-- 两条必须守住的规矩：
--
--   1. **一个部署一个站。** 所有表都只装这一个站的数据。`sitecode` 列还在，
--      但它的值恒等于 u_s_siteinfo.sitecode，不是一个筛选维度。
--
--   2. **不写库限定名。** 站点后端只连这一个库，SQL 里一律写 `from u_s_sitegoods`，
--      不写 `from sdsstation.u_s_sitegoods`。不写库名就物理上不可能再指到别的库去，
--      这比写一句注释管用（boundary §5.3）。
--
-- 可重复执行：全文只有 CREATE DATABASE IF NOT EXISTS / CREATE TABLE IF NOT EXISTS。
-- 跑在已有数据的库上不会毁数据，但也修不好结构——结构变更走 sql/station/migration/。
--
-- =====================================================================
-- ⚠ 这份基线**必须和 migration/ 保持同步**，而它曾经不同步
-- =====================================================================
--
-- 上面那句「结构变更走 migration」读起来像是「基线始终是最新的，migration 只给老库
-- 升级」。**不是。** 2026-09-05 实测：按这份建出来的新库比已部署的库少 13 个列和
-- 一整张表——都来自 migration/2026-09-04-inquiry-structured-rfq.sql，加了 migration
-- 却没有回写基线。新站和老站就此分叉，而且没有任何东西会报错。
--
-- 2026-09-06 已经补齐并逐列比对过。**以后加 migration，同一个提交里就要改这份。**
--
-- 怎么验（比读脚本可靠，两边都跑，然后 diff）：
--
--     SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME)
--       FROM information_schema.COLUMNS
--      WHERE TABLE_SCHEMA='sdsstation'
--      ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- 建新站点库的两个做法，任选：
--   a. 跑这份基线，**再按文件名日期顺序跑 migration/ 下全部脚本**；
--   b. 从一个结构正确的库整库导过去：mysqldump --databases sdsstation
--      （2026-09-05 部署 49.234.194.194 用的是这个，它同时完成了数据迁移）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `sdsstation`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `sdsstation`;

SET NAMES utf8mb4;


-- =====================================================================
-- 一、本站自己的五张表（ERP 侧没有）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 本站档案。**这张表只有一行。**
--
-- 它装的是从 ERP 的 u_s_site 上摘下来的那批「站里的业务数据」（boundary §7.2）：
-- 站点编码、名称、语言、币种、前台开关、首页装修 JSON。
--
-- 为什么站点必须能自己说出自己是谁：跳转令牌里的 site 声明要和这里的 sitecode
-- 对齐才放行（boundary §4.3）。靠问 ERP 拿自己的身份，就又是一条互通了。
--
-- status 和 ERP 那边的 status 是**两个开关，不是一个值的副本**：
--   · ERP 那份管「这个站还在不在服务、还发不发跳转令牌」；
--   · 这一份管「前台还开不开」。
-- 语言和币种则相反，只有这一份——ERP 留一份就是两份会各自被改的配置，
-- 而且没有任何机制能发现它们不一致了。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_siteinfo` (
  `id`          tinyint      NOT NULL DEFAULT 1 COMMENT '恒为 1。一个部署一个站，这张表只有一行',
  `sitecode`    varchar(50)  NOT NULL COMMENT '站点编码。必须和 ERP 登记的那个值一致，跳转令牌的 site 声明靠它对齐',
  `sitename`    varchar(100) NOT NULL COMMENT '站点名称，管理台顶栏显示',
  `shop_code`   varchar(50)  DEFAULT NULL COMMENT '【遗留列】建站时那个商户码。新代码不得读它、不得按它过滤，见本文件末尾的说明',
  `domain`      varchar(200) DEFAULT NULL COMMENT '主域名，存主机名如 tongling.vclinker.com，不带协议头。⚠ 存主域不是 buyer. 子域——前台分两个 host（主域公开页 / buyer. 账号页），验证信里的链接拼主域，靠 nginx 302 转到 buyer.，见 deploy/nginx/tongling.conf。⚠ 不是 SEO 字段：前台 canonical/hreflang 走 station-site 的 NUXT_PUBLIC_SITE_URL，不看这一列。真正用它的是买家验证邮件和找回密码邮件里的链接（BuyerAuthService#siteBase），空着那两个接口直接抛 500，而前台注册页面上看不出任何异常。在管理台「站点设置」里改，ERP 那边的域名是另一个库的另一件事，不同步',
  `langs`       varchar(200) DEFAULT NULL COMMENT '开放的语言，逗号分隔，如 en,ru,es。第一个是站点默认语言',
  `consolelang` varchar(10)  DEFAULT NULL COMMENT '管理台界面的默认语言：zh/en/fr，null=zh。⚠ 与上面的 langs 无关——那个是前台对买家开放哪几种语言，这个是管理台自己的界面语言（法国站的站长看不懂中文，而我们的业务员要看中文）。它只是**默认值**：每个人进来后可以在管理台顶栏自己切，切完存在他自己浏览器的 localStorage(station-console-lang) 里，不回写这一列。候选值必须和 station-console-ui/src/i18n 的文案表一致，写一个没有文案表的语言码等于整屏退回中文（后端 SiteInfo.CONSOLE_LANGS 会兜住）',
  `theme`       varchar(20)  DEFAULT NULL COMMENT '前台视觉主题：classic=紫色+Fraunces（康辰），NULL/esva=黑+电光蓝（法国站，site.css 本身那套）。见 station-site/app/assets/theme-classic.css；只能改库，管理台不改它',
  `currency`    varchar(10)  NOT NULL DEFAULT 'USD' COMMENT '结算币种。⚠ 这是**基准**：商品价格 u_s_sitegoods.price 就是用它存的，订单也一律落它。别和下面的 currencies 看混',
  `currencies`  varchar(200) DEFAULT NULL COMMENT '前台允许买家切换的显示币种，逗号分隔，如 USD,EUR,CNY。留空=不显示切换器。⚠ 与 currency 只差一个字母但不是一回事：那个是基准，这个是买家能切到哪些。基准币种由后端自动并入（SiteInfo.currencyList），不必重复填。换算率见 u_s_rate',
  `status`      varchar(20)  NOT NULL DEFAULT '运行中' COMMENT '前台开关：建设中/运行中/已停用。⚠ 与 ERP 那份 status 是两个决定，不是副本',
  `expiredate`  date         DEFAULT NULL COMMENT '服务到期日，null=不提醒。管理台首页那个统计块按剩余天数标黄（≤30天）标红（已过期），剩余天数不落库、由 SiteInfo.getExpireDays() 现算（服务把档案缓存在进程里，落库或用 SQL datediff 都会把天数冻在加载那一刻）。⚠ ERP 的 u_s_site 里也有一个 expiredate，两份不同步：那份是收费台账，这份给站长看',
  `contentjson` mediumtext COMMENT '装修内容 JSON。⚠ 是 v3 分语言结构，不是一份平铺的内容：{"v":3,"logo":"…","contact":{"phone":"…"},"reach":{…},"i18n":{"en":{…},"ja":{…}}}——i18n 之外的顶层键是共享层，各语言通用。三层从下往上铺：共享层 → 站点默认语言层 → 当前语言层，所以某语言只译了一半时缺的那几项按项退回默认语言（2026-09-10 起；在那之前是整份论的）。同名键两边都是对象时合一层（共享层放 contact.phone、语言层放 contact.address，合出来四项都在），其余整个覆盖。语言层的键：companyName/slogan/seoTitle/seoDescription/nav/hero(title,subtitle)/highlights/capabilities/about(title,content,stats)/contact(address)/reach(title,lead,stats)。共享层的键：logo、hero.images（Banner 背景图数组，多张即轮播；老数据是单张 hero.image，两种都认）、about.image、contact(phone,email,whatsapp)、reach(hq,destinations)。共享层还可以有 order（首页区块顺序）、off（关掉了哪几块）、blocks（每块的小设置）——后端不认识这三个，原样转发给前台。为空时接口返回内置默认。写成 v1 平铺或 v2 结构都仍然认，不会报错。实现见 SiteOpenService#siteContent',
  `notifyemail` varchar(500) DEFAULT NULL COMMENT '询盘通知邮箱，多个用逗号分隔，留空则不发',
  `consolesecret` varchar(500) DEFAULT NULL COMMENT '跳转令牌的验签密钥，一站一份。⚠⚠ 本列存**明文**，ERP 的 u_s_site.consolesecret 存的是 AES-GCM **密文**——两边是同一个密钥，但不是同一个列值。ERP 签名前会 AesUtils.decrypt 一次（StationConsoleSsoServiceImpl.decryptSecret），本站 JumpTokenVerifier.resolveSecret 直接拿本列的值去 HMAC、不解密。所以**不能**从 ERP 库里 SELECT 出来粘过来：那样不报错，只是每次跳转都签名不匹配，日志里像代码 bug。留空 = 跳转一律拒绝（fail-closed，正确的默认）',
  `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser`  varchar(50)  DEFAULT NULL COMMENT '更新人',
  `updatetime`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_siteinfo_sitecode` (`sitecode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本站档案，只有一行';


-- ---------------------------------------------------------------------
-- 管理台操作用户。**和 ERP 的 sys_user 没有任何关系。**
--
-- 要点 5：每套独立站自己成立用户体系，站与站之间、站与 ERP 之间都不关联。
--
-- **这张表里的行不是建出来的，是长出来的**：谁从 ERP 点「进入管理」第一次跳过来，
-- 后端就为他插一行（source='erpsso'，默认角色 operator），之后由站长在本站调角色。
-- 管理台里只能改和删，没有「新建用户」。
--
-- source='local' 是**历史遗留**：boundary §6a 曾经给管理台加过账号密码登录，
-- 那时候站长能在本站手工建账号。那一版撤掉了（唯一入口回到 ERP 发的令牌，
-- 见 station-console 的 SessionController、docs/独立站与ERP的边界.md §6d），
-- 所以 password 那一列也废了，见下面的列注释。库里已有的 local 行留着，但进不来。
--
-- 代价知道就好：ERP 挂了或者网断了，**没有人能新进这个后台**（已经建立的会话不受影响）。
-- 这是明知故犯换来的——本站不留第二套可以被撞库、被弱口令攻破的凭据。
--
-- erpuser 那一列存 ERP 令牌里的 payload.user，只作为**匹配用的身份标识**。
-- 令牌里没有 perms，也不该有：权限归本站这套，由 rolecode 决定（boundary §4.3）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_user` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `username`      varchar(64)  NOT NULL COMMENT '登录名。取令牌里的 user；撞上已有的名字时自动加 @erp 后缀',
  `password`      varchar(200) DEFAULT NULL COMMENT '【已废弃】曾经存 BCrypt 密文，给管理台的账号密码登录用。那一版撤掉了：本站没有登录页，唯一入口是从 ERP 免密进。现在没有任何代码读写这一列，新行一律 NULL。列没删是为了不动已有库的结构',
  `nickname`      varchar(100) DEFAULT NULL COMMENT '显示名，管理台顶栏那个',
  `rolecode`      varchar(50)  NOT NULL DEFAULT 'operator' COMMENT '角色，见 u_s_role。ERP 首次跳进来的人给这个默认值，之后由站长在本站调',
  `source`        varchar(20)  NOT NULL DEFAULT 'local' COMMENT '来源：erpsso 从 ERP 首次跳转自动落的——新行只会是这个；local 是遗留（有登录页那一版手工建的）',
  `erpuser`       varchar(64)  DEFAULT NULL COMMENT '对应 ERP 令牌里的 payload.user。免密进入靠它找人；遗留的 local 行为 NULL',
  `status`        varchar(20)  NOT NULL DEFAULT '正常' COMMENT '正常/停用。停用后从 ERP 免密进入会被拒',
  `logincount`    int          NOT NULL DEFAULT 0 COMMENT '累计进入次数（都是从 ERP 免密进来的）',
  `lastlogintime` datetime     DEFAULT NULL COMMENT '最后登录时间',
  `lastloginip`   varchar(64)  DEFAULT NULL COMMENT '最后登录 IP',
  `createuser`    varchar(50)  DEFAULT NULL COMMENT '创建人',
  `createtime`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_erpuser` (`erpuser`) COMMENT '一个 ERP 账号在本站只对应一个用户。多个 NULL 是允许的，遗留的 local 行不受影响'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理台操作用户，本站独有';


-- ---------------------------------------------------------------------
-- 角色。
--
-- perms 存的就是前端 v-hasPermi 用的那批权限串（site:goods:list 之类），逗号分隔——
-- 沿用同一套串，station-console-ui 的按钮显隐逻辑一个字都不用改。
--
-- 为什么不做 用户-角色 多对多：一个站的后台用户是个位数，一人一角色够用。
-- 多一张关联表就多一处 join，而这里没有任何需求要求它。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_role` (
  `rolecode`   varchar(50)   NOT NULL COMMENT '角色编码',
  `rolename`   varchar(50)   NOT NULL COMMENT '角色名称',
  `perms`      varchar(2000) NOT NULL DEFAULT '' COMMENT '权限串，逗号分隔。* 表示全部',
  `sortnum`    int           NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`     varchar(500)  DEFAULT NULL COMMENT '备注',
  `createtime` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`rolecode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理台角色，本站独有';


-- ---------------------------------------------------------------------
-- 本站的对象存储配置。替代 ERP 的 sdssys.s_m_shop_storage（boundary §5.2、§10.3.2）。
--
-- ⚠ 那张表**不在 sdsstation，在 sdssys**，而且 ERP 自己的文件上传也在用它
-- （ruoyi-common 的 OssStorageService / OssProperties）。所以它**留在 ERP 原地不动**，
-- 站点自己新建这一份。它之所以看着像独立站的表，只是因为 mapper 文件被放进了 mapper/station/ 目录。
--
-- 结构照抄那一张，只去掉 shop_code 主键（一个部署一个站，没有商家维度）。
-- access_key_secret 仍然是 AES-GCM 密文，主密钥从环境变量读，和 ERP 那套是同一个工具类
-- （AesUtils），**不要再发明第二种加密方式**——两套加密就有两个主密钥要轮换，
-- 而第二套一定是那个没人记得怎么轮换的。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_storage` (
  `provider`          varchar(20)  NOT NULL COMMENT '厂商：aliyun / huawei / tencent / local',
  `endpoint`          varchar(200) DEFAULT NULL COMMENT '访问域名。腾讯云留空，由 region 推导',
  `region`            varchar(50)  DEFAULT NULL COMMENT '地域代号，腾讯云必填，如 ap-beijing',
  `access_key_id`     varchar(200) DEFAULT NULL COMMENT 'AccessKeyId / SecretId，明文存',
  `access_key_secret` varchar(500) DEFAULT NULL COMMENT 'AccessKeySecret / SecretKey，AES-GCM 密文',
  `bucket`            varchar(100) DEFAULT NULL COMMENT '桶名。腾讯云必须带 APPID，如 name-1300000000',
  `public_url`        varchar(200) DEFAULT NULL COMMENT '自定义域名/CDN。留空用 https://<bucket>.<endpoint>/',
  `status`            char(1)      NOT NULL DEFAULT '0' COMMENT '0 启用 1 停用',
  `active`            char(1)      NOT NULL DEFAULT '0' COMMENT '是否当前生效：1 是 0 否。只有一行为 1',
  `createtime`        datetime     DEFAULT NULL,
  `updatetime`        datetime     DEFAULT NULL,
  `create_by`         varchar(64)  DEFAULT NULL,
  `update_by`         varchar(64)  DEFAULT NULL,
  PRIMARY KEY (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本站对象存储配置';


-- ---------------------------------------------------------------------
-- 站长工具配置。单行表，同 u_s_storage 的范式（一份全站配置、和站点档案分开）。
--
-- ⚠⚠ **这张表整体是公开的。** 每一列最终都会出现在匿名接口
-- `/openapi/site/info` 的返回里——必须如此，因为 sitemap.xml / robots.txt 是
-- station-site（Nitro）生成的，不是后端生成的，连 sitemapmax 这种看着像后端参数
-- 的东西也得发给前台。
--
-- 所以：**将来要存 GSC 的 API key、第三方 token 之类的东西，必须另建一张表。**
-- 加在这里不报错，只是把密钥公开在一个谁都能 curl 的接口上，而且没有任何现象提示。
--
-- ⚠ 统计那三列存的是 **ID，不是代码**。不给「自由粘贴统计代码」的框——
-- 那是一个真的存储型 XSS 入口（任何能进管理台的人都能在每个公开页面上执行
-- 任意 JS，包括偷买家 Cookie 和改结账页）。代价是接新平台要改代码，认了。
--
-- 迁移脚本：sql/station/migration/2026-09-08-seo-tools.sql（含逐列的理由）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_seo` (
  `id` tinyint NOT NULL DEFAULT 1 COMMENT '恒为 1。一个部署一个站，这张表只有一行',
  `noindexall`    tinyint      NOT NULL DEFAULT 0 COMMENT '1=整站禁止收录。⚠ 与 u_s_siteinfo.status 是两个独立的决定，不是副本：status 管前台开不开，这一列管「站开着但先别收录」（软上线）',
  `disallowpaths`  varchar(1000) DEFAULT NULL COMMENT '额外 Disallow 路径，逗号分隔。⚠ 是追加，不替换 robots.txt.ts 里那批基线（购物车/结账/我的询价/登录注册/账号中心）',
  `robotsextra`    varchar(2000) DEFAULT NULL COMMENT 'robots.txt 末尾原样追加的内容',
  `sitemapon`      tinyint      NOT NULL DEFAULT 1 COMMENT '1=生成 sitemap.xml，0=返回 404',
  `sitemapmax`     int          NOT NULL DEFAULT 5000 COMMENT '进 sitemap 的商品条数上限。⚠ 不是 URL 条数，每个商品乘以语言数',
  `homefreq`       varchar(20)  NOT NULL DEFAULT 'daily' COMMENT '首页和列表页的 changefreq',
  `goodsfreq`      varchar(20)  NOT NULL DEFAULT 'weekly' COMMENT '商品详情页的 changefreq',
  `googleverify`   varchar(100) DEFAULT NULL COMMENT 'Google Search Console 验证值。只填 content 那一段，不是整个 meta 标签',
  `bingverify`     varchar(100) DEFAULT NULL COMMENT 'Bing 的 msvalidate.01 值',
  `baiduverify`    varchar(100) DEFAULT NULL COMMENT '百度站长的验证值',
  `ga4id`          varchar(50)  DEFAULT NULL COMMENT 'GA4 测量 ID，形如 G-XXXXXXXXXX。⚠ 只存 ID，脚本由前台拼；格式在保存时和输出时各校验一次',
  `gtmid`          varchar(50)  DEFAULT NULL COMMENT 'GTM 容器 ID，形如 GTM-XXXXXXX。⚠ 和 ga4id 同时填大概率重复计数',
  `baiduhmid`      varchar(50)  DEFAULT NULL COMMENT '百度统计 hm.js 的 32 位 id。⚠ 对海外访客慢甚至连不上，默认留空',
  `remark`     varchar(500) DEFAULT NULL COMMENT '备注',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50)  DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站长工具配置，只有一行。⚠ 每一列都会出现在匿名接口里，不许存密钥';


-- =====================================================================
-- 二、从 ERP 搬过来的五张表（结构照抄，不改名）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 商品。
--
-- ⚠ **这张表比 ERP 那版多四列**：goodsname / spec / imgurl / mainimgs。
--
-- 原因是 boundary §5.4：商品从「在 ERP 库存目录里挑」改成「站点自己录」。
-- ERP 那版只存站点售价、分类、推荐位这些站点侧属性，名称、规格、档案图都还在
-- sdsshop.u_m_waregoods 里，取数时 join 回去（StationSiteGoodsMapper.xml 里三处）。
--
-- 那条 join 就是一条互通，而且它是**跨库的**——库一搬到这台机器，
-- 它连语法上都成立不了（跨库限定名只在同一个 MySQL 实例里有效）。
-- 所以那四列必须变成本表自己的列。
--
-- 代价：同一个商品在 ERP 和三个站里各录一遍。这是要点 1 的直接成本，一期认了。
-- 后期真要省这个人工，走的是「导出 / 导入一个商品包」这种**离线**方式，
-- 不是重新开一条实时接口。
--
-- unit 的语义也跟着变了：ERP 那版是 coalesce(sg.unit, g.unit)，留空回退商品档案；
-- 现在没有档案可回退，留空就是留空。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitegoods` (
  `shop_code`     varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾。恒为建站时那个商户码',
  `sitecode`      varchar(50)   NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `goodscode`     varchar(50)   NOT NULL COMMENT '商品编码。本站自己的编码，不再弱关联 ERP 的商品档案',
  `goodsname`     varchar(200)  DEFAULT NULL COMMENT '商品名称（内部叫法）。搬迁前来自 sdsshop.u_m_waregoods.goodsname，现在本站自己录',
  `spec`          varchar(200)  DEFAULT NULL COMMENT '规格型号。同上，原 u_m_waregoods.spec',
  `imgurl`        varchar(500)  DEFAULT NULL COMMENT '主图。原 u_m_waregoods.imgurl，siteimgs 为空时的回退来源',
  `mainimgs`      varchar(2000) DEFAULT NULL COMMENT '档案图组，逗号分隔。原 u_m_waregoods.mainimgs',
  `sitecategory`  varchar(100)  DEFAULT NULL COMMENT '站点分类（英文），列表页的筛选按钮由它去重生成',
  `price`         decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '站点售价，0 表示不标价、只显示 Inquiry',
  `moq`           decimal(18,4) DEFAULT NULL COMMENT '起订量',
  `unit`          varchar(20)   DEFAULT NULL COMMENT '起订量单位。⚠ 不再回退商品档案，留空就是留空',
  `featured`      tinyint       NOT NULL DEFAULT '0' COMMENT '是否首页推荐：1 是 0 否',
  `publishstatus` varchar(20)   NOT NULL DEFAULT '草稿' COMMENT '上架状态：草稿/已上架/已下架，只有已上架的对外可见',
  `sortnum`       int           NOT NULL DEFAULT '0' COMMENT '站点展示排序，越小越靠前',
  `specsjson`     varchar(2000) DEFAULT NULL COMMENT '规格键值对 JSON，详情页渲染成表格；如 {"Material":"Silicone"}',
  `optionsjson`   text          DEFAULT NULL COMMENT '组合的规格维度 JSON（最多两维，如尺码/款式），取值按编码挂多语言名称。空 = 组合是平铺的一排（2026-09-25，migration/2026-09-25-goods-options.sql）',
  `siteimgs`      varchar(2000) DEFAULT NULL COMMENT '站点专用图片，逗号分隔的完整 URL，第一张为主图。留空则回退 imgurl / mainimgs',
  `sitevideos`    varchar(2000) DEFAULT NULL COMMENT '站点商品视频，逗号分隔的完整 URL。来自文件资源库，无回退来源',
  `model3d`       varchar(500)  DEFAULT NULL COMMENT '3D 模型（.glb）完整地址，来自文件资源库；空 = 没有模型，详情页不渲染展示台',
  `model3d_marks` text          DEFAULT NULL COMMENT '3D 标注 JSON（logo 贴花/屏幕/开机键），坐标是 glb 场景自身坐标；空 = 没有标注',
  `model3d_states` text         DEFAULT NULL COMMENT '3D 模型状态 JSON（第一个=主模型 model3d，其余各一个 glb）；空 = 没有状态',
  `mainvideo`     varchar(500)  DEFAULT NULL COMMENT '主图位置的视频，完整地址，来自文件资源库；空 = 图库第一格仍是图。封面取其后第一张图',
  `remark`        varchar(500)  DEFAULT NULL COMMENT '备注',
  `createuser`    varchar(50)   DEFAULT NULL COMMENT '创建人',
  `createtime`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser`    varchar(50)   DEFAULT NULL COMMENT '更新人',
  `updatetime`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`),
  KEY `idx_sitegoods_list` (`shop_code`,`sitecode`,`publishstatus`,`sortnum`) COMMENT '对外列表接口走这条',
  KEY `idx_sitegoods_goods` (`shop_code`,`goodscode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点商品（含商品档案，不再 join ERP）';


-- ---------------------------------------------------------------------
-- 商品多语言文案。结构和 ERP 那版完全一致，一列没动。
--
-- description 是富文本，**对外输出前必须做 HTML 白名单过滤**——
-- 这条规矩跟着代码一起搬过来了（RichTextCleaner），不因为换了台机器就放宽。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitegoods_lang` (
  `shop_code`   varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`    varchar(50)  NOT NULL COMMENT '站点编码',
  `goodscode`   varchar(50)  NOT NULL COMMENT '商品编码',
  `lang`        varchar(20)  NOT NULL COMMENT '语言代码，如 en / ru / es',
  `title`       varchar(300) DEFAULT NULL COMMENT '站点标题，不是内部品名',
  `subtitle`    varchar(500) DEFAULT NULL COMMENT '一句话卖点，列表和详情都用得上',
  `description` mediumtext COMMENT '详情富文本。对外输出前必须做 HTML 白名单过滤',
  `keywords`    varchar(500) DEFAULT NULL COMMENT 'SEO 关键词，逗号分隔',
  `specsjson`    varchar(4000) DEFAULT NULL COMMENT '规格译文，按默认语言的键当身份：{"默认键":{"k":"译键","v":"译值"}}。缺键回退主表 specsjson（2026-09-21 加）',
  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点商品多语言文案';


-- ---------------------------------------------------------------------
-- 商品组合（详情页上那排带缩略图的「规格型号」选项卡）。
--
-- 和商品本身一样拆成主表 + 语言表，因为**组合名要分语言**。
-- 一列 JSON 也存得下，但塞进七种语言之后就变成 {"en":[…],"ja":[…]}，
-- 于是「第三个组合」在两种语言里靠**下标**对齐——商家在英文里删掉中间一条，
-- 其余六种语言全错位，而且不报错。拆开之后对齐靠 combocode。
--
-- ⚠ 不做库存：站不收款不发货，ERP 又不互通（boundary 要点 1），
--   站内库存没有数据来源，只能手工维护，两周就和实际对不上。
--   要「售罄置灰」用 status 那一列。
--
-- 迁移脚本：sql/station/migration/2026-09-08-goods-combo.sql
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitegoods_combo` (
  `shop_code`  varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`   varchar(50)   NOT NULL COMMENT '站点编码',
  `goodscode`  varchar(50)   NOT NULL COMMENT '所属商品编码',
  `combocode`  varchar(50)   NOT NULL COMMENT '组合编码。⚠ 这是**稳定标识**：名称分七种语言、还会被商家改，只有它不变。单据快照、深链 ?combo=、按组合统计全靠它',
  `opt1`       varchar(50)   DEFAULT NULL COMMENT '第一维的取值编码（对应 u_s_sitegoods.optionsjson 的 dims[0].values[].code）。平铺模式下为空',
  `opt2`       varchar(50)   DEFAULT NULL COMMENT '第二维的取值编码。只有一个维度时为空',
  `price`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '该组合的价格，站点本币。⚠ 0 = 面议，不是免费（和 u_s_sitegoods.price 同一约定）',
  `image`      varchar(500)  DEFAULT NULL COMMENT '组合缩略图完整 URL。各语言通用——产品实拍不分市场。留空则选项卡只显示名字',
  `model3d`    varchar(500)  DEFAULT NULL COMMENT '这个规格自己的 3D 模型 .glb 地址；空 = 用商品主模型',
  `model3d_marks` text       DEFAULT NULL COMMENT '这个规格自己的 3D 标注；空 = logo 跟随主模型（前台自动投影）',
  `model3d_states` text      DEFAULT NULL COMMENT '这个规格的3D模型状态JSON；首个状态使用model3d，其余为该规格状态模型',
  `sortnum`    int           NOT NULL DEFAULT 0 COMMENT '显示顺序，越小越靠前。第一条是详情页默认选中的那个',
  `status`     varchar(20)   NOT NULL DEFAULT '可选' COMMENT '可选/停用。停用的不对外返回，但历史单据里的快照还在',
  `createuser` varchar(50)   DEFAULT NULL COMMENT '创建人',
  `createtime` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50)   DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`,`combocode`),
  KEY `idx_combo_goods` (`shop_code`,`sitecode`,`goodscode`,`status`,`sortnum`) COMMENT '详情页取某个商品的可选组合走这条'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品组合（规格型号），语言无关的部分';


-- ---------------------------------------------------------------------
-- 组合名称多语言。只有 title 一个字段——价格、图片、排序改一次七种语言一起变，
-- 放语言表就要改七行，还会出现「六种语言 199、一种语言 299」这种没人发现的错。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitegoods_combo_lang` (
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码',
  `goodscode`  varchar(50)  NOT NULL COMMENT '所属商品编码',
  `combocode`  varchar(50)  NOT NULL COMMENT '所属组合编码',
  `lang`       varchar(20)  NOT NULL COMMENT '语言代码，如 en / ru / ja',
  `title`      varchar(200) DEFAULT NULL COMMENT '该语言下的组合名。某种语言没填时，接口退回站点默认语言那一条',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`,`combocode`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品组合的多语言名称';


-- ---------------------------------------------------------------------
-- 图片资源。结构照抄。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_siteimage` (
  `imageid`    varchar(50)  NOT NULL COMMENT '图片主键',
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`   varchar(50)  DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `imagename`  varchar(200) NOT NULL COMMENT '显示名称，默认取原始文件名，可改',
  `imageurl`   varchar(500) NOT NULL COMMENT '完整访问地址',
  `thumburl`   varchar(600) DEFAULT NULL COMMENT '缩略图地址，厂商服务端处理，不占额外存储',
  `objectname` varchar(300) DEFAULT NULL COMMENT '桶内对象路径，删除文件时要用',
  `provider`   varchar(20)  DEFAULT NULL COMMENT '存到了哪个厂商，换厂商后老文件仍可追溯',
  `filesize`   bigint       DEFAULT NULL COMMENT '字节数',
  `mimetype`   varchar(100) DEFAULT NULL,
  `width`      int          DEFAULT NULL COMMENT '像素宽',
  `height`     int          DEFAULT NULL COMMENT '像素高',
  `remark`     varchar(500) DEFAULT NULL,
  `createtime` datetime     DEFAULT NULL,
  `updatetime` datetime     DEFAULT NULL,
  `create_by`  varchar(64)  DEFAULT NULL,
  `update_by`  varchar(64)  DEFAULT NULL,
  PRIMARY KEY (`imageid`),
  KEY `idx_siteimage_shop` (`shop_code`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点图片资源';


-- ---------------------------------------------------------------------
-- 视频资源。结构照抄。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitevideo` (
  `videoid`    varchar(50)  NOT NULL COMMENT '视频主键',
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`   varchar(50)  DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `videoname`  varchar(200) NOT NULL COMMENT '显示名称，默认取原始文件名，可改',
  `videourl`   varchar(500) NOT NULL COMMENT '完整访问地址',
  `objectname` varchar(300) DEFAULT NULL COMMENT '桶内对象路径，删除文件时要用',
  `provider`   varchar(20)  DEFAULT NULL COMMENT '存到了哪个厂商，换厂商后老文件仍可追溯',
  `filesize`   bigint       DEFAULT NULL COMMENT '字节数',
  `mimetype`   varchar(100) DEFAULT NULL,
  `width`      int          DEFAULT NULL COMMENT '画面宽，前端按它对齐卡片',
  `height`     int          DEFAULT NULL COMMENT '画面高',
  `duration`   int          DEFAULT NULL COMMENT '时长（秒）',
  `remark`     varchar(500) DEFAULT NULL,
  `createtime` datetime     DEFAULT NULL,
  `updatetime` datetime     DEFAULT NULL,
  `create_by`  varchar(64)  DEFAULT NULL,
  `update_by`  varchar(64)  DEFAULT NULL,
  PRIMARY KEY (`videoid`),
  KEY `idx_sitevideo_shop` (`shop_code`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点视频资源';


-- ---------------------------------------------------------------------
-- 站点 3D 模型资源（2026-09-20 加，迁移脚本 migration/2026-09-20-goods-model3d.sql）。
-- 和 u_s_sitevideo 同构，去掉宽高时长。只收 .glb（白名单在 application.yml）。
-- 商品挂模型存的是 modelurl（u_s_sitegoods.model3d），不是 modelid。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sitemodel` (
  `modelid`    varchar(50)  NOT NULL COMMENT '模型主键，SMDL 开头',
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`   varchar(50)  DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `modelname`  varchar(200) NOT NULL COMMENT '显示名称，默认取原始文件名，可改',
  `modelurl`   varchar(500) NOT NULL COMMENT '完整访问地址，上传那一刻整串入库',
  `objectname` varchar(300) DEFAULT NULL COMMENT '桶内对象路径，model/YYYY/MM/DD/<modelid>.glb',
  `provider`   varchar(20)  DEFAULT NULL COMMENT '存到了哪个厂商，换厂商后老文件仍可追溯',
  `filesize`   bigint       DEFAULT NULL COMMENT '字节数',
  `mimetype`   varchar(100) DEFAULT NULL COMMENT '浏览器给的 Content-Type，.glb 常见 model/gltf-binary 或空',
  `remark`     varchar(500) DEFAULT NULL,
  `folderid`   varchar(50)  DEFAULT NULL COMMENT '所在文件夹，NULL = 未分类',
  `createtime` datetime     DEFAULT NULL,
  `updatetime` datetime     DEFAULT NULL,
  `create_by`  varchar(64)  DEFAULT NULL,
  `update_by`  varchar(64)  DEFAULT NULL,
  PRIMARY KEY (`modelid`),
  KEY `idx_sitemodel_shop` (`shop_code`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点 3D 模型资源（.glb）';


-- ---------------------------------------------------------------------
-- 混元生 3D 任务（2026-09-26 加，迁移脚本 migration/2026-09-26-model-gen-job.sql）。
-- 管理台从文件库选商品照片 → 提交腾讯混元 → 后端轮询 → 下载 glb 转存进 u_s_sitemodel。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_model_job` (
  `jobid`       varchar(50)   NOT NULL COMMENT '本站任务号，SMJB 开头',
  `sitecode`    varchar(50)   DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `tcjobid`     varchar(100)  DEFAULT NULL COMMENT '混元返回的 JobId（24 小时有效）；提交失败时为空',
  `status`      varchar(20)   NOT NULL COMMENT 'SUBMITTING / WAIT / RUN / SAVING / DONE / FAIL',
  `jobname`     varchar(200)  DEFAULT NULL COMMENT '生成后模型的显示名',
  `imagesjson`  varchar(4000) DEFAULT NULL COMMENT '{"front":url,"left":url,"right":url,"back":url}，只存用到的',
  `facecount`   int           DEFAULT NULL,
  `enablepbr`   tinyint       NOT NULL DEFAULT '0',
  `folderid`    varchar(50)   DEFAULT NULL COMMENT '生成后放进哪个模型文件夹',
  `modelid`     varchar(50)   DEFAULT NULL COMMENT '成功后对应的 u_s_sitemodel.modelid',
  `previewurl`  varchar(1000) DEFAULT NULL COMMENT '混元给的预览图地址（临时，过期就不显示）',
  `errorcode`   varchar(100)  DEFAULT NULL,
  `errormsg`    varchar(1000) DEFAULT NULL COMMENT '已翻译成给商家看的话',
  `polls`       int           NOT NULL DEFAULT '0' COMMENT '已查询次数',
  `saveretries` int           NOT NULL DEFAULT '0' COMMENT '下载转存失败次数，满 5 次置 FAIL',
  `create_by`   varchar(64)   DEFAULT NULL,
  `createtime`  datetime      DEFAULT NULL,
  `updatetime`  datetime      DEFAULT NULL,
  `finishtime`  datetime      DEFAULT NULL,
  PRIMARY KEY (`jobid`),
  KEY `idx_modeljob_status` (`status`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='混元生 3D 任务（照片生成 .glb）';


-- ---------------------------------------------------------------------
-- 买家 3D 定制方案（2026-09-29 加，迁移脚本 migration/2026-09-29-custom-plan.sql）。
-- 前台 /customize 页上买家贴 logo、换开机画面，保存后不可改；planid 随机，不走发号器。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_custom_plan` (
  `planid`     varchar(16)   NOT NULL COMMENT '16 位随机 base62，方案链接里那一段',
  `shop_code`  varchar(50)   DEFAULT NULL COMMENT '同其他 u_s_* 表，新代码不读不筛',
  `sitecode`   varchar(50)   DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `goodscode`  varchar(50)   NOT NULL COMMENT '定制的是哪个商品',
  `combocode`  varchar(50)   DEFAULT NULL COMMENT '定制的是哪个规格；空 = 主模型',
  `model3d`    varchar(500)  NOT NULL COMMENT '保存时商品模型地址的快照',
  `marks`      text          NOT NULL COMMENT '完整 v1 标注 JSON 快照（商家的屏幕/开机键 + 买家的 logo/开机画面）',
  `model3d_states` text       DEFAULT NULL COMMENT '保存方案时商品模型状态的快照（只含有模型的状态，名字全语言）；空 = 没有状态',
  `email`      varchar(200)  DEFAULT NULL COMMENT '买家选填；公开接口不下发',
  `note`       varchar(500)  DEFAULT NULL COMMENT '买家选填；公开接口不下发',
  `parentid`   varchar(16)   DEFAULT NULL COMMENT '从哪个方案「在此基础上修改」来的',
  `iphash`     char(64)      DEFAULT NULL COMMENT '提交 IP 的 SHA-256（加盐），不存明文',
  `createtime` datetime      NOT NULL,
  PRIMARY KEY (`planid`),
  KEY `idx_customplan_goods` (`goodscode`,`createtime`),
  KEY `idx_customplan_time` (`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='买家 3D 定制方案（保存后不可改）';


-- ---------------------------------------------------------------------
-- 客户询盘。
--
-- ⚠ 比 ERP 那版**少一件事**：没有「转 ERP 客户」这个动作了（boundary §3.2）。
-- 那是一条互通——它往 ERP 的 sdsshop.u_m_buyer 里写数据。
--
-- buyercode 这一列**保留不删**：删列是不可逆的，而站点自己的客户管理（一期不做，
-- boundary §11）将来正好用得上这个位置。留着、空着，成本是零。
-- 列注释改了，说明它现在指的是本站客户，不是 ERP 的。
-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- ⚠ 下面标了「【RFQ 留位置】」的 13 列，现在**没有任何代码在写**，恒为 NULL。
--
-- 它们来自 migration/2026-09-04-inquiry-structured-rfq.sql。那次迁移的设想是：
-- 结账页继续走询盘接口，但把收货地址/运输付款偏好/商品明细结构化，不再拼成
-- 一段会被截断的文本塞进 message。
--
-- **那个设想后来没有落地，被订单接口取代了**（2026-09-06 决定）：结账页现在
-- POST /openapi/site/order，落 u_s_order + u_s_orderitem，那条路是完整的
-- （下单/列表/详情/状态流转/买家自查全有）。同一件事没必要有两套表。
-- 配套的 u_s_inquiry_item 明细表已经删掉，见
-- migration/2026-09-06-drop-inquiry-item-and-orphans.sql。
--
-- 这 13 列**保留不删**，理由和 buyercode 那一列一样：它们全部可空、全部为 NULL，
-- 留着成本是零；而删列是不可逆的，且要在每个已部署的站上各做一次 ALTER。
-- 新库也照建，是为了让新站和老站的结构**逐列一致**——基线存在的意义就是这个，
-- 为了少 13 个空列而让两边分叉，得不偿失。
--
-- 单品询盘（详情页 InquiryForm）用的是 message 那一列，不受影响。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_inquiry` (
  `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`    varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】见文件末尾',
  `sitecode`     varchar(50)   NOT NULL COMMENT '来自哪个站点',
  `inquirytype`  varchar(20)   NOT NULL DEFAULT 'product' COMMENT '【RFQ 留位置】类型：product 单品询盘 / rfq 下单请求。恒为 product',
  `inquirycode`  varchar(50)   NOT NULL COMMENT '询盘单号，XP + Base10 流水',
  `inquiryref`   varchar(50)   DEFAULT NULL COMMENT '【RFQ 留位置】前台生成的对外单号，如 RFQ-2026-1043',
  `name`         varchar(100)  NOT NULL COMMENT '客户姓名',
  `email`        varchar(150)  NOT NULL COMMENT '客户邮箱，回复靠它',
  `company`      varchar(200)  DEFAULT NULL COMMENT '公司名',
  `phone`        varchar(50)   DEFAULT NULL COMMENT '电话 / WhatsApp',
  `goodscode`    varchar(50)   DEFAULT NULL COMMENT '意向商品编码，从详情页提交时带上',
  `producttitle` varchar(300)  DEFAULT NULL COMMENT '意向商品名称（客户看到的那个标题）',
  `combocode`    varchar(50)   DEFAULT NULL COMMENT '询盘针对的规格型号编码。⚠ 不是外键：那条型号可能已经被删了，历史询盘不受影响',
  `comboname`    varchar(200)  DEFAULT NULL COMMENT '型号名称快照（客户当时那个语言的那一份）。销售看询盘时要知道客户问的是哪一款，不能只有编码',
  `message`      varchar(2000) NOT NULL COMMENT '客户留言',
  `note`         varchar(2000) DEFAULT NULL COMMENT '【RFQ 留位置】买家补充说明。与 message 不混',
  `pageurl`      varchar(500)  DEFAULT NULL COMMENT '提交时所在页面，用来判断哪个页面带来的询盘',
  `clientip`     varchar(64)   DEFAULT NULL COMMENT '客户 IP',
  `useragent`    varchar(300)  DEFAULT NULL COMMENT '浏览器 UA',
  `lang`         varchar(20)   DEFAULT NULL COMMENT '【RFQ 留位置】提交时的站点语言（URL 前缀那个）',
  `currency`     varchar(10)   DEFAULT NULL COMMENT '【RFQ 留位置】提交时的站点币种快照',
  `shippingpref` varchar(20)   DEFAULT NULL COMMENT '【RFQ 留位置】运输偏好：sea/air/express/agent',
  `paymentpref`  varchar(20)   DEFAULT NULL COMMENT '【RFQ 留位置】付款偏好：tt/lc/paypal/other。站上不收款',
  `shipcountry`  varchar(100)  DEFAULT NULL COMMENT '【RFQ 留位置】收货国家/地区。⚠ 有索引',
  `shipline1`    varchar(200)  DEFAULT NULL COMMENT '【RFQ 留位置】收货地址第一行',
  `shipline2`    varchar(200)  DEFAULT NULL COMMENT '【RFQ 留位置】收货地址第二行',
  `shipcity`     varchar(100)  DEFAULT NULL COMMENT '【RFQ 留位置】城市',
  `shipregion`   varchar(100)  DEFAULT NULL COMMENT '【RFQ 留位置】州/省。很多国家没有，可空',
  `shippostal`   varchar(30)   DEFAULT NULL COMMENT '【RFQ 留位置】邮编',
  `status`       varchar(20)   NOT NULL DEFAULT '待处理' COMMENT '处理状态：待处理/已联系/已成交/无效',
  `buyercode`    varchar(50)   DEFAULT NULL COMMENT '【留位置】本站客户编码。站点客户管理一期不做，恒为 NULL。⚠ 不再指向 ERP 的 u_m_buyer',
  `handleuser`   varchar(50)   DEFAULT NULL COMMENT '处理人',
  `handletime`   datetime      DEFAULT NULL COMMENT '处理时间',
  `remark`       varchar(500)  DEFAULT NULL COMMENT '内部备注。⚠ 只有一条、改一次盖一次；要留「谁在什么时候说了什么」用 u_s_followup',
  `owner`        varchar(50)   DEFAULT NULL COMMENT '负责人。⚠ 和 handleuser 不是一回事：那个是「最后动过的人」，这个是「这条归谁」。没人动过的询盘 handleuser 为空，而「没人负责」和「没人动过」要分开看才知道是流程问题还是人手问题',
  `nextfollow`   date          DEFAULT NULL COMMENT '下次跟进日期。⚠ 只到天不到时分：销售定的是「下周三再问一次」，精确到分钟是假精度。跟进看板的「今天要跟」「已逾期」都看它',
  `createtime`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收到时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inquiry_code` (`shop_code`,`inquirycode`),
  KEY `idx_inquiry_list` (`shop_code`,`sitecode`,`status`,`createtime`),
  KEY `idx_inquiry_ip` (`clientip`,`createtime`) COMMENT '查垃圾询盘用',
  KEY `idx_inquiry_country` (`shop_code`,`shipcountry`,`createtime`) COMMENT '【RFQ 留位置】按收货国家统计询盘来源',
  KEY `idx_inquiry_ref` (`inquiryref`) COMMENT '【RFQ 留位置】客服拿买家手里的 RFQ 号反查'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点客户询盘';


-- ---------------------------------------------------------------------
-- 单号流水。
--
-- ERP 那边询盘单号和图片主键是走 Redis 发号的（ERP 的公共发号器）。搬过来之后
-- 那个发号器不在了，用这张表在本库里发——一个站的量级下，一条
-- `update ... set nextval = nextval + 1` 完全够用，而且它和业务数据在同一个库、
-- 同一个事务里，不会出现「Redis 清了导致单号从头开始」这种事。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_sequence` (
  `name`       varchar(50) NOT NULL COMMENT '序列名：inquiry / image / video / goods',
  `nextval`    bigint      NOT NULL DEFAULT 1 COMMENT '下一个可用值',
  `updatetime` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本库发号器';


-- ---------------------------------------------------------------------
-- 汇率。单行表，配合 u_s_siteinfo.currencies 做前台币种切换。
--
-- 后端每天从 open.er-api.com 拉一次（免费、无 key、166 币种、日更），
-- 整张表存成一列 JSON——永远整体读整体写，没有按币种查一行的场景，
-- 拆成 166 行只会多出一堆没人看的行和「这次比上次少一个币种」的同步问题。
--
-- ⚠ 换算只发生在展示层：结算页回到本币，u_s_order 落的一律是本币。
--   站上不收款，订单金额必须有稳定口径，否则汇率一变老订单就对不上账。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_rate` (
  `id`           tinyint       NOT NULL DEFAULT 1     COMMENT '恒为 1。和 u_s_siteinfo 一样，这张表只有一行',
  `base_code`    varchar(10)   NOT NULL DEFAULT 'USD' COMMENT '⚠ 汇率源返回值的基准，跟着 API 走，**不是站点本币**。站点本币在 u_s_siteinfo.currency，混淆会让换算方向整个反过来',
  `ratesjson`    mediumtext                           COMMENT '自动拉回来的完整汇率表，{"CNY":6.728426,"EUR":0.860364,...}，相对 base_code',
  `overridejson` varchar(1000)                        COMMENT '运营手工覆盖，同样相对 base_code。填了压过 ratesjson，逐币种生效。用途：给汇率加 buffer，或 API 挂掉时兜底',
  `fetchtime`    datetime                             COMMENT '最后一次**成功**拉取的时间。超 7 天管理台标红。拉取失败不更新它',
  `fetchstatus`  varchar(20)                          COMMENT '成功/失败',
  `fetcherror`   varchar(500)                         COMMENT '失败原因。免费 API 静悄悄挂掉是最可能的故障，页面上没有别的症状',
  `updateuser`   varchar(50)                          COMMENT '最后改覆盖值的人',
  `updatetime`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率表，本库只有一行';

-- ratesjson 留空是正常起点：后端启动后 30 秒拉第一次。拉到之前前台只有本币、
-- 不显示切换器——降级成没有这个功能时的样子，不报错。
INSERT IGNORE INTO `u_s_rate` (`id`, `base_code`) VALUES (1, 'USD');


-- ---------------------------------------------------------------------
-- 迁移台账。人工登记这个库跑过 sql/station/migration/ 下的哪几个脚本。
--
-- ⚠ 不是 Flyway：没有校验和、不阻止乱序、不自动执行任何东西，准确性靠人。
--   跑完脚本忘了登记，台账就是错的，而错的台账比没有台账更危险
--   ——下一个人会信它。宁可插一行写"不确定"，也别不插。
--
-- source 列区分「执行」（真跑过）和「回填」（只是按结构比对确认已生效）。
-- 两者可信度不同：结构对得上，证明不了脚本里的数据清洗部分跑过没有。
--
-- 新建的库跑完本脚本后这张表是空的——这是对的，01 建的是最终结构，
-- 没有"跑过哪些增量"这回事。只有从旧版本升上来的库才该有行。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_schema_migration` (
  `filename`  varchar(200) NOT NULL COMMENT '脚本文件名，和 sql/station/migration/ 下一字不差。用它做主键：重复登记应该失败，而不是产生第二行',
  `appliedat` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间。source=回填时这是回填时间，不是脚本实际执行的时间',
  `appliedby` varchar(50)           DEFAULT NULL COMMENT '谁跑的',
  `source`    varchar(20)  NOT NULL DEFAULT '执行' COMMENT '⚠ 这张表最重要的一列。执行=确实跑过；回填=无执行记录，只是按结构比对确认改动已生效。别混着看',
  `note`      varchar(500)          DEFAULT NULL COMMENT '备注。回填的要写清依据',
  PRIMARY KEY (`filename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='迁移台账，人工登记';


-- =====================================================================
-- 三、站点订单与客户（2026-09 新增）
-- =====================================================================
--
-- ⚠ boundary §11 把「订单管理」「客户管理」列在「一期不做、但要留位置」里，
--   而且明说「不建表」。这两张表是那条规则的**例外**，例外的依据是：
--   §11 不建表的理由是「这五个模块的需求现在只有名字」，而这两个已经不是了——
--   前台的询价流程（station-site/app/lib/shop.ts）把订单模型定完了，
--   u_s_inquiry.buyercode 那一列建库时就为客户档案留好了钩子。
--
--   博客 / 营销 / 站长工具三个仍然只有名字，仍然不建表。
--
--   完整理由、以及「为什么时间轴不建第三张表」「为什么客户按邮箱去重」，
--   见 sql/station/migration/2026-09-04-order-and-buyer.sql 的文件头。
-- 3.1 站点订单
--
-- ⚠ 和 ERP 的订单**没有任何隶属关系**（boundary 要点 4）。这里没有一列指向
--   sdsorder 的任何东西，也没有「同步状态」列——一期的同步方式是「像拼多多一样
--   人工在 ERP 里录一张」，那是流程，不是字段（§8：不做，也不预埋接口）。
--
-- 状态词和前台 station-site/app/lib/shop.ts 的 OrderStatus 一一对应：
--     submitted → 待确认   confirmed → 已确认
--     shipped   → 已发货   delivered → 已完成   cancelled → 已取消
-- 库里存中文，前台那边按站点语言翻——库里存英文的话，管理台要为了显示再翻一遍，
-- 而管理台只有中文界面。

CREATE TABLE IF NOT EXISTS `u_s_order` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`     varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】恒为建站时那个商户码，新代码不得读它',
  `sitecode`      varchar(50)   NOT NULL COMMENT '来自哪个站点，恒等于 u_s_siteinfo.sitecode',
  `ordercode`     varchar(50)   NOT NULL COMMENT '订单号，DD + Base10 流水。由后端发号，不接受前端传',

  `status`        varchar(20)   NOT NULL DEFAULT '待确认' COMMENT '待确认/已确认/已发货/已完成/已取消',
  `source`        varchar(20)   NOT NULL DEFAULT 'site' COMMENT '来源：site 前台自助提交 / console 管理台手工录',

  -- ---- 金额 ----
  `currency`      varchar(10)   NOT NULL DEFAULT 'USD' COMMENT '币种，下单时站点的币种',
  `subtotal`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '有标价那部分的小计。⚠ 不含运费、不含面议商品',
  `inquireonly`   int           NOT NULL DEFAULT 0 COMMENT '价格面议的行数。>0 时这张单的 subtotal 不是全部金额',

  -- ---- 联系人。⚠ 不 join 客户表，存快照 ----
  `contactname`   varchar(100)  NOT NULL COMMENT '联系人',
  `email`         varchar(150)  NOT NULL COMMENT '邮箱。回复靠它，也是关联客户档案的键',
  `company`       varchar(200)  DEFAULT NULL COMMENT '公司名',
  `phone`         varchar(50)   DEFAULT NULL COMMENT '电话 / WhatsApp',

  -- ---- 目的地 ----
  `addrline1`     varchar(200)  DEFAULT NULL COMMENT '地址第一行',
  `addrline2`     varchar(200)  DEFAULT NULL COMMENT '地址第二行',
  `city`          varchar(80)   DEFAULT NULL COMMENT '城市',
  `region`        varchar(80)   DEFAULT NULL COMMENT '州 / 省',
  `postal`        varchar(30)   DEFAULT NULL COMMENT '邮编',
  `country`       varchar(80)   NOT NULL COMMENT '国家。唯一必填的地址字段——估运费最少要它',

  -- ---- 买家的倾向。⚠ 只是倾向，不是定下来的条款 ----
  `shippingpref`  varchar(20)   DEFAULT NULL COMMENT '运输倾向：sea/air/express/agent，见 shop.ts SHIPPING_PREFS',
  `paymentpref`   varchar(20)   DEFAULT NULL COMMENT '付款倾向：tt/lc/paypal/other，见 shop.ts PAYMENT_PREFS',
  `buyernote`     varchar(800)  DEFAULT NULL COMMENT '买家补充说明（目标价、交期、包装、认证……）',
  `wizardjson`    mediumtext    NULL COMMENT '引导式下单的选配快照：[{group,field,value,code,raw,groupZh,fieldZh,valueZh}]。普通购物车下的单为 NULL（2026-09-28-order-wizard.sql）',

  -- ---- 四段时间轴。见文件头「不建第三张表」那一条 ----
  `confirmtime`   datetime      DEFAULT NULL COMMENT '报价/订单确认时间。为空表示还没确认',
  `shiptime`      datetime      DEFAULT NULL COMMENT '发货时间',
  `finishtime`    datetime      DEFAULT NULL COMMENT '完成（送达）时间',

  `buyercode`     varchar(50)   DEFAULT NULL COMMENT '本站客户编码，指向 u_s_buyer.buyercode。下单时按邮箱自动挂上',
  `clientip`      varchar(64)   DEFAULT NULL COMMENT '提交时的客户端 IP，查刷单用',
  `handleuser`    varchar(50)   DEFAULT NULL COMMENT '最后处理人',
  `handletime`    datetime      DEFAULT NULL COMMENT '最后处理时间',
  `remark`        varchar(500)  DEFAULT NULL COMMENT '内部备注，买家看不到',
  `createtime`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updatetime`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_code` (`shop_code`,`ordercode`),
  KEY `idx_order_list` (`shop_code`,`sitecode`,`status`,`createtime`),
  KEY `idx_order_buyer` (`shop_code`,`buyercode`,`createtime`) COMMENT '客户详情页要列出他的订单',
  KEY `idx_order_email` (`email`) COMMENT '按邮箱找单：客户来信问进度时用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点订单。⚠ 与 ERP 订单无任何隶属关系（boundary 要点 4）';


-- 3.2 订单行
--
-- ⚠ 每一行都是**下单当时的商品快照**，不 join u_s_sitegoods。
--   商品改名、调价、下架都不影响已下的单。理由见文件头第 2 条。

CREATE TABLE IF NOT EXISTS `u_s_orderitem` (
  `id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`  varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `ordercode`  varchar(50)   NOT NULL COMMENT '所属订单号',
  `goodscode`  varchar(50)   NOT NULL COMMENT '商品编码（下单当时）',
  `goodsname`  varchar(200)  DEFAULT NULL COMMENT '商品名快照',
  `combocode`  varchar(50)   DEFAULT NULL COMMENT '选中的规格型号编码（下单当时）。⚠ 不是外键：那条型号可能已经被删了，历史订单不受影响',
  `comboname`  varchar(200)  DEFAULT NULL COMMENT '型号名称快照。商家改名不影响历史订单',
  `subtitle`   varchar(200)  DEFAULT NULL COMMENT '名称下那行小字，一般是分类',
  `imgurl`     varchar(500)  DEFAULT NULL COMMENT '主图快照',
  `price`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '单价快照。⚠ 0 表示价格面议，不是免费',
  `currency`   varchar(10)   DEFAULT NULL COMMENT '币种快照',
  `qty`        decimal(18,4) NOT NULL DEFAULT '1.0000' COMMENT '数量。外贸按米/千克下单，所以是小数不是整数',
  `unit`       varchar(20)   DEFAULT NULL COMMENT '单位',
  `linetotal`  decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '行小计 = price × qty。面议行为 0',
  `sortnum`    int           NOT NULL DEFAULT 0 COMMENT '行序，保持买家清单里的顺序',
  PRIMARY KEY (`id`),
  KEY `idx_orderitem_order` (`shop_code`,`ordercode`,`sortnum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点订单明细（下单当时的商品快照）';


-- 3.3 引导式下单的向导定义（2026-09-28，docs/superpowers/specs/2026-09-28-order-wizard-design.md）
--
-- 一个站最多一行。空表 = 向导没开，前台入口不出现。
CREATE TABLE IF NOT EXISTS `u_s_orderwizard` (
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码。一个部署一个站，这张表最多一行',
  `enabled`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '前台开关。0 = 顶栏入口不出现、/configure 显示未开放、带向导的下单被拒',
  `schemajson` mediumtext   NULL COMMENT '向导定义 JSON：{version,title,lead,groups:[{code,title,sub,showIf,fields:[{code,type,options:[…]}]}]}。结构见设计文档；后端 OrderWizardService.validateSchema 是唯一的格式校验',
  `updateuser` varchar(50)  NULL,
  `updatetime` datetime     NULL,
  PRIMARY KEY (`sitecode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引导式下单的向导定义';


-- 3.3 站点客户
--
-- ⚠ 这是**站点自己的**买家档案，和 ERP 的销售客户（sdsshop.u_m_buyer）没有任何关系
--   （boundary §6c）。没有一列指向那边，也没有「转 ERP 客户」这个动作——
--   那个动作是一条互通，§10.1 里已经删掉了。
--
-- 档案是**长出来的**，不是建出来的：谁发了询盘或者下了单，就按邮箱为他落一行。
-- 管理台里能改的只有备注和标签，联系方式跟着最近一次提交更新。
-- 这和 u_s_user「谁从 ERP 跳进来就落一行」是同一个思路：没有「新建」按钮，
-- 就没有「建了一个谁都用不上的档案」这种状态。
--
-- ---------------------------------------------------------------------
-- ⚠ 这张表也是**前台买家登录**用的账号表。别和 u_s_user 搞混
-- ---------------------------------------------------------------------
--
--   | 表        | 谁                    | 怎么进来                       | 存密码吗 |
--   | --------- | --------------------- | ------------------------------ | -------- |
--   | u_s_user  | 管理台操作员（自己人）| 只能从 ERP 点「进入管理」免密进 | **不存** |
--   | u_s_buyer | 前台买家（客户）      | 前台自助注册、邮箱验证、登录    | 存       |
--
-- 两拨人两张表，**不要合并**：u_s_user.rolecode 是管理台权限的来源，
-- 买家和操作员共用一张表的话，某个查询漏一个 where 就是「买家拿到管理台权限」。
--
-- 凭据为什么加在这张已有的表上：买家的身份本来就是邮箱，而这张表已经按邮箱唯一了。
-- 于是先发过询盘、后来才注册的人，注册时认领的就是他自己那一行——
-- 登录进去立刻看得到之前的询盘和订单。完整取舍见
-- sql/station/migration/2026-09-04-buyer-login.sql 的文件头。

CREATE TABLE IF NOT EXISTS `u_s_buyer` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`    varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`     varchar(50)  NOT NULL COMMENT '来自哪个站点',
  `buyercode`    varchar(50)  NOT NULL COMMENT '客户编码，KH + Base10 流水',

  `email`        varchar(150) NOT NULL COMMENT '邮箱。⚠ 客户的身份就是它，见下面的唯一键',

  -- ---- 前台登录用的凭据。⚠ 这几列是**买家**的，和 u_s_user（管理台操作员）无关 ----
  `passwordhash`  varchar(200) DEFAULT NULL COMMENT 'BCrypt 密文。⚠ 绝不存明文。为 NULL = 有档案没账号（发过询盘但没注册），是常态不是缺失',
  `emailverified` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '邮箱验证过没有。0 时不许登录——注册时填的邮箱可能压根不是他的',
  `verifytime`    datetime     DEFAULT NULL COMMENT '邮箱验证通过的时间',
  `status`        varchar(20)  NOT NULL DEFAULT '正常' COMMENT '正常/停用。停用后登不进来，但档案、询盘、订单都留着——运营还要看',

  `name`         varchar(100) DEFAULT NULL COMMENT '联系人（最近一次提交时填的）',
  `company`      varchar(200) DEFAULT NULL COMMENT '公司名（同上）',
  `phone`        varchar(50)  DEFAULT NULL COMMENT '电话 / WhatsApp（同上）',
  `country`      varchar(80)  DEFAULT NULL COMMENT '国家。只有下过单才有——询盘表单不收这一项',

  -- 头像。两种取值：`preset:<色块id>`（挑了色块），或浏览器裁成正方形、缩到 256px、
  -- JPEG 编码之后的 data URL（传了图）。十几到几十 KB。
  -- ⚠ 这一列**不进** SiteBuyerMapper.xml 的 columns / authColumns：那两份清单用在
  --   管理台的客户列表（一页 20 行）和 requireBuyer（每个要身份的请求跑一次）上，
  --   挂上几十 KB 的 base64 等于把那两条路径白白撑重。取它的只有 selectAvatar 一条。
  `avatar`       mediumtext   DEFAULT NULL COMMENT '头像：preset:<色块id> 或 256px 的 JPEG data URL。⚠ 不进 mapper 的列清单',

  `inquirycount` int          NOT NULL DEFAULT 0 COMMENT '累计询盘数',
  `ordercount`   int          NOT NULL DEFAULT 0 COMMENT '累计下单数',
  `lastlogintime` datetime    DEFAULT NULL COMMENT '最后登录时间。⚠ 和 lastactive 不是一回事：那一列是最近一次询盘或下单',
  `lastloginip`  varchar(64)  DEFAULT NULL COMMENT '最后登录 IP。只用于排查，不参与任何判定（这个头是客户端可伪造的）',
  `logincount`   int          NOT NULL DEFAULT 0 COMMENT '累计登录次数。只在成功时加——失败也加的话，它就变成了「有人在试密码」的计数',
  `lastactive`   datetime     DEFAULT NULL COMMENT '最近一次询盘或下单的时间。客户列表默认按它排',

  `tag`          varchar(50)  DEFAULT NULL COMMENT '运营自己打的标签，如「重点」「已寄样」。一期就一个自由文本，不做标签表',
  `remark`       varchar(500) DEFAULT NULL COMMENT '内部备注',
  `createtime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现的时间',
  `updatetime`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_code` (`shop_code`,`buyercode`),
  -- ⚠ 客户按邮箱去重。见文件头第 3 条：按人名去重会把两个同名的人合成一个
  UNIQUE KEY `uk_buyer_email` (`shop_code`,`email`),
  KEY `idx_buyer_active` (`shop_code`,`sitecode`,`lastactive`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点客户档案。⚠ 与 ERP 的 u_m_buyer 无任何关系（boundary §6c）';


-- 3.4 买家收货地址簿
--
-- 外贸买家一个人对多个收货地（工厂、货代仓库、展会现场、客户指定的第三方港口）是常态，
-- 所以是一张表，不是往 u_s_buyer 上加几列。
--
-- ⚠ 列名和上面 u_s_order 的目的地那一组**一字不差**（contactname / phone /
--   addrline1 / addrline2 / city / region / postal / country）。地址簿里选一条 →
--   填进结账表单 → 落进 u_s_order 那几列，名字对齐就不用写映射层。改这里请同时看那边。
--
-- ⚠ 订单上的地址仍然是**下单当时的快照**，和这张表没有外键、也不 join：
--   买家事后改地址簿，历史订单的收货地不能跟着变（货已经按旧地址发出去了）。
--   同 u_s_orderitem 存商品快照那一条。
--
-- ⚠ 这张表对外用自增 id，不发业务码。代价是 id 连号可猜，所以
--   **每一条 SQL 的 where 里都必须带 buyercode**，少一处就是「改个数字读到别人的地址」。
--   完整取舍见 sql/station/migration/2026-09-08-buyer-address.sql 的文件头。

CREATE TABLE IF NOT EXISTS `u_s_buyeraddr` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键。⚠ 它就是对外的地址 id',
  `shop_code`   varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`    varchar(50)  NOT NULL COMMENT '来自哪个站点',
  `buyercode`   varchar(50)  NOT NULL COMMENT '属于谁，指向 u_s_buyer.buyercode。⚠ 每一条查询的 where 里都必须带它',

  `label`       varchar(50)           DEFAULT NULL COMMENT '买家给这条地址起的名字，如 Warehouse / Head office。选填',

  `contactname` varchar(100) NOT NULL COMMENT '收件人',
  `phone`       varchar(50)           DEFAULT NULL COMMENT '收件电话 / WhatsApp。选填——强制填会挡住只想问价的人',

  `addrline1`   varchar(200)          DEFAULT NULL COMMENT '地址第一行',
  `addrline2`   varchar(200)          DEFAULT NULL COMMENT '地址第二行',
  `city`        varchar(80)           DEFAULT NULL COMMENT '城市',
  `region`      varchar(80)           DEFAULT NULL COMMENT '州 / 省',
  `postal`      varchar(30)           DEFAULT NULL COMMENT '邮编',
  `country`     varchar(80)  NOT NULL COMMENT '国家。唯一必填的地址字段，和 u_s_order 一致',

  `isdefault`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是不是默认地址。⚠ 一人一条靠服务层事务保证（先清零再置一），不靠唯一索引：MySQL 没有部分唯一索引',

  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_buyeraddr_owner` (`shop_code`,`buyercode`,`isdefault`,`id`) COMMENT '列地址簿：默认的排最前'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='买家收货地址簿。⚠ 订单上的地址是下单当时的快照，不 join 这张表';


-- ---------------------------------------------------------------------
-- 在线咨询：一个访客一条线程 + 线程里的每一句
-- ---------------------------------------------------------------------
--
-- 匿名就能聊（CHAT_VISITOR 这个 HttpOnly Cookie 认线程），邮箱选填。
-- 这和询盘、下单「不挡登录」是同一条立场。留了邮箱就挂到上面那张 u_s_buyer 上。
--
-- ⚠ 这里没有 unreadcount 那样的列：未读数是**现算**的，只存两个已读游标。
--   理由和 u_s_buyer 的 inquirycount 那条一样——累加的计数会漂，漏减一次就永远错下去。
--
-- 完整说明见 docs/设计稿/2026-09-04-在线咨询.md

CREATE TABLE IF NOT EXISTS `u_s_chat` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`      varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`       varchar(50)  NOT NULL COMMENT '来自哪个站点',
  `chatcode`       varchar(50)  NOT NULL COMMENT '会话编码，LT + Base10 流水',

  -- ⚠ 匿名会话的**唯一凭据**，拿到就能读走整条对话。只在 HttpOnly Cookie 里流动，
  --   绝不进 URL——query 参数会原样进 nginx 的 access log
  `visitortoken`   varchar(64)  NOT NULL COMMENT '访客令牌，32 字节 SecureRandom 的 base64url。就是 CHAT_VISITOR Cookie 里那个值',

  `buyercode`      varchar(50)  DEFAULT NULL COMMENT '挂上的客户档案，指向 u_s_buyer.buyercode。留了邮箱才有',
  `email`          varchar(150) DEFAULT NULL COMMENT '访客留的邮箱（选填）。留了才能邮件回访',
  `name`           varchar(100) DEFAULT NULL COMMENT '访客填的称呼（选填）',
  `lang`           varchar(10)  DEFAULT NULL COMMENT '访客用哪种语言开的口。前台是 8 语言站，运营得知道用哪国话回',

  `pageurl`        varchar(500) DEFAULT NULL COMMENT '他在哪个页面开的口。运营一眼看出他在看哪个商品',
  `clientip`       varchar(64)  DEFAULT NULL COMMENT '排查用，和 u_s_inquiry 一致',
  `useragent`      varchar(500) DEFAULT NULL COMMENT '同上',

  `lastmessage`    varchar(500) DEFAULT NULL COMMENT '最后一条的摘要。会话列表直接显示它，不用 join 消息表',
  `lastmessagetime` datetime    DEFAULT NULL COMMENT '最后一条的时间。列表默认按它倒排',

  `consolereadid`  bigint       NOT NULL DEFAULT 0 COMMENT '运营读到的最大 message id。未读数靠它现算',
  `buyerreadid`    bigint       NOT NULL DEFAULT 0 COMMENT '买家读到的最大 message id。同上',

  `createtime`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '第一条消息发出的时间',
  `updatetime`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_code` (`shop_code`,`chatcode`),
  -- 令牌全局唯一：认线程认的就是它，撞一次就是两个人共用一条对话
  UNIQUE KEY `uk_chat_visitor` (`visitortoken`),
  KEY `idx_chat_active` (`shop_code`,`sitecode`,`lastmessagetime`),
  KEY `idx_chat_buyer` (`buyercode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线咨询会话。一个访客一行';


-- ⚠ 这张表上**没有 sitecode**：它由 chatid 决定。冗余一份就多一个会不一致的地方，
--   而这张表永远是从某一条 chat 出发去查的，没有「跨会话按站点捞消息」这种查询。
CREATE TABLE IF NOT EXISTS `u_s_chatmessage` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键。⚠ 它同时是已读游标的取值，所以不能改成 UUID 之类',
  `shop_code`   varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `chatid`      bigint       NOT NULL COMMENT '属于哪条会话，指向 u_s_chat.id',

  `sender`      varchar(20)  NOT NULL COMMENT '买家 / 运营',
  `senderuser`  varchar(50)  DEFAULT NULL COMMENT '哪个操作员回的（u_s_user.username）。买家发的为 NULL',

  `content`     varchar(2000) NOT NULL COMMENT '纯文本。⚠ 存原文不做 HTML 过滤——过滤会把买家问的「<M8 螺丝」吃掉。两侧都用文本插值渲染，不解释 HTML',
  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发出时间',

  PRIMARY KEY (`id`),
  -- 增量拉取（where chatid = ? and id > ?）和未读现算走的都是这条
  KEY `idx_msg_chat` (`chatid`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线咨询消息';


-- ---------------------------------------------------------------------
-- 前台浏览量。两张表，明细 + 日聚合。
--
-- 完整的设计说明（为什么两张、为什么按东八区切天、为什么 IP 要截断、
-- 为什么爬虫行不删）在 migration/2026-09-08-pageview.sql 的文件头里，
-- 改这两张表之前先读它——那些决定都有理由，不是随手写的。
--
-- ⚠ 这两张表**没有 shop_code**。老表都带，新表不带：文件末尾那段写着
--   「新代码不得读它、不得按它过滤」，既然不读就不建，免得下一个人
--   以为它有意义然后在 WHERE 里带上它。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_pageview` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `sitecode`   varchar(50) NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',

  -- ⚠ 这一列是 UV 的**唯一**依据。来自 SITE_VISITOR Cookie 里一个 16 字节随机串，
  --   不含个人信息、和买家账号无关。清了 Cookie 或换浏览器就算新访客——
  --   这是这种统计方式的固有误差，别试图用 IP+UA 去「修正」它
  `visitorid`  varchar(64) NOT NULL COMMENT '访客标识，SITE_VISITOR Cookie 里的随机串。算 UV 靠它',
  `sessionid`  varchar(64) DEFAULT NULL COMMENT '会话标识，SITE_SESSION Cookie，30 分钟不活动就换一个',

  `pagetype`   varchar(20) NOT NULL COMMENT '页面类型：home/list/detail/cart/checkout/account/other。⚠ 服务端按白名单校验，不认识的一律记 other',

  -- detail 之外的页面这一列是**空串不是 NULL**：它进了 u_s_pv_daily 的主键，
  -- 而 NULL 进不了主键。两张表用同一个约定，免得对不上
  `goodscode`  varchar(50) NOT NULL DEFAULT '' COMMENT '看的哪个商品，pagetype=detail 才有值，其余为空串。⚠ 服务端校验过它在 u_s_sitegoods 里真的存在',

  `pagepath`   varchar(300) NOT NULL COMMENT '页面路径，带语言前缀，如 /ja/products/G001。不存 query string',
  `lang`       varchar(10)  DEFAULT NULL COMMENT '访客在看哪个语种的页面',
  `referrer`   varchar(500) DEFAULT NULL COMMENT '来源页。⚠ 比 pv 值钱：它区分「搜索进来的」和「直接输网址的」，是判断 SEO 有没有起作用的唯一依据',

  `clientip`   varchar(64)  DEFAULT NULL COMMENT '⚠ 已截断，不是完整 IP。IPv4 去掉最后一段、IPv6 只留前 48 位。和 u_s_chat.clientip 不同，理由见 migration 文件头',
  `country`    varchar(2)   DEFAULT NULL COMMENT '国家二字码。⚠ 现在恒为 NULL——没接 IP 归属库。留列不留空谈：将来接了直接回填',
  `useragent`  varchar(500) DEFAULT NULL COMMENT '浏览器标识。留着是为了事后能重新判定爬虫和设备（判定规则会改，原始 UA 不会）',

  -- 这两列由 UserAgentParser 从上面那个 useragent 解析出来，**不采集新数据**。
  -- 为什么落列而不是查询时解析：UA 判定是一串顺序敏感的规则，用 SQL 的
  -- CASE WHEN LIKE 堆出来顺序写错不报错、只是数字偏，而且每次查报表都要全表匹配。
  -- ⚠ 这两列**不进 u_s_pv_daily**（同 referrer，见那张表的规矩三），
  --   所以设备分布只能查保留期以内——够了，这是个季度级才会变的结构性问题。
  `device`     varchar(10)  DEFAULT NULL COMMENT '设备类型：mobile/tablet/desktop',
  `browser`    varchar(20)  DEFAULT NULL COMMENT '浏览器：Chrome/Safari/Edge/Firefox/Opera/Samsung/IE/Other。⚠ 判定顺序敏感，见 UserAgentParser',

  -- ⚠ 判定为爬虫的行**照样入库**，只在统计时排除。删掉的话，将来发现规则
  --   误杀了某个真实浏览器，那些数据就永远找不回来了
  `isbot`      tinyint      NOT NULL DEFAULT 0 COMMENT '1=判定为爬虫。不删，只在统计时排除',

  `createtime` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '浏览时间。⚠ 精度到毫秒：同一个人连点两下的间隔小于 1 秒，秒精度会把它们压成同一时刻',

  PRIMARY KEY (`id`),
  KEY `idx_pv_time` (`sitecode`,`createtime`) COMMENT '按时间范围捞，今日 UV 现算走这条',
  KEY `idx_pv_goods` (`sitecode`,`goodscode`,`createtime`) COMMENT '这个商品最近 N 天被谁看过',
  KEY `idx_pv_visitor` (`visitorid`,`createtime`) COMMENT '单个访客的足迹，以及 90 天清理的批量删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台浏览明细。保留 90 天，由 PageViewCleaner 清理';


CREATE TABLE IF NOT EXISTS `u_s_pv_daily` (
  `sitecode`  varchar(50) NOT NULL COMMENT '站点编码',

  -- ⚠ 「一天」按 **Asia/Shanghai** 切，不按 UTC。访客在境外，两种切法都有道理，
  --   但必须只选一个并且写下来——落库用一种、查询用另一种，跨零点那几个小时
  --   的数字会对不上，而且极难发现。选东八区：看这个数字的是国内的运营。
  `statdate`  date        NOT NULL COMMENT '统计日期，按 Asia/Shanghai 切天。见上面那段注释',

  -- ⚠⚠ 特殊值 `_all`：它不是一种页面类型，是「当天全站合计」。
  --   每次上报往这张表写两行——一行 ('_all',''), 一行 (真实类型, 商品编码或空串)。
  --   查趋势只读 _all；查分布和排行必须排除 _all，**否则总数正好翻倍**。
  `pagetype`  varchar(20) NOT NULL COMMENT '页面类型，同 u_s_pageview.pagetype。⚠ 特殊值 _all = 当天全站合计，不是一种类型；查分布时必须排除它，否则总数翻倍',
  `goodscode` varchar(50) NOT NULL DEFAULT '' COMMENT '商品编码，非详情页和 _all 行为空串。⚠ 不能用 NULL——它在主键里',

  `pv`        int         NOT NULL DEFAULT 0 COMMENT '浏览次数，含爬虫。每次上报同步 +1',
  `pvhuman`   int         NOT NULL DEFAULT 0 COMMENT '排除爬虫之后的浏览次数。⚠ 看报表要看这一列，pv 只用来估爬虫占比',

  -- ⚠ **今天这一行的 uv 恒为 0**，直到次日 01:50 由 PageViewMaintenance 回填。
  --   UV 是 COUNT(DISTINCT visitorid)，没法像 pv 那样每来一次 +1。
  --   所以：今天的 UV 由管理台从 u_s_pageview 现算，历史某天读这一列。
  --
  -- ⚠⚠ **uv 不能相加，任何方向都不行。** 同一个访客在 home 行和 detail 行里
  --   各算一次，两行相加就算了两遍；跨天同理。一段时间的 uv 只能从明细现算。
  `uv`        int         NOT NULL DEFAULT 0 COMMENT '独立访客数，排除爬虫。⚠ 两条铁律：次日凌晨才回填（当天恒为 0）；且**不能相加**，跨行跨天都不行',

  `updatetime` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后累加时间',

  -- 主键就是四个维度：upsert 靠它命中，也就不需要额外的唯一索引
  PRIMARY KEY (`sitecode`,`statdate`,`pagetype`,`goodscode`),
  KEY `idx_pvdaily_date` (`sitecode`,`statdate`) COMMENT '趋势图（最近 N 天按天汇总）走这条',
  KEY `idx_pvdaily_goods` (`sitecode`,`goodscode`,`statdate`) COMMENT '商品排行（最近 30 天按商品汇总）走这条'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台浏览日聚合。永不删除，是 90 天之后唯一剩下的历史';


-- ---------------------------------------------------------------------
-- IP 段到国家的离线对照表。给 u_s_pageview.country 用。
--
-- ⚠ 这张表**只有结构、没有数据**。数据是 71.7 万行 / 38 MB，不进 git——
--   建完库还要单独导一次，方法见 migration/2026-09-09-ip-country.sql 的文件末尾。
--   **不导的话国家统计一直是空的**，那不是坏了，是还没导。这一条必须知道，
--   否则会以为「功能接好了怎么没数」。
--
-- ⚠ 查询必须先按 ipver 过滤：IPv4 存 4 字节、IPv6 存 16 字节，varbinary 比较是
--   逐字节的，两种长度混着比大小毫无意义、而且不报错。
--
-- ⚠ 查到候选行之后还要判 ipend >= 目标：IP 段之间有空隙（未分配的地址），
--   只取「最近的一个起点」会把空隙里的地址错误地归给前一段。
--
-- 数据源 DB-IP country-lite，CC-BY 许可——将来若把国家统计放进**对外**页面，
-- 要在那个页面署名「IP Geolocation by DB-IP」。现在只在管理台内部看，还不需要。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_ip_country` (
  `ipver`   tinyint       NOT NULL COMMENT 'IP 版本：4 或 6。⚠ 查询必须先按它过滤，见上面注释',
  `ipstart` varbinary(16) NOT NULL COMMENT '段起始，INET6_ATON 的字节形式。IPv4 是 4 字节，IPv6 是 16 字节',
  `ipend`   varbinary(16) NOT NULL COMMENT '段结束，含。⚠ 查到候选行后还要判它——段之间有空隙',
  `country` char(2)       NOT NULL COMMENT '国家二字码（ISO 3166-1 alpha-2）。ZZ = 未知/保留段',
  PRIMARY KEY (`ipver`,`ipstart`),
  KEY `idx_ipc_end` (`ipver`,`ipend`) COMMENT '备用：反向核对某个段时用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP 段到国家的离线对照表。数据不进 git，单独导入';

-- ---------------------------------------------------------------------
-- 跟进记录。一条询盘可以有很多条。
--
-- §11 里「营销管理」只有一个名字。落到这个站上，营销最值钱的一块不是优惠券
-- 也不是群发邮件——**是「询盘来了没人回」**。这是个 B2B 询盘站：前台不收款、
-- 价格 0 表示面议、成交靠人跟出来的报价单。漏掉一条询盘 = 漏掉一单，
-- 而漏掉是静悄悄的。
--
-- ⚠ 为什么不复用 u_s_inquiry.remark：那是**一个字段**，改一次盖一次。
--   跟进最需要的恰恰是历史——「上次他说预算不够」「三周后再问一次」，
--   这些话的价值在于时间顺序，压成一段文本就没了。
--
-- ⚠ 这张表**只记人写的动作**，不记系统事件（状态变更、邮件送达）。
--   混在一起的话真正的对话会被自动流水淹掉，而它存在的意义就是让人
--   一眼看完这条线索的来龙去脉。系统事件要记的话另起一张表。
--
-- ⚠ 不加新权限：跟进记录就是询盘数据，复用 site:inquiry:list / edit。
--   单开一套 site:followup:* 会造出「能看跟进看板、却点不开背后的询盘」
--   这种没用的中间状态。
--
-- 迁移脚本：sql/station/migration/2026-09-09-followup.sql
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_followup` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `targettype` varchar(20)  NOT NULL DEFAULT 'inquiry' COMMENT '跟进对象类型。⚠ 目前只有 inquiry 一种，留列是给订单跟进预备的，别提前写别的值——没有代码读得懂',
  `targetcode` varchar(50)  NOT NULL COMMENT '对象编码。targettype=inquiry 时是 u_s_inquiry.inquirycode',
  `action`     varchar(20)  NOT NULL DEFAULT '记录' COMMENT '跟进方式：记录/邮件/电话/whatsapp/会面。只是给人看的分类，不驱动任何逻辑',
  `content`    varchar(2000) NOT NULL COMMENT '跟进内容。⚠ 纯文本，前台永不显示（内部记录），所以不过富文本白名单',
  `createuser` varchar(50)  DEFAULT NULL COMMENT '记录人，u_s_user.username',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间。⚠ 「多久没人管」算的就是它的最大值',
  PRIMARY KEY (`id`),
  KEY `idx_followup_target` (`sitecode`,`targettype`,`targetcode`,`createtime`) COMMENT '查一条询盘的跟进历史，以及算最后跟进时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跟进记录，只记人写的动作';



-- =====================================================================
-- 三之二、博客（2026-09-08 新增）
-- =====================================================================
--
-- §11 原来把「博客管理」列在「一期不做」里。做出来的理由：这个站的 SEO 价值目前
-- 全压在商品详情页上，而商品页能覆盖的关键词就是商品名，覆盖不到
-- 「how to choose silicone tubing」这类真正带来询盘的长尾词。
--
-- 迁移脚本：sql/station/migration/2026-09-08-blog.sql（含逐列的理由）
--
-- ⚠⚠ 博客和商品在「缺译文」这件事上**行为故意相反**：
--   商品缺某语言文案会回退默认语言（宁可标题是英文，也不能让日语站少一个能下单的商品）；
--   文章缺某语言标题则**那个语言版本直接不存在**（列表里没有、详情 404）。
--   一篇英文长文摆在日语站上对读者没价值，而且会被 Google 判成
--   「hreflang 说是日语、内容是英文」——比少一篇文章更伤。
--   这条规则在 selectPublic*、sitemap、hreflang 三处一起生效，缺一处就漏。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 文章主表。
--
-- ⚠ 两个标识：postcode 是内部的（发号器给，语言表外键 + 管理台增删改用），
--   slug 是对外的 URL 那一段。博客不像商品——商品 URL 里放编码可以，
--   买家搜的是商品名；博客整个存在的理由就是自然搜索，URL 里的关键词是排名因素。
--
-- ⚠ slug 只有一个，不分语言。语言靠已有的路径前缀区分（/ja/blog/xxx）。
--   分语言的 slug 对 SEO 更好一点，但一篇文章就有了八个身份，
--   改一个要考虑另外七个的重定向，而且两种语言撞同一个 slug 时没有正确答案。
--
-- ⚠ 没有 shop_code：同 u_s_pageview 的决定，见本文件末尾那段说明。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_post` (
  `sitecode`      varchar(50)  NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `postcode`      varchar(50)  NOT NULL COMMENT '内部编码，CodeGenerator 发号（BLOG 前缀）。⚠ 永不出现在前台 URL 里，那是 slug 的事',
  `slug`          varchar(160) NOT NULL COMMENT '公开 URL 那一段，只允许小写字母、数字和连字符。⚠ 改已发布文章的 slug = 换地址，老地址 404、排名和外链一起丢',
  `category`      varchar(100) DEFAULT NULL COMMENT '博客分类（英文）。筛选按钮由它去重现算，不另建分类表——同 u_s_sitegoods.sitecategory',
  `coverimg`      varchar(500) DEFAULT NULL COMMENT '封面图完整 URL，来自文件资源库。也是 og:image',
  `author`        varchar(100) DEFAULT NULL COMMENT '署名。留空则前台不显示作者行（不要兜底成公司名，那看着像没填）',
  `publishstatus` varchar(20)  NOT NULL DEFAULT '草稿' COMMENT '草稿/已发布/已下架。只有「已发布」对外可见',
  `publishtime`   datetime     DEFAULT NULL COMMENT '发布时间。对外显示、排序、sitemap lastmod 都用它。⚠ 不是 createtime：写了两周才发的文章不该排在这两周的文章前面',
  `sortnum`       int          NOT NULL DEFAULT 0 COMMENT '置顶用，越小越靠前。排序是 sortnum 再 publishtime 倒序',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注，只在管理台可见',
  `createuser`    varchar(50)  DEFAULT NULL COMMENT '创建人',
  `createtime`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser`    varchar(50)  DEFAULT NULL COMMENT '更新人',
  `updatetime`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`sitecode`,`postcode`),
  UNIQUE KEY `uk_post_slug` (`slug`) COMMENT '⚠ 全库唯一，不带 sitecode：slug 是 URL 的一部分，而一个部署一个站，URL 空间就一个',
  KEY `idx_post_list` (`sitecode`,`publishstatus`,`sortnum`,`publishtime`) COMMENT '对外列表走这条'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章主表。文案在 u_s_post_lang';


-- ---------------------------------------------------------------------
-- 文章多语言文案。分工同 u_s_sitegoods_lang：文案分语言，其余不分。
--
-- 分语言的表能做到「日语还没翻译好就先不发日语版」——博客最常见的工作方式，
-- 内容是一篇一篇、一种语言一种语言攒出来的。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_post_lang` (
  `postcode`   varchar(50)  NOT NULL COMMENT '文章内部编码，对应 u_s_post.postcode',
  `lang`       varchar(20)  NOT NULL COMMENT '语言代码。⚠ 站点没开的语言也可能有历史译文，取数时按站点当前开的语言过滤',
  `title`      varchar(300) DEFAULT NULL COMMENT '标题。⚠ 某语言为空 = 那个语言版本还没翻好，前台**直接不显示这篇文章**，不回退默认语言（和商品相反，理由见本节开头）',
  `summary`    varchar(500) DEFAULT NULL COMMENT '摘要。列表卡片正文 + meta description。留空则由正文剥标签截前 160 字兜底',
  `content`    mediumtext            COMMENT '正文富文本。⚠ 对外输出前必须过 RichTextCleaner 白名单——和商品详情同一条规矩',
  `keywords`   varchar(500) DEFAULT NULL COMMENT 'SEO 关键词，逗号分隔。⚠ Google 早就不用 meta keywords，留它是为了和 u_s_sitegoods_lang 结构一致',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`postcode`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章多语言文案';




-- =====================================================================
-- 四、出厂数据
-- =====================================================================

-- 两个角色。perms 里的串就是 station-console-ui 的 v-hasPermi 用的那批，
-- 沿用同一套，前端一个字都不用改。
INSERT IGNORE INTO `u_s_role` (`rolecode`, `rolename`, `perms`, `sortnum`, `remark`) VALUES
  ('admin', '站长', '*', 1, '本站全部权限。第一个从 ERP 跳进来的人应当由建站的人手工提成这个角色'),
  ('operator', '运营', 'site:station:list,site:goods:list,site:goods:query,site:goods:add,site:goods:edit,site:goods:remove,site:inquiry:list,site:inquiry:query,site:inquiry:edit,site:file:list,site:file:upload,site:file:edit,site:file:remove,site:order:list,site:order:query,site:order:edit,site:buyer:list,site:buyer:query,site:buyer:edit,site:chat:list,site:chat:reply,site:stat:query,site:seo:query,site:post:list,site:post:query,site:post:add,site:post:edit,site:post:remove,site:customplan:list', 2, '日常运营。⚠ 不含首页装修（site:station:edit）和存储设置（site:storage:*）——那两样改错了整站受影响');

-- ⚠ 上面那一行的权限串必须和 migration/ 下面那些 UPDATE u_s_role 的结果**一致**。
--   老库靠迁移脚本追加，新库靠这一行——两条路要走到同一个终点。
--   订单/客户那六个串原来只在 2026-09-04-order-and-buyer.sql 里追加、忘了同步到这里，
--   于是新装的机器上运营看不到订单和客户菜单。补上了，连同这次的 site:chat:*。
--   site:stat:query 是 2026-09-08-pageview.sql 加的，两边同一个提交里改的。
--   site:customplan:list 是 2026-09-29-custom-plan.sql 加的，同上。

INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES
  ('inquiry', 1), ('image', 1), ('video', 1), ('goods', 1), ('order', 1), ('buyer', 1), ('chat', 1),
  ('post', 1), ('model', 1), ('modeljob', 1);

-- ⚠ u_s_siteinfo 那一行**不在这里插**。
-- 它要填 sitecode / sitename / consolesecret，而这三个值一站一份、由建站的人现场定，
-- 尤其 consolesecret 必须和 ERP 登记的那份一致。在基线脚本里塞一个占位值的后果是：
-- 某台机器上线时没人记得改，跳转密钥变成了一个进过 Git 仓库的公开字符串。
-- 建站步骤见 docs/独立站部署手册（Windows）.md §4.3「初始化本站档案」。


-- =====================================================================
-- 附：shop_code 这一列为什么留着
-- =====================================================================
--
-- 一个部署一个站，租户列在这里没有意义。但**不删**——删它要动每一条 SQL
-- 和每一个 domain，而搬迁这一步应当只搬东西、不改结构（boundary §5.3）。
--
-- 约束：
--   · 值恒为建站时那个商户码（历史数据）或空串（新数据）；
--   · **新代码不得读它、不得按它过滤**；
--   · 将来清理是一次独立的动作，连着 SQL 和 domain 一起改。
--
-- 同理 sitecode：它留着是因为跳转令牌的 site 声明要和 u_s_siteinfo.sitecode 对齐，
-- 但它在这些业务表里也不是筛选维度——一个库里只有一个站的数据。
