-- =====================================================================
-- 博客：u_s_post（主表）+ u_s_post_lang（多语言文案）
-- =====================================================================
--
-- 起因：docs/独立站与ERP的边界.md §11 把「博客管理」列在「一期不做、只留位置」里，
-- 理由是「需求现在只有名字」。这一条现在不成立了——博客要什么已经很清楚：
-- 外贸站的自然流量很大一部分来自内容页，而这个站的 SEO 价值目前**全压在商品详情页上**
-- （sitemap.xml.ts 的注释里写着这一条）。商品页能覆盖的关键词就是商品名，
-- 覆盖不到「how to choose silicone tubing」这类真正带来询盘的长尾词。
--
-- =====================================================================
-- 为什么拆成两张表
-- =====================================================================
--
-- 和 u_s_sitegoods / u_s_sitegoods_lang 一样的分工：**文案分语言，其余不分**。
--
-- 一列 JSON 也存得下八种语言，但那样「第三段正文」在两种语言里只能靠**下标**对齐
-- （u_s_sitegoods_combo 的注释里记着这个坑的代价）。而且分语言的表能做到
-- 「日语还没翻译好就先不发日语版」——这是博客最常见的工作方式，
-- 内容是一篇一篇、一种语言一种语言攒出来的。
--
-- =====================================================================
-- ⚠ 两个标识：postcode 是内部的，slug 是对外的
-- =====================================================================
--
--   · postcode  内部编码（CodeGenerator 发号，BLOG 前缀）。语言表的外键靠它，
--               管理台的增删改也用它。**永不出现在前台 URL 里。**
--   · slug      公开 URL 那一段：/blog/how-to-choose-silicone-tubing
--
-- 为什么不像商品那样直接用编码做 URL（/products/G001）：
-- **商品页的 URL 里放编码是可以的**，买家搜的是商品名，而商品名在 <title> 里；
-- 博客不是——博客整个存在的理由就是自然搜索，而 URL 里的关键词是排名因素之一。
-- /blog/BLOG0000000001 这种地址等于把博客最主要的一个优势扔掉。
--
-- ⚠ **slug 只有一个，不分语言。** 语言靠已有的路径前缀区分：
--   /blog/xxx（默认语言）、/ja/blog/xxx（日语）。
--
--   分语言的 slug 对 SEO 更好一点（日语页用日语关键词），但代价很重：
--   一篇文章会有八个身份，改一个语言的 slug 要考虑另外七个的重定向，
--   而且两种语言撞上同一个 slug 时没有正确答案。一篇文章一个身份，
--   这是 hreflang 那一组链接能成立的前提。
--
-- =====================================================================
-- ⚠ 这两张表没有 shop_code
-- =====================================================================
--
-- 同 u_s_pageview / u_s_pv_daily 的决定：老表都带这一列，而
-- 01_station_schema.sql 末尾写着「新代码不得读它、不得按它过滤」。
-- 既然不读，新表就不建——建了只会让下一个人以为它有意义，然后在 WHERE 里带上它。
--
-- sitecode 留着：和 u_s_inquiry / u_s_chat / u_s_pageview 一致。
--
-- =====================================================================
-- 可重复执行：CREATE TABLE IF NOT EXISTS + INSERT IGNORE + 幂等的权限追加。
-- 新建的库不用跑——01_station_schema.sql 里已经带上这两张表了。
-- =====================================================================

USE `sdsstation`;


