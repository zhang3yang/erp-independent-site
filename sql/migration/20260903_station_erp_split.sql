-- =====================================================================
-- 独立站与 ERP 拆分：ERP 侧的结构变更
-- =====================================================================
--
-- 依据：docs/独立站与ERP的边界.md（要点 1「ERP 和独立站完全分开部署，
-- 独立站与 ERP 的后端没有任何互通」）。
--
-- ⚠ 这个脚本只跑在 **ERP 那台**。站点服务器上跑的是 sql/station/01_station_schema.sql。
--
-- ---------------------------------------------------------------------
-- ⚠⚠ 顺序不能反，也不能隔天做
-- ---------------------------------------------------------------------
-- boundary §10.4 第⑧步：
--
--     a. u_s_site 搬进 sdsshop（建表 + INSERT SELECT）      ← 本脚本第 1、2 节
--     b. StationSiteMapper.xml 9 处改 sdsshop.，重启，验独立站管理页  ← 代码，已改
--     c. 删那批文件、路由、菜单                              ← 代码 + 本脚本第 4 节
--     d. mysqldump 全量备份，然后 DROP DATABASE sdsstation    ← 本脚本第 5 节（手工）
--
-- a、b 之间 ERP 是**跑不通独立站管理页**的（表已经在新库、跑着的进程还指旧库），
-- 所以这两步要连着做：跑完本脚本第 1~2 节 → 立刻发新版 → 验证 → 再往下。
--
-- ⚠ 第 5 节那个 DROP 是不可逆的，而 u_s_sitegoods 里那些人工录了几个月的商品文案
--   没有别的地方有。**先把数据导到站点服务器上并验证过，再回来跑第 5 节。**
--
-- ---------------------------------------------------------------------
-- 可重复执行
-- ---------------------------------------------------------------------
-- 加列用 information_schema 判存在，UPDATE 带 WHERE，INSERT 用 IGNORE / NOT EXISTS。
-- 同一个脚本在三个环境上跑两遍不会出错。
--
-- ⚠ 唯一不可重复的是第 5 节的 DROP DATABASE，它被注释掉了，需要人工确认后执行。
-- =====================================================================


-- =====================================================================
-- 1. sdsshop 建 u_s_site（登记表）
-- =====================================================================
-- 它以前在独立站那个库里，装着站点的全部信息。现在拆成两半（§7.2）：
--   · 登记信息（域名、服务器 IP、跳转密钥）留 ERP，就是这张表；
--   · 站里的业务数据（装修 JSON、语言、币种、询盘通知邮箱）搬到站点库的 u_s_siteinfo。
--
-- 为什么进 sdsshop 而不是在 ERP 侧留一个只剩一张表的独立站库：它是按 shop_code
-- 分租户的商家资产，和 u_m_buyer / u_m_waregoods 是同一类东西；更实际的理由是，
-- ERP 侧留一个同名的空库，下一个人看到它还在，会以为独立站的数据仍然在这边。

