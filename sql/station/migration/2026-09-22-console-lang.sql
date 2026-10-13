-- =====================================================================
-- 管理台界面语言：给 u_s_siteinfo 加 consolelang
-- =====================================================================
--
-- 起因：法国站（esvafit）的站长是外国人。他点开管理台看到的是一整屏中文——
-- 左边十一个菜单、每一栏的说明、每一个按钮。他不是「看得慢一点」，是**根本不知道
-- 哪个按钮是保存**。而我们自己的业务员同时也在用同一个管理台，他们要看中文。
--
-- 所以这一列存的是「这个站的管理台默认用哪种语言」，不是「管理台只能用这种语言」：
--
--   · 这一列  = 一个人**第一次**进来（浏览器里还没有他自己的选择）时看到的语言；
--   · 每个人  = 顶栏那个语言下拉自己切，存在他自己浏览器的 localStorage 里
--               （键 station-console-lang），从此只认他自己那一份。
--
-- 两层的分工是这个功能的全部要点：把它做成单层（只有站点一个开关）的话，
-- 站长切成法语的那一刻，所有业务员的管理台也一起变成法语。
--
-- ⚠ 它和 langs 是两件事，别看混：
--     langs       = 前台对买家开放哪几种语言（商品文案要按这些语言填）；
--     consolelang = 管理台自己的界面语言。
--   一个只卖法国的站可以 langs=fr 而 consolelang=zh（我们自己运营），
--   反过来也成立（站长是法国人，前台却开着六种语言）。
--
-- 取值：zh / en / fr。NULL 或空 = zh，也就是**改这一列之前的样子**——
-- 已经在跑的站升级上来一切照旧，不会有人某天早上发现管理台变成了英文。
--
-- 候选值必须和管理台前端 station-console-ui/src/i18n/index.js 的 LOCALES 一致：
-- 那里才有真正的文案表。这一列写进一个没有文案表的语言码，界面会整屏退回中文——
-- 和 langs 那个白名单是同一类问题，处理也一样，后端 SiteInfo.CONSOLE_LANGS 兜住。
--
-- 什么时候需要跑：只在「库是这次改动之前建的」时候。
-- sql/station/01_station_schema.sql 里已经带上这一列了，新建的库不用跑。
--
-- 幂等：重复执行不报错（information_schema 判断）。
-- =====================================================================

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_siteinfo'
     AND COLUMN_NAME  = 'consolelang'
);

SET @ddl := IF(@has_col > 0,
  'SELECT ''u_s_siteinfo.consolelang 已存在，跳过'' AS msg',
  'ALTER TABLE `u_s_siteinfo`
     ADD COLUMN `consolelang` varchar(10) DEFAULT NULL
     COMMENT ''管理台界面的默认语言：zh/en/fr，null=zh。⚠ 与 langs 无关——那个是前台对买家开放的语言，这个是管理台自己的界面语言。它只是**默认值**：每个人可以在管理台顶栏自己切，切完存在他自己浏览器的 localStorage(station-console-lang) 里，不回写这一列。候选值必须和 station-console-ui/src/i18n 的文案表一致，写一个没有文案表的语言码等于整屏退回中文''
     AFTER `langs`');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 加完是 NULL = 中文，和以前一模一样。要让某个站默认开英文/法语：
--
--   UPDATE `u_s_siteinfo` SET `consolelang` = 'fr' WHERE `id` = 1;   -- 法国站
--
-- 也可以不跑这条，直接在管理台「站点概览 → 站点设置 → 管理台语言」里选。
--
-- ⚠ 绕过服务改库要重启 station-console：站点档案缓存在进程里（SiteInfoService），
--    从管理台改则会自己清缓存。
-- ---------------------------------------------------------------------
