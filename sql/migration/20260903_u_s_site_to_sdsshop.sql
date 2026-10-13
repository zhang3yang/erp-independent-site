-- ============================================================================
-- 站点档案 u_s_site：从 sdsstation 搬到 sdsshop
--
-- 背景：独立站和 ERP 完全分开部署，sdsstation 整个库归站点服务器，ERP 侧最后会
--      DROP DATABASE sdsstation。但「有哪些站、在哪、怎么进」这份登记表是 ERP 自己要用的，
--      它按 shop_code 分租户，和 u_m_buyer / u_m_waregoods 是同一类商家资产，所以进 sdsshop。
--      依据：docs/独立站与ERP的边界.md §5.2、§7，迁移顺序见同一份 §10.4 ⑧a。
--
-- ⚠ 这一步和改 mapper 必须连着做：StationSiteMapper.xml 里 9 处 sdsstation.u_s_site
--    已经改成 sdsshop.u_s_site（StationInquiryMapper.xml 的 2 处 left join 一并改了）。
--    先跑脚本再发版，中间那一段时间独立站管理页是查不到数据的，别隔一天。
--
-- ⚠ 前置：无。20260831_station_inquiry_notify.sql 跑没跑过都行，②那一段会自己适配。
--
-- ⚠ 本脚本会 DROP 掉旧表 sdsstation.u_s_site，但只在「源表每一行都能在 sdsshop 找到」时才删；
--    对不上就跳过删除并原样留着，让你先去查为什么对不上。跑之前先备份：
--       mysqldump -uroot -p --databases sdsstation --tables u_s_site > u_s_site.backup.sql
--    sdsstation 剩下的 5 张表不动——它们要整库 dump 给站点服务器（§10.4 ⑦），
--    最后随 ⑧d 的 DROP DATABASE 一起走。
--
-- 可重复执行：建表 IF NOT EXISTS，搬数据 INSERT IGNORE（主键 shop_code+sitecode），
-- 源表已经删掉时②③自动跳过。重复跑不会覆盖 sdsshop 这边已经改过的行。
-- ============================================================================

-- ---- ① 在 sdsshop 建表：列定义原样照抄旧表，这一步只搬家、不改结构 ----
-- 瘦身（摘掉 contentjson / langs / currency、删 sitekey / apienabled / adminuser）
-- 和加列（服务器 IP、四个子域、跳转密钥）是 §7 那一步的事，不在这个脚本里做。