CREATE TABLE IF NOT EXISTS `sdsshop`.`u_s_site` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码（租户列）',
  `sitecode` varchar(50) NOT NULL COMMENT '站点编码。同一个值也存在站点库的 u_s_siteinfo 里，跳转令牌靠它对齐',
  `sitename` varchar(100) NOT NULL COMMENT '站点名称',
  `domain` varchar(200) DEFAULT NULL COMMENT '主域名。商户内唯一，无值存 NULL 禁止空串',
  `datadomain` varchar(200) DEFAULT NULL COMMENT '后端服务域名。留空则取 data.<主域名>',
  `filedomain` varchar(200) DEFAULT NULL COMMENT '文件域名。留空则取 file.<主域名>',
  `consoledomain` varchar(200) DEFAULT NULL COMMENT '管理后台域名。留空则取 console.<主域名>。⚠ 跳转地址由它拼',
  `buyerdomain` varchar(200) DEFAULT NULL COMMENT '买家后台域名。留空则取 buyer.<主域名>。一期只登记',
  `serverip` varchar(100) DEFAULT NULL COMMENT '站点服务器公网 IP，逗号分隔。⚠ 运维找机器用，ERP 不会去连它',
  `consolesecret` varchar(500) DEFAULT NULL COMMENT '跳转令牌签名密钥（AES-GCM 密文，主密钥 OSS_CONFIG_SECRET）。一站一个，和站点库 u_s_siteinfo.consolesecret 同值',
  `ownername` varchar(50) DEFAULT NULL COMMENT '负责人',
  `expiredate` date DEFAULT NULL COMMENT '域名/服务器到期日',
  `status` varchar(20) NOT NULL DEFAULT '启用' COMMENT '启用/停用。⚠ 管的是「还发不发跳转令牌」，不是「前台开不开」',
  `lastaccesstime` datetime DEFAULT NULL COMMENT '最后一次【点了进入管理】的时间。⚠ 不是「进成功了」，别当活跃度指标',
  `sortnum` int NOT NULL DEFAULT '0' COMMENT '列表排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL,
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateuser` varchar(50) DEFAULT NULL,
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`shop_code`,`sitecode`),
  UNIQUE KEY `uk_site_shop_domain` (`shop_code`,`domain`),
  KEY `idx_site_shop_status` (`shop_code`,`status`),
  KEY `idx_site_shop_expire` (`shop_code`,`expiredate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立站登记表';


-- =====================================================================
-- 1.5 表已经建过了：把新列补上
-- =====================================================================
-- ⚠ 上面那句是 CREATE TABLE IF NOT EXISTS，表在就整段跳过——连同新加的那几列。
--   而 sdsshop.u_s_site 多半已经被 20260903_u_s_site_to_sdsshop.sql 建出来了：
--   那个脚本明说「只搬家、不改结构」，建的是老结构（有 sitekey / consoleurl /
--   contentjson，没有四个子域，也没有 consolesecret）。两个脚本按文件名顺序跑下来，
--   结果是表存在、新列没有，StationSiteMapper 一查就报
--   Unknown column 'datadomain'。这一节专门堵这个缺口。
--
-- 老列一列都不删（sitekey / consoleurl / siteurl / adminuser / apienabled /
-- langs / currency / notifyemail / contentjson）：contentjson 里是人工录了几个月的
-- 站点文案，得先 dump 给站点服务器（§10.4 ⑦）。mapper 是按列名取的，多几列不影响。
-- 清理等那批数据确认落地之后再单独做。

DROP PROCEDURE IF EXISTS sdsshop.add_site_column;
DELIMITER $
CREATE PROCEDURE sdsshop.add_site_column(IN col VARCHAR(64), IN ddl TEXT)
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = 'sdsshop' AND table_name = 'u_s_site'
         AND column_name = col) = 0 THEN
    SET @s = CONCAT('ALTER TABLE `sdsshop`.`u_s_site` ADD COLUMN `', col, '` ', ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$
DELIMITER ;

-- 列序跟上面的建表语句对齐，新库和老库升上来的库看到的是同一个顺序
CALL sdsshop.add_site_column('datadomain',
  "varchar(200) DEFAULT NULL COMMENT '后端服务域名。留空则取 data.<主域名>' AFTER `domain`");
CALL sdsshop.add_site_column('filedomain',
  "varchar(200) DEFAULT NULL COMMENT '文件域名。留空则取 file.<主域名>' AFTER `datadomain`");
CALL sdsshop.add_site_column('consoledomain',
  "varchar(200) DEFAULT NULL COMMENT '管理后台域名。留空则取 console.<主域名>。⚠ 跳转地址由它拼' AFTER `filedomain`");
CALL sdsshop.add_site_column('buyerdomain',
  "varchar(200) DEFAULT NULL COMMENT '买家后台域名。留空则取 buyer.<主域名>。一期只登记' AFTER `consoledomain`");
CALL sdsshop.add_site_column('serverip',
  "varchar(100) DEFAULT NULL COMMENT '站点服务器公网 IP，逗号分隔。⚠ 运维找机器用，ERP 不会去连它' AFTER `buyerdomain`");
CALL sdsshop.add_site_column('consolesecret',
  "varchar(500) DEFAULT NULL COMMENT '跳转令牌签名密钥（AES-GCM 密文，主密钥 OSS_CONFIG_SECRET）。一站一个，和站点库 u_s_siteinfo.consolesecret 同值' AFTER `serverip`");

DROP PROCEDURE IF EXISTS sdsshop.add_site_column;

-- consoleurl（老列，完整 URL）→ consoledomain（新列，只存域名）。
-- 只填空着的行，不覆盖已经手工填过的；老列本身留着不动。
-- 剥不干净也不要紧，StationSite.subDomain 读出来时还会再剥一次。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'sdsshop' AND table_name = 'u_s_site'
                   AND column_name = 'consoleurl') > 0,
  'UPDATE sdsshop.u_s_site
      SET consoledomain = NULLIF(TRIM(TRAILING "/" FROM
            REPLACE(REPLACE(COALESCE(consoleurl, ""), "https://", ""), "http://", "")), "")
    WHERE (consoledomain IS NULL OR consoledomain = "")
      AND consoleurl IS NOT NULL AND consoleurl <> ""',
  'SELECT "没有 consoleurl 老列，跳过" AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 状态三态 → 两态，跟建表语句里的默认值一起对齐。
