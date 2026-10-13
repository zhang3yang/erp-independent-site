-- =====================================================================
-- 买家收货地址簿：新建 u_s_buyeraddr
-- =====================================================================
--
-- 库：sdsstation。可重复执行（CREATE TABLE IF NOT EXISTS）。
--
-- 起因：前台买家能登录了（2026-09-04-buyer-login.sql），但地址仍然只在结账页
-- 填一次，存在**买家自己浏览器**的 localStorage `station-buyer` 里
-- （station-site/app/pages/checkout/index.vue）。换台电脑、清一次缓存就没了——
-- 而登录这件事的全部意义就是「换台电脑还在」。
--
-- =====================================================================
-- ⚠ 为什么是一张新表，而不是往 u_s_buyer 上加几列
-- =====================================================================
--
-- 外贸买家一个人对多个收货地：工厂、货代仓库、展会现场、客户指定的第三方港口，
-- 是常态不是特例。加列只能存一个，那等于每次下单还是要重填。
--
-- =====================================================================
-- ⚠ 列名和 u_s_order 的目的地那一组**一字不差**
-- =====================================================================
--
--   contactname / phone / addrline1 / addrline2 / city / region / postal / country
--
-- 这不是巧合：地址簿里选一条 → 填进结账表单 → 提交后落进 u_s_order 的那几列。
-- 名字对齐的话中间一层映射都不用写，也就没有「line1 抄成 addrline1」这类错。
-- 加字段时请**同时看** u_s_order，别让两边漂开。
--
-- =====================================================================
-- ⚠ 这张表存的是「买家自己填的地址」，不是订单地址
-- =====================================================================
--
-- 订单上的地址仍然是**下单当时的快照**（u_s_order 那几列），和这张表没有外键、
-- 也不 join。买家事后改地址簿，历史订单上的收货地不能跟着变——
-- 那批货已经按旧地址发出去了。理由和 u_s_orderitem 存商品快照是同一条。
--
-- =====================================================================
-- 为什么没有 addrcode（这张表用自增 id 对外）
-- =====================================================================
--
-- 别的表都发业务码（DD/KH/LT），这张不发：
--
--   · 发号要在 u_s_sequence 里加一行，而漏加那一行的表现是「用户点保存报 500」
--     （CodeGenerator#next 那句「发号器里没有这一行」），为一张内部表添一个部署坑不值得；
--   · 这个 id 不出现在任何单据、邮件、对外沟通里，没有「等宽好念」的需求。
--
-- ⚠ 代价是 id 连号可猜。所以**每一条 SQL 的 where 里都必须带 buyercode**
--   （见 SiteBuyerAddressMapper.xml），少一处就是「改一个数字就能读到别人的收货地址」。
--   服务端从会话取 buyercode，绝不从参数取。
--
-- =====================================================================
-- 为什么「默认地址」是一个 tinyint 而不是 u_s_buyer 上的一列外键
-- =====================================================================
--
-- 放在这张表上，删一条地址时默认标记跟着那一行一起没了；放在 u_s_buyer 上则会留下
-- 一个指向已删行的悬空编号，然后结账页选不出默认地址、也没人看得出为什么。
--
-- ⚠ 「一个买家只能有一条默认」靠**服务层的事务**保证（先清零再置一），
--   不靠唯一索引：MySQL 8 没有部分唯一索引，(buyercode, isdefault) 上做唯一
--   会连「两条非默认」都插不进去。
-- =====================================================================

USE `sdsstation`;

CREATE TABLE IF NOT EXISTS `u_s_buyeraddr` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键。⚠ 它就是对外的地址 id，见文件头「为什么没有 addrcode」',
  `shop_code`   varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】恒为建站时那个商户码，新代码不得读它',
  `sitecode`    varchar(50)  NOT NULL COMMENT '来自哪个站点，恒等于 u_s_siteinfo.sitecode',
  `buyercode`   varchar(50)  NOT NULL COMMENT '属于谁，指向 u_s_buyer.buyercode。⚠ 每一条查询的 where 里都必须带它',

  `label`       varchar(50)           DEFAULT NULL COMMENT '买家给这条地址起的名字，如 Warehouse / Head office。选填，空的话前台显示国家+城市',

  -- ---- 收件人。⚠ 和 u_s_order 的联系人列同名同宽 ----
  `contactname` varchar(100) NOT NULL COMMENT '收件人',
  `phone`       varchar(50)           DEFAULT NULL COMMENT '收件电话 / WhatsApp。清关和派送要，但强制填会挡住只想问价的人，所以选填',

  -- ---- 地址。⚠ 和 u_s_order 的目的地那一组同名同宽，见文件头 ----
  `addrline1`   varchar(200)          DEFAULT NULL COMMENT '地址第一行',
  `addrline2`   varchar(200)          DEFAULT NULL COMMENT '地址第二行',
  `city`        varchar(80)           DEFAULT NULL COMMENT '城市',
  `region`      varchar(80)           DEFAULT NULL COMMENT '州 / 省',
  `postal`      varchar(30)           DEFAULT NULL COMMENT '邮编',
  `country`     varchar(80)  NOT NULL COMMENT '国家。唯一必填的地址字段——和 u_s_order 一致，估运费最少要它',

  `isdefault`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是不是默认地址。⚠ 一人一条靠服务层事务保证，不靠唯一索引，见文件头',

  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 列出某个买家的地址簿：默认的排在最前，其余按新建时间倒排。
  -- isdefault 放在 buyercode 后面，这一条索引就把「查他的」和「默认的在前」一起满足了
  KEY `idx_buyeraddr_owner` (`shop_code`,`buyercode`,`isdefault`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='买家收货地址簿。⚠ 订单上的地址是下单当时的快照，不 join 这张表';


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
     VALUES (''2026-09-08-buyer-address.sql'', ''claude'', ''执行'',
             ''建 u_s_buyeraddr（买家收货地址簿）'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');

PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 上线顺序：**这个脚本必须跑在换 jar 之前**
-- =====================================================================
--
--   1. 跑这个脚本建表
--   2. 换 station-console 的 jar 并重启（读写这张表的是新代码：
--      SiteBuyerAddressMapper / SiteBuyerAddressService）
--   3. 发前台产物（account 页的地址簿、结账页的地址下拉）
--
-- ⚠ 顺序反了**不会有任何东西报警**，这一点值得单独说清楚：
--
--   · MyBatis 启动时只**解析** mapper XML，不校验表存不存在。所以表没建，
--     jar 照样起得来；
--   · 服务器上 deploy3.ps1 那道「先在 8099 端口试起一次」的闸，验的是
--     「新 jar 能不能起来」，同样发现不了这件事，冒烟照样通过。
--
--   炸的时间点是**第一个买家点开账户页**——那时候已经上线了。
--
--   也就是说，挡住这个的只有「先跑迁移」这条人守的顺序。换 jar 之前跑一句确认：
--
--       select count(*) from information_schema.tables
--        where table_schema = 'sdsstation' and table_name = 'u_s_buyeraddr';
--
--   不是 1 就停在这里，别往下走。
-- =====================================================================
