-- =====================================================================
-- u_s_pageview 加两列：device / browser
-- =====================================================================
--
-- 起因：想看「买家是手机还是电脑」。这个问题**数据早就有了**——
-- 2026-09-08-pageview.sql 建表时就把 useragent 原样存下来了，
-- 当时写的理由是「isbot 的规则会改，原始 UA 不会」。
-- 现在正好用上：不需要新采集任何东西，只是把已有的 UA 解析出来。
--
-- =====================================================================
-- 为什么落成两列，而不是查询时用 SQL 解析 UA
-- =====================================================================
--
-- 因为 UA 判定是一串「谁在谁之前」的顺序敏感规则（见 UserAgentParser 的类注释：
-- 每个浏览器的 UA 里都有 Safari，Chrome 系的里都有 Chrome）。
-- 用 SQL 的 CASE WHEN LIKE 堆出来能跑，但：
--
--   · 顺序写错了不报错，只是数字偏——而且偏得很像真的；
--   · 每次查报表都要对全表做一遍字符串匹配，用不上任何索引；
--   · 规则要改的时候得同时改 SQL 和 Java，两处迟早不一致。
--
-- 落列则是写入时算一次，查询就是 GROUP BY。规则改了重新解析一遍 useragent
-- 就能回填——这也是当初坚持存原始 UA 的价值所在。
--
-- =====================================================================
-- ⚠ 这两列不进 u_s_pv_daily
-- =====================================================================
--
-- 和 referrer 同一条规矩（见 SitePageViewMapper.xml 文件头规矩三）：
-- 进聚合表就要进主键，主键每多一个维度、那张表的行数就乘一遍。
--
-- 代价是设备分布**只能查保留期（默认 90 天）以内**。可以接受——
-- 「买家用手机还是电脑」是个季度级别才会变的结构性问题，不需要看三年前的。
--
-- =====================================================================
-- 可重复执行：用 information_schema 判断列在不在。
-- 新建的库不用跑——01_station_schema.sql 里已经带上这两列了。
-- =====================================================================

USE `sdsstation`;


SET @has_device := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_pageview'
     AND COLUMN_NAME  = 'device'
);

SET @ddl := IF(@has_device > 0,
  'SELECT ''u_s_pageview.device 已存在，跳过'' AS msg',
  'ALTER TABLE `u_s_pageview`
     ADD COLUMN `device` varchar(10) DEFAULT NULL
       COMMENT ''设备类型：mobile/tablet/desktop。由 UserAgentParser 从 useragent 解析，不采集新数据''
       AFTER `useragent`,
     ADD COLUMN `browser` varchar(20) DEFAULT NULL
       COMMENT ''浏览器：Chrome/Safari/Edge/Firefox/Opera/Samsung/IE/Other。⚠ 判定顺序敏感，见 UserAgentParser''
       AFTER `device`');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 回填已有的行
--
-- ⚠ 这里**只做一个粗回填**，规则比 Java 那份简单得多（顺序对齐了，但少了
--   平板、Samsung、IE 这些分支）。理由：这张表在加这两列的时候基本是空的
--   （2026-09-08 上线当天就加了），回填的意义只是「别让老行是 NULL」。
--
--   要精确回填就跑 Java 那份逻辑，别指望这段 SQL——它和 UserAgentParser
--   **不是同一套规则**，两边结果不一定一致。这一点必须知道，
--   否则「为什么老数据的分布和新数据不一样」会查很久。
-- ---------------------------------------------------------------------
UPDATE `u_s_pageview`
   SET `device` = CASE
         WHEN `useragent` IS NULL OR `useragent` = '' THEN NULL
         WHEN LOWER(`useragent`) LIKE '%ipad%' THEN 'tablet'
         WHEN LOWER(`useragent`) LIKE '%iphone%'
           OR LOWER(`useragent`) LIKE '%windows phone%'
           OR (LOWER(`useragent`) LIKE '%android%' AND LOWER(`useragent`) LIKE '%mobile%') THEN 'mobile'
         WHEN LOWER(`useragent`) LIKE '%android%' THEN 'tablet'
         ELSE 'desktop'
       END,
       -- ⚠ 顺序就是这里的全部内容：Edg 在 Chrome 前面（Edge 的 UA 里有 Chrome），
       --    Chrome 在 Safari 前面（Chrome 的 UA 里有 Safari）。调换任何两行都会错。
       `browser` = CASE
         WHEN `useragent` IS NULL OR `useragent` = '' THEN NULL
         WHEN LOWER(`useragent`) LIKE '%edg%'     THEN 'Edge'
         WHEN LOWER(`useragent`) LIKE '%opr/%'    THEN 'Opera'
         WHEN LOWER(`useragent`) LIKE '%firefox%' THEN 'Firefox'
         WHEN LOWER(`useragent`) LIKE '%chrome%'  THEN 'Chrome'
         WHEN LOWER(`useragent`) LIKE '%safari%'  THEN 'Safari'
         ELSE 'Other'
       END
 WHERE `device` IS NULL;


-- ---------------------------------------------------------------------
-- 登记台账。判一下表在不在，理由见 2026-09-08-pageview.sql 里同样的那段
-- ---------------------------------------------------------------------
SET @has_ledger := (
  SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_schema_migration'
);

SET @ledger_sql := IF(@has_ledger > 0,
  'INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
     VALUES (''2026-09-08-pageview-device.sql'', ''claude'', ''执行'',
             ''u_s_pageview 加 device/browser 两列，从已存的 useragent 解析'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');

PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 跑完要重启 station-console：解析这两列的是新代码。
--
-- 验证：
--   SELECT device, browser, count(*) FROM u_s_pageview
--    WHERE isbot = 0 GROUP BY device, browser ORDER BY 3 DESC;
--
-- ⚠ 如果 device 全是 desktop，先看 useragent 那一列是不是空的——
--   而不是怀疑解析规则。空 UA 会被 BotSniffer 判成爬虫（isbot=1），
--   上面那句带了 isbot=0，所以空 UA 的行压根不该出现在结果里。
-- ---------------------------------------------------------------------