-- Java 侧 StationSite.normalizeStatus 读写时也会折一次，这里只是别让库里留着
-- 「界面显示启用、库里存的是运行中」这种对不上的值。
UPDATE sdsshop.u_s_site SET status = '启用' WHERE status IN ('建设中', '运行中');
UPDATE sdsshop.u_s_site SET status = '停用' WHERE status = '已停用';
ALTER TABLE sdsshop.u_s_site
  MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT '启用'
  COMMENT '启用/停用。⚠ 管的是「还发不发跳转令牌」，不是「前台开不开」';


-- =====================================================================
-- 2. 把老库里的登记信息搬过来
-- =====================================================================
-- 只搬登记那几列。contentjson / langs / currency / notifyemail 不搬——
-- 它们的去处是站点服务器，走 mysqldump 那条路（§10.4 第⑦步）。
--
-- consolesecret 留空：老库里根本没有这一列，密钥是这次才引入的。
-- 搬完之后要**逐个站在 ERP 界面上点「重置跳转密钥」**，把生成的明文填进
-- 那台站点服务器的 u_s_siteinfo.consolesecret。在那之前，那些站的「进入管理」进不去。
--
-- consoleurl（老库那一列）→ consoledomain：老值是完整 URL（https://console.a.com），
-- 新列存的是**域名**，所以要剥掉协议头和末尾斜杠。剥不干净也不要紧——
-- StationSite.subDomain 读出来时还会再剥一次。
--
-- 用 NOT EXISTS 而不是 INSERT IGNORE：IGNORE 会把「主键冲突」和「字段截断」
-- 一起忽略掉，而后者是真的丢数据。

SET @has_old_site := (SELECT COUNT(*) FROM information_schema.TABLES
                       WHERE TABLE_SCHEMA = 'sdsstation' AND TABLE_NAME = 'u_s_site');