CREATE TABLE IF NOT EXISTS `sdsshop`.`u_s_site` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码（租户列）',
  `sitecode` varchar(50) NOT NULL COMMENT '站点编码，DLZ + Base10 流水，业务主键',
  `sitekey` varchar(64) DEFAULT NULL COMMENT '对外接口的站点标识，站点前端带着它来取数；不是密钥，只用于限定数据范围',
  `sitename` varchar(100) NOT NULL COMMENT '站点名称，如「英文主站」',
  `domain` varchar(200) DEFAULT NULL COMMENT '站点域名，如 www.example.com；商户内唯一，无值存 NULL 禁止空串',
  `consoleurl` varchar(500) DEFAULT NULL COMMENT '这个站专用的管理台地址（https://...），覆盖全局 ruoyi.station-console.base-url。留空=用全局配置。只允许 http/https',
  `siteurl` varchar(500) DEFAULT NULL COMMENT '站点前台地址。留空时由 domain 拼 https:// 兜底',
  `adminuser` varchar(100) DEFAULT NULL COMMENT '【已废弃】外部后台的登录账号。同 consoleurl，留列不删',
  `ownername` varchar(50) DEFAULT NULL COMMENT '负责人',
  `notifyemail` varchar(500) DEFAULT NULL COMMENT '询盘通知邮箱，多个用逗号分隔，留空则不发',
  `langs` varchar(200) DEFAULT NULL COMMENT '站点语言，逗号分隔，如 en,ru,es',
  `currency` varchar(10) NOT NULL DEFAULT 'USD' COMMENT '站点结算币种，取值与 CurrencySupport 白名单一致',
  `expiredate` date DEFAULT NULL COMMENT '域名/建站服务到期日，用于到期提醒；留空表示不提醒',
  `status` varchar(20) NOT NULL DEFAULT '运行中' COMMENT '站点状态：建设中/运行中/已停用',
  `apienabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否对外提供接口：0 时开放接口一律拒绝，站点会显示取数失败',
  `lastaccesstime` datetime DEFAULT NULL COMMENT '最后一次进站点管理台的时间。内置管理台在 /enter 打点，外部管理台在票据兑换成功时打点',
  `sortnum` int NOT NULL DEFAULT '0' COMMENT '控制台排序，越小越靠前',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `contentjson` mediumtext COMMENT '首页装修内容 JSON：hero/highlights/capabilities/about/nav，为空时接口返回内置默认',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`),
  UNIQUE KEY `uk_site_shop_domain` (`shop_code`,`domain`),
  UNIQUE KEY `uk_site_sitekey` (`sitekey`),
  KEY `idx_site_shop_status` (`shop_code`,`status`),
  KEY `idx_site_shop_expire` (`shop_code`,`expiredate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立站站点档案（ERP 侧登记表）';

-- ---- ② 搬数据 ----
-- 列名写全不写 SELECT *：两边列顺序将来会分叉（sdsshop 这张要加子域和跳转密钥），
-- 那时候 SELECT * 会静默错位。
--
-- notifyemail 这一列是 20260831_station_inquiry_notify.sql 加的。按文件名顺序执行时它已经在了；
-- 万一某个环境跳过了那一步，这里按「源表有没有这列」拼 SQL，没有就填 NULL，不让整个脚本挂掉。

SET @src := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'sdsstation' AND TABLE_NAME = 'u_s_site'
);
SET @notify := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'sdsstation' AND TABLE_NAME = 'u_s_site' AND COLUMN_NAME = 'notifyemail'
);
SET @dml := IF(@src = 1,
    CONCAT(
    'INSERT IGNORE INTO sdsshop.u_s_site
       (shop_code, sitecode, sitekey, sitename, domain, consoleurl, siteurl, adminuser,
        ownername, notifyemail, langs, currency, expiredate, status, apienabled,
        lastaccesstime, sortnum, remark, contentjson, createuser, createtime, updateuser, updatetime)
     SELECT
        shop_code, sitecode, sitekey, sitename, domain, consoleurl, siteurl, adminuser,
        ownername, ', IF(@notify = 1, 'notifyemail', 'NULL'), ', langs, currency, expiredate, status, apienabled,
        lastaccesstime, sortnum, remark, contentjson, createuser, createtime, updateuser, updatetime
       FROM sdsstation.u_s_site'),
    'SELECT ''sdsstation.u_s_site 已经不在了，跳过搬数据'' AS msg');
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- ③ 删掉旧表 ----
-- 先对一遍：源表有没有哪一行没搬过去。有就不删——宁可留一张多余的表，
-- 也不能在没对上的情况下把唯一的正本删了。
-- 注意这里只比主键在不在，不比列内容：INSERT IGNORE 的语义是「目标已有这条就不动」，
-- 所以目标那边被人改过的行，本来就该以目标为准。

SET @chk := IF(@src = 1,
    'SELECT COUNT(*) INTO @missing
       FROM sdsstation.u_s_site s
       LEFT JOIN sdsshop.u_s_site d
         ON d.shop_code = s.shop_code AND d.sitecode = s.sitecode
      WHERE d.sitecode IS NULL',
    'SET @missing := 0');
PREPARE stmt FROM @chk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop := IF(@src = 1 AND @missing = 0,
    'DROP TABLE sdsstation.u_s_site',
    IF(@src = 0,
       'SELECT ''sdsstation.u_s_site 已经不在了，跳过'' AS msg',
       'SELECT ''⚠ 有行没搬过去，旧表保留，先查清楚再手工删'' AS msg'));
PREPARE stmt FROM @drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- ④ 验证 ----
-- 期望：sdsshop 那张有数据，sdsstation 那张已经不存在（station_left 为 0）。
SELECT 'sdsshop.u_s_site' AS tbl, COUNT(*) AS rows_cnt FROM sdsshop.u_s_site;

SELECT COUNT(*) AS station_left
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'sdsstation' AND TABLE_NAME = 'u_s_site';

SELECT shop_code, sitecode, sitename, domain, status, notifyemail
FROM sdsshop.u_s_site
ORDER BY shop_code, sitecode;

-- 搬完之后 ERP 侧只认 sdsshop.u_s_site。要确认没有漏网的旧写法：
--     grep -rn "sdsstation\.u_s_site" RuoYi-Vue/ sql/
-- 应当零结果（本文件除外）。