CREATE TABLE IF NOT EXISTS `u_s_post` (
  `sitecode`      varchar(50)  NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `postcode`      varchar(50)  NOT NULL COMMENT '内部编码，CodeGenerator 发号（BLOG 前缀）。语言表的外键、管理台的增删改都用它。⚠ 永不出现在前台 URL 里，那是 slug 的事',

  -- ⚠ 这一列进了唯一索引。改一篇已发布文章的 slug 等于换一个地址：
  --   老地址会 404，已经积累的排名和外链一起丢。管理台上对已发布的文章
  --   会警告这一点（SitePostService 里那段注释）。
  `slug`          varchar(160) NOT NULL COMMENT '公开 URL 那一段，如 how-to-choose-silicone-tubing。只允许小写字母、数字和连字符。⚠ 不分语言：一篇文章一个身份，这是 hreflang 能成立的前提',

  `category`      varchar(100) DEFAULT NULL COMMENT '博客分类（英文）。列表页的筛选按钮由它去重现算，同 u_s_sitegoods.sitecategory 的做法——不另建分类表，因为分类会跟着内容改，维护一张表一定会不同步',
  `coverimg`      varchar(500) DEFAULT NULL COMMENT '封面图完整 URL，来自文件资源库。列表页的卡片和详情页顶部都用它，也是 og:image',
  `author`        varchar(100) DEFAULT NULL COMMENT '署名。留空则前台不显示作者行（不要兜底成公司名——那看起来像是没填）',

  `publishstatus` varchar(20)  NOT NULL DEFAULT '草稿' COMMENT '草稿/已发布/已下架。⚠ 只有「已发布」对外可见，口径和 u_s_sitegoods.publishstatus 的「已上架」一致',

  -- ⚠ 和 createtime 是两件事，别用后者代替。
  --   一篇文章可能写了两周才发，用创建时间排序会让它排在这两周里发的所有文章之前。
  --   也允许手工改：补录一篇旧文章时要能把日期写成当时的。
  `publishtime`   datetime     DEFAULT NULL COMMENT '发布时间。对外显示、列表排序、sitemap 的 lastmod 都用它。⚠ 不是 createtime：写了两周才发的文章不该排在这两周的文章前面。首次转「已发布」时自动填当前时间，之后可手工改',

  `sortnum`       int          NOT NULL DEFAULT 0 COMMENT '置顶用，越小越靠前。⚠ 列表排序是 sortnum 再 publishtime 倒序——全为 0 时就是纯时间序，也就是博客该有的样子',

  `remark`        varchar(500) DEFAULT NULL COMMENT '备注，只在管理台可见',
  `createuser`    varchar(50)  DEFAULT NULL COMMENT '创建人',
  `createtime`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser`    varchar(50)  DEFAULT NULL COMMENT '更新人',
  `updatetime`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`sitecode`,`postcode`),
  UNIQUE KEY `uk_post_slug` (`slug`) COMMENT '⚠ 全库唯一，不带 sitecode：slug 是 URL 的一部分，而一个部署一个站，URL 空间就一个',
  KEY `idx_post_list` (`sitecode`,`publishstatus`,`sortnum`,`publishtime`) COMMENT '对外列表走这条：先按状态过滤，再 sortnum + publishtime 排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章主表。文案在 u_s_post_lang';


CREATE TABLE IF NOT EXISTS `u_s_post_lang` (
  `postcode`   varchar(50)  NOT NULL COMMENT '文章内部编码，对应 u_s_post.postcode',
  `lang`       varchar(20)  NOT NULL COMMENT '语言代码，如 en / ja / es。⚠ 站点没开的语言也可能有历史译文，取数时要按站点当前开的语言过滤',

  `title`      varchar(300) DEFAULT NULL COMMENT '标题。进 <title> 和 og:title。⚠ 某语言这一列为空 = 那个语言版本还没翻译好，**前台直接不显示这篇文章**，不回退默认语言，见下面那段说明',
  `summary`    varchar(500) DEFAULT NULL COMMENT '摘要。列表页的卡片正文 + meta description。留空则由正文剥标签截前 160 字兜底（同商品详情页的做法）',
  `content`    mediumtext            COMMENT '正文富文本。⚠ 对外输出前必须过 RichTextCleaner 的白名单——和商品详情同一条规矩，不因为换了张表就放宽',
  `keywords`   varchar(500) DEFAULT NULL COMMENT 'SEO 关键词，逗号分隔。⚠ Google 早就不用 meta keywords 了，留这一列是为了和 u_s_sitegoods_lang 结构一致、以及给站内检索留余地',

  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`postcode`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章多语言文案';


-- ---------------------------------------------------------------------
-- ⚠⚠ 博客和商品在「缺译文」这件事上的行为**故意相反**
-- ---------------------------------------------------------------------
--
--   · 商品缺某语言文案 → 回退站点默认语言（SiteGoodsMapper 的 selectPublic* 都带
--     defaultLang 回退）。因为商品必须能被买到：宁可标题是英文，也不能让日语站
--     少一个可下单的商品。
--
--   · 文章缺某语言标题 → **那个语言版本直接不存在**（列表里没有、详情 404）。
--
-- 为什么反过来：一篇英文长文摆在日语站上，对日本读者没有任何价值，
-- 而它会被 Google 判成「hreflang 说是日语、内容是英文」——那比少一篇文章更伤。
-- 商品页的标题短、图和价格自己会说话；文章除了文字什么都没有。
--
-- 这条规则在三处一起生效，缺一处就漏：
--   · SitePostMapper.xml 的 selectPublic*（inner join + title 非空）
--   · sitemap 只列有译文的那些语言（sitemap.xml.ts）
--   · hreflang 也只列有译文的语言 —— 否则等于向 Google 承诺一个 404
--
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- 发号器加一个序列。前缀 BLOG，宽度和其它一样 10 位。
-- ⚠ 这一行不加，nextPostCode() 第一次调用会拿不到号（SequenceMapper 靠这一行自增）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES ('post', 1);


-- ---------------------------------------------------------------------
-- 权限。五个，和商品那一套对齐。
--
--   site:post:list / query / add / edit / remove
--
-- **全给 operator**：写文章就是运营的日常活，和商品管理同一个性质
-- （operator 本来就有完整的 site:goods:* 五件套）。
--
-- ⚠ 这里和站长工具的分工不一样，不是不一致：站长工具里那个「整站禁收录」
--   一开就静默影响全站，所以归站长；博客是内容，改错了只影响那一篇，
--   而且看得见、改得回来。
--
-- ⚠ 用 FIND_IN_SET 判重，不用 LIKE：LIKE '%site:post:list%' 会被将来可能出现的
--   site:post:listall 之类误判成「已经有了」。
-- admin 是 '*'，本来就全都有，不用动。
-- ---------------------------------------------------------------------
UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`,
        ',site:post:list,site:post:query,site:post:add,site:post:edit,site:post:remove')
 WHERE `rolecode` = 'operator'
   AND `perms` != '*'
   AND NOT FIND_IN_SET('site:post:list', `perms`);


-- ---------------------------------------------------------------------
-- 登记台账（sql/station/migration/2026-09-08-schema-migration-ledger.sql）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
VALUES ('2026-09-08-blog.sql', NULL, '执行',
        '博客：u_s_post + u_s_post_lang 建表，post 序列，operator 加 site:post:* 五件套');