SET @sql := IF(@has_old_site > 0,
  'INSERT INTO sdsshop.u_s_site
     (shop_code, sitecode, sitename, domain, consoledomain, ownername,
      expiredate, status, lastaccesstime, sortnum, remark,
      createuser, createtime, updateuser, updatetime)
   SELECT o.shop_code, o.sitecode, o.sitename, o.domain,
          NULLIF(TRIM(TRAILING "/" FROM
            REPLACE(REPLACE(COALESCE(o.consoleurl, ""), "https://", ""), "http://", "")), ""),
          o.ownername, o.expiredate,
          CASE WHEN o.status = "已停用" THEN "停用"
               WHEN o.status = "停用"   THEN "停用"
               ELSE "启用" END,
          o.lastaccesstime, o.sortnum, o.remark,
          o.createuser, o.createtime, o.updateuser, o.updatetime
     FROM sdsstation.u_s_site o
    WHERE NOT EXISTS (SELECT 1 FROM sdsshop.u_s_site n
                       WHERE n.shop_code = o.shop_code AND n.sitecode = o.sitecode)',
  'SELECT "老库里没有 u_s_site，跳过搬迁（新环境或已经搬过）" AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 搬完对一下数：两边条数应当一致
SET @sql := IF(@has_old_site > 0,
  'SELECT (SELECT COUNT(*) FROM sdsstation.u_s_site) AS 老库站点数,
          (SELECT COUNT(*) FROM sdsshop.u_s_site)    AS 新库站点数',
  'SELECT "跳过" AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 3. 月表：buyeruser / buyershop 默认值 NULL → 空串
-- =====================================================================
-- 要点 6（原文）：「ERP 系统中订单和客户中的 buyeruser 和 buyershop 暂时保留不删，
-- 内容默认空串即可。」
--
-- ⚠ 这一节和 MonthlyTableInitializer.buildOrderDDL 必须一起改，而且**存量月表要挨个 ALTER**。
--   只改 builder 不改存量表，会出现「这个月好好的、上个月不对」的诡异 bug——
--   20260828_receive_status_default.sql 修的就是同一类问题，
--   sql/migration/README.md 里也写了这条规矩。
--
-- 下面这段游标把 sdsorder 下所有 u_o_order_YYYYMM 挨个过一遍。

DROP PROCEDURE IF EXISTS sdsorder.fix_buyer_columns;
DELIMITER $$
CREATE PROCEDURE sdsorder.fix_buyer_columns()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = 'sdsorder' AND TABLE_NAME REGEXP '^u_o_order_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname;
    IF done = 1 THEN LEAVE read_loop; END IF;

    -- 先把已有的 NULL 补成空串，再改列定义。
    -- 顺序不能反：列上有 NOT NULL 之后，存量的 NULL 会让 ALTER 失败
    -- （严格模式下）或者被静默改成空串（非严格模式下）——后者更糟，
    -- 因为它让这一步的成功与否取决于会话的 sql_mode。
    SET @s := CONCAT('UPDATE sdsorder.`', tname, '` SET buyeruser = "" WHERE buyeruser IS NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    SET @s := CONCAT('UPDATE sdsorder.`', tname, '` SET buyershop = "" WHERE buyershop IS NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    SET @s := CONCAT('ALTER TABLE sdsorder.`', tname, '` ',
      'MODIFY COLUMN buyershop varchar(50) NOT NULL DEFAULT "" COMMENT "买家商家代码。⚠ 默认空串，见 boundary 要点 6", ',
      'MODIFY COLUMN buyeruser varchar(50) NOT NULL DEFAULT "" COMMENT "个人买家代码或商家买家员工代码。⚠ 默认空串，见 boundary 要点 6"');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL sdsorder.fix_buyer_columns();
DROP PROCEDURE sdsorder.fix_buyer_columns;

-- u_m_buyer 上那两列同样处理。这张不是月表，一条语句就够
UPDATE sdsshop.u_m_buyer SET buyeruser = '' WHERE buyeruser IS NULL;
UPDATE sdsshop.u_m_buyer SET buyershop = '' WHERE buyershop IS NULL;

ALTER TABLE sdsshop.u_m_buyer
  MODIFY COLUMN buyershop varchar(50) NOT NULL DEFAULT '' COMMENT '买家商家代码。⚠ 默认空串，见 boundary 要点 6',
  MODIFY COLUMN buyeruser varchar(50) NOT NULL DEFAULT '' COMMENT '买家用户代码。⚠ 默认空串，见 boundary 要点 6';


-- =====================================================================
-- 4. 菜单：删掉搬走的三棵子树，站点控制台改名
-- =====================================================================
-- 商品上架（fn9010）、询盘管理（fn9020）、文件资源（fn9030）连同它们的按钮权限
-- 都搬到站点自己的管理台去了（§10.1）。
--
-- 顺序：先删关联表再删菜单表——sys_role_menu / sys_shop_menu 上有外键的话，
-- 反过来会被外键挡住。

DELETE FROM sdssys.sys_role_menu WHERE fun_code REGEXP '^fn90[123][0-9]$';
DELETE FROM sdssys.sys_shop_menu WHERE fun_code REGEXP '^fn90[123][0-9]$';
DELETE FROM sdssys.sys_menu      WHERE fun_code REGEXP '^fn90[123][0-9]$';

UPDATE sdssys.sys_menu
   SET menu_name = '独立站管理',
       remark = '独立站登记表：有哪些站、在哪、怎么进。站里的内容在各站自己的管理台改'
 WHERE fun_code = 'fn9001';


-- =====================================================================
-- 5. ⚠⚠ 最后一步：删掉 ERP 侧的独立站库
-- =====================================================================
-- **故意注释掉。**
--
-- ---------------------------------------------------------------------
-- ⚠⚠⚠ 先看这一条：开发机上这句会删掉外置独立站自己的库
-- ---------------------------------------------------------------------
-- 这一节是按「ERP 一台、站点一台」写的：那种拓扑下 ERP 上的 sdsstation 是搬走后的
-- 残骸，删掉才对。
--
-- 但**开发机上只有一个 MySQL 实例**，station-console 连的也是本机的 sdsstation。
-- 2026-09-03 在本机实测，那个库里已经有站点侧自己的表：
--
--     u_s_siteinfo  u_s_user  u_s_role  u_s_storage  u_s_sequence
--     u_s_sitegoods  u_s_sitegoods_lang  u_s_siteimage  u_s_sitevideo  u_s_inquiry
--
-- 也就是说它同时是「ERP 侧的残骸」和「站点侧的正式库」。在本机执行这句
-- DROP，等于把刚建好的独立站后端连库一起删了。
--
-- 判断办法：执行前先确认这台机器上**没有** station-console 在跑、
-- 并且 sdsstation 里没有 u_s_siteinfo 这张表（它只属于站点侧）：
--
--     SELECT COUNT(*) FROM information_schema.TABLES
--      WHERE TABLE_SCHEMA='sdsstation' AND TABLE_NAME='u_s_siteinfo';
--
-- 结果不是 0，就说明这个库是站点侧在用的，**不要删**。
-- ---------------------------------------------------------------------
--
-- 拓扑确认过是「两台机器」之后，还要再确认三件事：
--
--   1. 站点服务器上的库已经建好、数据导进去了、前台和管理台都验过；
--   2. 这份备份存在、而且**验证过能恢复**：
--
--          mysqldump -h <ERP> -u root -p --single-transaction \
--            --databases sdsstation > sdsstation_final_$(date +%Y%m%d).sql
--
--      §10.4 第⑧d 步：「那份备份是这批数据在 ERP 侧的最后一份，
--      u_s_sitegoods 里那些人工录了几个月的商品文案没有别的地方有。」
--
--   3. ERP 新版已经发上去并且跑通了——旧版的 mapper 还指着这个库，
--      库一没就是满屏 Unknown database。
--
-- 三条都确认了，再手工执行下面这一句：
--
--     DROP DATABASE sdsstation;
--
-- 执行完之后 §13 第 7 条就成立了：ERP 服务器上 SHOW DATABASES 里没有它，
-- 站点服务器上有，且里面没有 u_s_site。


-- =====================================================================
-- 6. 验收
-- =====================================================================
-- 跑完之后这几条应当都对得上：

SELECT '登记表条数' AS 项, COUNT(*) AS 值 FROM sdsshop.u_s_site
UNION ALL
SELECT '还没配跳转密钥的站（这些站进不了管理台）', COUNT(*) FROM sdsshop.u_s_site
 WHERE consolesecret IS NULL OR consolesecret = ''
UNION ALL
SELECT '还没登记域名的站（「进入管理」按钮是灰的）', COUNT(*) FROM sdsshop.u_s_site
 WHERE (domain IS NULL OR domain = '') AND (consoledomain IS NULL OR consoledomain = '')
UNION ALL
SELECT '残留的站点子菜单（应为 0）', COUNT(*) FROM sdssys.sys_menu
 WHERE fun_code REGEXP '^fn90[123][0-9]$'
UNION ALL
SELECT 'u_m_buyer 里 buyeruser 仍为 NULL 的（应为 0）', COUNT(*) FROM sdsshop.u_m_buyer
 WHERE buyeruser IS NULL;
