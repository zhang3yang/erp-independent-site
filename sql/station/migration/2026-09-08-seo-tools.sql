-- =====================================================================
-- 站长工具：u_s_seo（单行表）
-- =====================================================================
--
-- 起因：管理台左侧「站长工具」一直是个空页（docs/独立站与ERP的边界.md §11）。
-- 前台的 SEO 基建其实已经相当完整——useSeo.ts 有 canonical + hreflang + og，
-- sitemap.xml.ts 把每个商品每种语言都列出来，robots.txt.ts 还按域区分了买家中心。
--
-- 所以这个模块**不是从零做 SEO**，是两件事：
--   · 把那些写死在前台代码里的规则搬到库里，让站长能改；
--   · 加上验证码和统计代码的注入位——那是「真想拿到收录数据」的前提。
--
-- =====================================================================
-- ⚠⚠ 这张表整体是公开的，任何私密字段都不许进来
-- =====================================================================
--
-- 下面每一列最终都会出现在 `/openapi/site/info` 的返回里，而那是个**匿名接口**。
-- 不是疏忽，是必须如此：sitemap.xml 和 robots.txt 是 station-site（Nuxt/Nitro）
-- 生成的，不是后端生成的，所以连 sitemapmax 这种看着像后端参数的东西也得发给前台。
--
-- 于是这条约束是硬的：**将来要存 GSC 的 API key、第三方平台的 token 之类的东西，
-- 必须另建一张表**，不能图省事加在这里。加在这里不会报错，只是把密钥公开在
-- 一个谁都能 curl 的接口上，而且没有任何现象提示。
--
-- =====================================================================
-- 为什么单独一张表，不加在 u_s_siteinfo 上
-- =====================================================================
--
-- 照 u_s_storage 的先例：那也是「一份全站配置、单行、和站点档案分开」。
--
-- u_s_siteinfo 现在 17 列，注释已经在费力解释「currency 和 currencies 只差一个
-- 字母但不是一回事」这类问题了。而 SEO 配置的生命周期和它不一样：验证码是一次性的、
-- robots 是运营随时会动的，跟 sitecode / consolesecret 那种一配就再也不碰的东西
-- 放在一起，语义上是错的。
--
-- =====================================================================
-- 可重复执行：CREATE TABLE IF NOT EXISTS + INSERT IGNORE + 幂等的权限追加。
-- 新建的库不用跑——01_station_schema.sql 里已经带上这张表了。
-- =====================================================================

USE `sdsstation`;


