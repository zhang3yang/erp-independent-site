-- =====================================================================
-- u_s_ip_country：IP 段 → 国家的离线对照表
-- =====================================================================
--
-- 起因：u_s_pageview.country 这一列从建表起就一直是 NULL。当时的注释写的是
-- 「没接 IP 归属库。留列不留空谈：将来接了直接回填，不用改表」。现在接上。
--
-- =====================================================================
-- ⚠ 这个脚本**只建表，不带数据**
-- =====================================================================
--
-- 数据是 71.7 万行、38 MB 的 SQL，不该进 git 仓库。导入方式见文件末尾。
-- 也就是说：**跑完这个脚本，国家统计仍然是空的**，还要再导一次数据。
-- 这一点必须知道，否则会以为「接好了怎么没数」。
--
-- =====================================================================
-- 为什么是自己一张表，而不是调第三方 IP 查询接口
-- =====================================================================
--
--   · 每记一次浏览就往外发一个请求，是把「统计挂了」和「别人的服务挂了」
--     绑在一起——而且那个请求会把访客 IP 送给第三方，
--     这和「数据不出自己服务器」这条整套设计的前提直接冲突；
--   · 这台服务器出站受限（见 memory: server-network-limits），
--     依赖外部接口等于给自己埋一个随时会断的依赖。
--
-- 离线表则是一次导入、之后完全本地查，没有任何外部依赖。
--
-- =====================================================================
-- 数据来源和许可
-- =====================================================================
--
-- DB-IP country-lite（https://db-ip.com/db/download/ip-to-country-lite）
-- 许可是 CC-BY 4.0：**免费商用，但要求署名**。
-- ⚠ 如果将来把国家统计做进对外可见的页面（不只是内部管理台），
--   要在那个页面上标一句「IP Geolocation by DB-IP」。现在只在管理台内部看，
--   还没到那一步，但改动前先想起这条。
--
-- 数据每月更新一次。不更新也不会坏，只是新分配的 IP 段查不到（返回 NULL）。
-- 想更新就重新生成一份 SQL 再导，TRUNCATE 在生成的文件里，是幂等的。
--
-- =====================================================================
-- 为什么 IPv4 和 IPv6 放同一张表
-- =====================================================================
--
-- 用 `ipver` 区分，值都存成 varbinary(16)：IPv4 是 4 字节，IPv6 是 16 字节。
--
-- ⚠ 长度不同的值放同一列，比较是**逐字节**的，短的算小。所以查询
--   **必须先按 ipver 过滤**——不过滤的话 IPv4 的 4 字节值会和 IPv6 的
--   16 字节值混在一起比大小，结果毫无意义，而且不会报错。
--   索引 (ipver, ipstart) 的第一列就是为这件事存在的。
--
-- 为什么不把 IPv4 存成 int unsigned 单独一张表：那样查询要分两套代码路径，
-- 而这里的代价只是索引第一列多一个字节。
--
-- ⚠ 为什么必须支持 IPv6：这个站面向境外买家，而欧洲移动网络的 IPv6 占比很高。
--   只做 IPv4 的话，那部分访客的国家会静默为空——看起来像「有一批查不到的」，
--   而不像「少做了一半」。
--
-- =====================================================================
-- 可重复执行：CREATE TABLE IF NOT EXISTS。
-- =====================================================================

USE `sdsstation`;

CREATE TABLE IF NOT EXISTS `u_s_ip_country` (
  -- ⚠ 查询必须带上这一列，理由见文件头
  `ipver`   tinyint       NOT NULL COMMENT 'IP 版本：4 或 6。⚠ 查询必须先按它过滤——两种长度的值混在一列里比大小是无意义的',
  `ipstart` varbinary(16) NOT NULL COMMENT '段起始，INET6_ATON 的字节形式。IPv4 是 4 字节，IPv6 是 16 字节',
  `ipend`   varbinary(16) NOT NULL COMMENT '段结束，含。⚠ 查到候选行之后还要判 ipend >= 目标——段与段之间有空隙',
  `country` char(2)       NOT NULL COMMENT '国家二字码（ISO 3166-1 alpha-2）。ZZ = 未知/保留段',

  PRIMARY KEY (`ipver`,`ipstart`),
  -- 查询形态是 `where ipver=? and ipstart <= ? order by ipstart desc limit 1`，
  -- 主键正好能走这条路。⚠ 不要改成 `between ipstart and ipend`：
  -- 那种写法 MySQL 只能用上索引的一侧，另一侧要回表逐行判，
  -- 在 71 万行上是几百毫秒 vs 零点几毫秒的差别
  KEY `idx_ipc_end` (`ipver`,`ipend`) COMMENT '备用：反向核对某个段时用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP 段到国家的离线对照表。数据来自 DB-IP country-lite (CC-BY)，不进 git，单独导入';


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
     VALUES (''2026-09-09-ip-country.sql'', ''claude'', ''执行'',
             ''建 u_s_ip_country（只建表，数据另导 38MB SQL）'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');

PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 怎么导数据（这个脚本不带数据）
--
--   1. 下载 DB-IP 的免费 CSV（不用注册）：
--      https://download.db-ip.com/free/dbip-country-lite-YYYY-MM.csv.gz
--
--   2. 生成 SQL（脚本在 scripts/gen-ipcountry.js，带自检用例）：
--      node scripts/gen-ipcountry.js dbip.csv ipcountry.sql
--
--   3. 传到服务器，然后：
--      mysql -uroot -p -e "SOURCE C:/build/ipcountry.sql"
--      ⚠ 路径用正斜杠——SOURCE 里的反斜杠被当转义符
--
-- 验证（导完必做）：
--
--   SELECT ipver, COUNT(*) FROM u_s_ip_country GROUP BY ipver;
--   -- 2026-09 那版是 IPv4 357325 / IPv6 359845
--
--   -- 抽查几个已知答案的地址
--   SELECT country FROM u_s_ip_country
--    WHERE ipver=4 AND ipstart <= INET6_ATON('8.8.8.8')
--    ORDER BY ipstart DESC LIMIT 1;          -- 应为 US
--
-- ⚠ 导完不用重启 station-console：查询是现查的，没有进程内缓存。
-- ---------------------------------------------------------------------
