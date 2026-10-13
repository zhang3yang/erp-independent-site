-- =====================================================================
-- 币种切换与汇率换算：建 u_s_rate，给 u_s_siteinfo 加 currencies
-- =====================================================================
--
-- 依据：docs/设计稿/2026-09-08-前台币种与汇率.md
--
-- 买家在前台顶栏选一个币种，商品价格按汇率换算显示。基准是站点本币
-- （u_s_siteinfo.currency，商品价格本来就是用它存的），汇率每天由后端
-- 从 open.er-api.com 自动拉取，运营可以手工覆盖。
--
-- ⚠ 换算只发生在展示层。结算页回到本币，u_s_order / u_s_orderitem 一行不改
--   （spec §2.3）。站上不收款，订单金额必须有一个稳定口径，否则汇率一变
--   老订单就成了糊涂账，管理台的订单列表也加不出总额。
--
-- =====================================================================
-- 什么时候需要跑这一份
-- =====================================================================
--
-- **只在「库是这次改动之前建的」时候需要。**
-- sql/station/01_station_schema.sql 里已经带上这张表和这一列了，但那个脚本
-- 全文是 CREATE TABLE IF NOT EXISTS——它对已经存在的表什么都不做。
--
--   · 全新的站点服务器：跑 01_station_schema.sql 就够了，**不用**跑这一份；
--   · 已经在跑的站（如 49.234.194.194 上那个）：需要跑这一份。
--
-- 不跑的症状：后端启动时 u_s_rate 查不到会报 Table doesn't exist，
-- 而 /openapi/site/info 里 currencies 字段缺失，前台不显示币种切换器
-- ——降级是安全的，但功能等于没上。
--
-- 可重复执行：CREATE TABLE IF NOT EXISTS + information_schema 判列 + INSERT IGNORE。
-- =====================================================================

USE `sdsstation`;


-- =====================================================================
-- 一、汇率表
-- =====================================================================
--
-- 单行表，和 u_s_siteinfo 一样 id 恒为 1。
--
-- **为什么整张汇率表存成一列 JSON，而不是 166 行**：永远整体读、整体写，
-- 没有任何「按币种查一行」的场景。拆成 166 行只会多出 166 次 insert 和一堆
-- 没人看的行，还要额外处理「API 这次返回的币种比上次少了一个」这种同步问题。
-- 和 u_s_siteinfo.contentjson 的做法一致。

CREATE TABLE IF NOT EXISTS `u_s_rate` (
  `id`           tinyint       NOT NULL DEFAULT 1     COMMENT '恒为 1。和 u_s_siteinfo 一样，这张表只有一行',
  `base_code`    varchar(10)   NOT NULL DEFAULT 'USD' COMMENT '⚠ 这是汇率源返回值的基准，跟着 API 走，**不是站点本币**。站点本币在 u_s_siteinfo.currency。两者混淆会让换算方向整个反过来',
  `ratesjson`    mediumtext                           COMMENT '自动拉回来的完整汇率表，形如 {"CNY":6.728426,"EUR":0.860364,...}，相对 base_code。整体读整体写',
  `overridejson` varchar(1000)                        COMMENT '运营手工覆盖，同样相对 base_code，形如 {"EUR":0.90}。填了就压过 ratesjson，**逐币种生效**——覆盖 EUR 不影响 CNY。用途：给汇率加 buffer，或 API 挂掉时兜底',
  `fetchtime`    datetime                             COMMENT '最后一次**成功**拉取的时间。管理台显示「汇率更新于」，也是判断过不过期（超 7 天标红）的依据。拉取失败不更新它',
  `fetchstatus`  varchar(20)                          COMMENT '成功/失败。最后一次拉取的结果',
  `fetcherror`   varchar(500)                         COMMENT '失败原因，排查用。成功时清空。免费 API 静悄悄挂掉是最可能的故障，而它在页面上没有别的症状',
  `updateuser`   varchar(50)                          COMMENT '最后改覆盖值的人',
  `updatetime`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率表，本库只有一行';

-- 那唯一一行。ratesjson 留空是正常起点：后端启动后 30 秒会拉第一次。
-- 拉到之前 /openapi/site/info 的 rates 里只有本币，前台不显示切换器——
-- 也就是降级成没有这个功能时的样子，不会报错。
INSERT IGNORE INTO `u_s_rate` (`id`, `base_code`) VALUES (1, 'USD');


-- =====================================================================
-- 二、u_s_siteinfo 加 currencies
-- =====================================================================
--
-- ⚠ 它和已有的 currency **不是一回事**，而且名字只差一个字母，看错的代价很实在：
--
--     currency    = 基准币种。商品价格 u_s_sitegoods.price 就是用它存的。
--     currencies  = 买家在前台能切到哪些币种。
--
-- 基准币种由后端自动并入可选列表（SiteInfo.currencyList()），所以运营
-- 不填它也不会把买家锁在外面。留空 = 不显示切换器。
--
-- MySQL 没有 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的扩展），用
-- information_schema 判一下再拼语句，好让这份脚本能被跑第二遍。

DROP PROCEDURE IF EXISTS `__add_siteinfo_currencies_column`;

DELIMITER $$
CREATE PROCEDURE `__add_siteinfo_currencies_column`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_siteinfo'
                   AND column_name = 'currencies') THEN
    ALTER TABLE `u_s_siteinfo`
      ADD COLUMN `currencies` varchar(200) DEFAULT NULL
      COMMENT '前台允许买家切换的显示币种，逗号分隔，如 USD,EUR,CNY。留空=不显示切换器。⚠ 与 currency 不是一回事：currency 是基准（商品价格用它存），这一列是买家能切到哪些。基准币种由后端自动并入，不必重复填'
      AFTER `currency`;
  END IF;
END$$
DELIMITER ;

CALL `__add_siteinfo_currencies_column`();
DROP PROCEDURE IF EXISTS `__add_siteinfo_currencies_column`;


-- =====================================================================
-- 三、自查
-- =====================================================================
--
-- 跑完应该看到：u_s_rate 一行、u_s_siteinfo 多了 currencies 列。
--
--   SELECT id, base_code, fetchstatus, fetchtime FROM u_s_rate;
--   SHOW COLUMNS FROM u_s_siteinfo LIKE 'currenc%';
--
-- 后端起来 30 秒后再看一次，fetchstatus 应该是「成功」、ratesjson 非空：
--
--   SELECT fetchstatus, fetchtime, CHAR_LENGTH(ratesjson) FROM u_s_rate;
--
-- 如果一直是「失败」，看 fetcherror。这台机器出网受限（memory
-- server-network-limits），但 open.er-api.com 在 2026-09-08 实测可达、818ms。