CREATE TABLE IF NOT EXISTS `u_s_seo` (
  `id` tinyint NOT NULL DEFAULT 1 COMMENT '恒为 1。一个部署一个站，这张表只有一行，同 u_s_siteinfo 的范式',

  -- ---- 收录 ----------------------------------------------------------
  --
  -- ⚠ noindexall 和 u_s_siteinfo.status 是**两个独立的决定**，别把它当成状态的副本。
  --   status=建设中/已停用 会让前台整个不对外（SiteInfo#isOpen 只认「运行中」），
  --   那时候谈收录没有意义。这一列管的是另一件事：**站开着、对访客正常，但先别收录**
  --   ——软上线阶段（内容还没铺完就先给客户看）唯一正确的姿势。
  `noindexall`    tinyint      NOT NULL DEFAULT 0 COMMENT '1=整站禁止收录。robots.txt 输出 Disallow: /，每个页面加 noindex，sitemap.xml 返回 404。⚠ 与 status 是两件事，不是副本，见上面注释',

  -- ⚠ 这一列是**追加**，不是替换。robots.txt.ts 里那批硬编码的 Disallow
  --   （购物车 / 结账 / 我的询价 / 登录注册 / 账号中心，各带一份语言前缀版）**故意留在代码里**：
  --   结账页不该被收录是这个站的结构性事实，不是运营偏好。搬进库的唯一效果，
  --   是某天有人把它们删掉，然后结账页开始出现在搜索结果里。
  `disallowpaths`  varchar(1000) DEFAULT NULL COMMENT '额外的 Disallow 路径，逗号分隔，如 /promo-draft,/tmp。⚠ 是追加，不替换代码里那批基线',
  `robotsextra`    varchar(2000) DEFAULT NULL COMMENT 'robots.txt 末尾原样追加的内容（Crawl-delay、针对某个 UA 的段落等）。按行原样输出，只做长度和控制字符过滤',

  -- ---- sitemap -------------------------------------------------------
  `sitemapon`      tinyint      NOT NULL DEFAULT 1 COMMENT '1=生成 sitemap.xml，0=返回 404',

  -- 现状是硬编码封在 540 条（size=60 × 最多 9 页），而 sitemap.xml.ts 的注释自己
  -- 写着「真超过 500 个商品时要改成分片」。这一轮**不做分片**：单文件上限是 5 万条
  -- URL，这个站离得很远，为一个还不存在的问题引入索引文件不值得。
  -- 只把上限变成可配的，默认给到 5000。
  `sitemapmax`     int          NOT NULL DEFAULT 5000 COMMENT '进 sitemap 的商品条数上限。⚠ 不是 URL 条数：每个商品会乘以语言数。默认 5000，单文件规范上限 5 万条 URL',
  `homefreq`       varchar(20)  NOT NULL DEFAULT 'daily' COMMENT '首页和列表页的 changefreq',
  `goodsfreq`      varchar(20)  NOT NULL DEFAULT 'weekly' COMMENT '商品详情页的 changefreq',

  -- ---- 站点验证 ------------------------------------------------------
  --
  -- ⚠ 只存**验证码那个值**，不存整个 <meta> 标签。前台自己拼标签。
  --   存整个标签就等于开了一个「往 <head> 里塞任意 HTML」的口子，
  --   而这几个框是给运营用的。
  `googleverify`   varchar(100) DEFAULT NULL COMMENT 'Google Search Console 的 google-site-verification 值。只填 content 那一段，不要整个 meta 标签',
  `bingverify`     varchar(100) DEFAULT NULL COMMENT 'Bing 站长工具的 msvalidate.01 值',
  `baiduverify`    varchar(100) DEFAULT NULL COMMENT '百度站长的 baidu-site-verification 值',

  -- ---- 统计 ----------------------------------------------------------
  --
  -- ⚠⚠ 这三列存的是 **ID，不是代码**。<script> 标签由 station-site 的
  --   server/plugins/seo-head.ts 自己拼出来。
  --
  --   为什么不给一个「自由粘贴统计代码」的文本框（几乎所有建站产品都给）：
  --   那是一个真的存储型 XSS 入口——任何能进管理台的人都能在**每一个公开页面**
  --   上执行任意 JavaScript，包括偷买家 Cookie 和改结账页。
  --   代价是接一个新平台要改代码，这个代价我们认。
  --
  --   ⚠ 格式校验做**两次**：保存时（SiteSeoService）和输出时（seo-head.ts）。
  --     输出时那一次不是多余的——库里的值可能是别人用 SQL 直接改的，
  --     而输出端正是拼字符串进 <script> 的地方。「只存 ID」这个安全前提，
  --     靠的就是输出端那一次。
  `ga4id`          varchar(50)  DEFAULT NULL COMMENT 'GA4 测量 ID，形如 G-XXXXXXXXXX。⚠ 只存 ID，脚本由前台拼',
  `gtmid`          varchar(50)  DEFAULT NULL COMMENT 'Google Tag Manager 容器 ID，形如 GTM-XXXXXXX。⚠ 和 ga4id 同时填大概率重复计数——GTM 容器里通常也挂着一个 GA4 代码',
  `baiduhmid`      varchar(50)  DEFAULT NULL COMMENT '百度统计 hm.js 的 32 位 id。⚠ 对海外访客慢甚至连不上，而这个站的访客在境外，默认留空',

  `remark`     varchar(500) DEFAULT NULL COMMENT '备注',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50)  DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站长工具配置，只有一行。⚠ 每一列都会出现在匿名接口 /openapi/site/info 里，不许存密钥';


-- ---------------------------------------------------------------------
-- 插那一行。
--
-- ⚠ 用 INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE：部署脚本被重复执行是常态，
--   后者会把已经配好的验证码和统计 ID 覆盖回默认值。同 02_init_siteinfo.sql。
--
-- ⚠ 全部取列默认值 = **和这张表不存在时的行为完全一致**。这是有意的：
--   后端在查不到这一行时也返回同一份默认值（SiteSeoService#current），
--   所以「老库没跑这个脚本」和「跑了但没配」表现一模一样，不会多出一种故障形态。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `u_s_seo` (`id`, `remark`) VALUES (1, '建表时自动插入');


-- ---------------------------------------------------------------------
-- 权限。两个，不是三个。
--
--   site:seo:query   看体检、看配置   → operator + admin
--   site:seo:edit    改配置           → **只有 admin**
--
-- 体检要给 operator：它就是运营的日常活——「哪个商品的日语标题还没填」，
-- 看完直接去改商品。
--
-- 改配置不给 operator：这里面有 noindexall 那种一开就静默毁掉自然流量的开关
-- （站照常显示，只有几周后流量掉了才发现）。按管理台既有的分工，
-- 站点级配置归站长——operator 本来也没有 site:station:edit。
--
-- 不加第三个 site:seo:script：统计代码已经只存 ID 了，它和验证码不再是两个风险等级。
--
-- ⚠ 用 FIND_IN_SET 判重，不用 LIKE。LIKE '%site:seo:query%' 会被
--   site:seo:query2 这种将来可能出现的权限串误判成"已经有了"。
-- admin 是 '*'，本来就全都有，不用动。
-- ---------------------------------------------------------------------
UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`, ',site:seo:query')
 WHERE `rolecode` = 'operator'
   AND `perms` != '*'
   AND NOT FIND_IN_SET('site:seo:query', `perms`);


-- ---------------------------------------------------------------------
-- 登记台账（sql/station/migration/2026-09-08-schema-migration-ledger.sql）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
VALUES ('2026-09-08-seo-tools.sql', NULL, '执行', '站长工具：u_s_seo 建表 + operator 加 site:seo:query');
