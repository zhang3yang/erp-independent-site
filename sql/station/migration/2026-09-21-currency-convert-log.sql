-- =====================================================================
-- 改结算币种时按汇率换算金额：留一份换算流水
-- 2026-09-21
-- =====================================================================
--
-- 在这之前，管理台「站点概览 → 结算币种」改的只是一个三字母码：商品价、型号价、
-- 订单金额的**数字一个不动**，只有符号变了。也就是说把 GBP 改成 USD，一个
-- 标价 £100 的商品会当场变成 $100 —— 页面上没有任何提示，而它是一次实打实的调价。
--
-- 本次改成：改币种时按当前汇率把四处金额一起换算。
--
--   u_s_sitegoods.price        商品价
--   u_s_sitegoods_combo.price  型号价
--   u_s_orderitem.price        订单行单价（连 currency 列一起改）
--   u_s_orderitem.linetotal    行小计，按新单价重算
--   u_s_order.subtotal         订单小计，重算为 sum(linetotal)
--   u_s_order.currency         订单币种
--
-- ---------------------------------------------------------------------
-- ⚠ 这张表存在的唯一理由：换算不可逆
-- ---------------------------------------------------------------------
--
-- 换算是「乘一个汇率、四舍五入到 4 位」，原值不留副本。改回去只能除以同一个汇率，
-- 而那个汇率是**换算当天的** —— 隔一天再改回来用的是新汇率，数字回不到原处。
--
-- 所以每次换算在这里落一行，记死当时用的汇率。要还原，拿 rate 去除，
-- 而不是拿「今天的汇率」去除。这两件事看着一样，结果不一样。
--
-- ⚠ 即使如此也只是**近似**还原：round(x * rate, 4) 丢掉的小数位追不回来。
--   真要精确回滚，只有换算前的库备份。所以管理台那个确认框里写了
--   「先备份」四个字，不是客套话。
--
-- ---------------------------------------------------------------------
-- 为什么不存换算前的全部金额
-- ---------------------------------------------------------------------
--
-- 那是一份全表快照：商品 + 型号 + 所有历史订单行。一个跑了两年的站，
-- 光订单行就是几万条，而改币种是个极低频操作（现场记录：零次）。
-- 为一件几乎不发生的事常备一套快照机制，成本花在了错的地方。
--
-- 真要那份数据，换算前 mysqldump 一次，比这张表可靠得多。
--
-- ---------------------------------------------------------------------
-- 可重复执行：CREATE TABLE IF NOT EXISTS。
--
-- ⚠ 线上库 sdsstation，用 mysql 客户端跑的话记得 --default-character-set=utf8mb4。
-- =====================================================================

USE `sdsstation`;

CREATE TABLE IF NOT EXISTS `u_s_currency_convert_log` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `fromcode`   varchar(10)  NOT NULL COMMENT '换算前的结算币种',
  `tocode`     varchar(10)  NOT NULL COMMENT '换算后的结算币种',
  `rate`       decimal(24,10) NOT NULL COMMENT '⚠ 本表的核心：1 个 fromcode 换多少个 tocode，换算当时的值。还原就是除以它——不是除以今天的汇率',
  `goodsrows`  int          NOT NULL DEFAULT 0 COMMENT 'u_s_sitegoods 改了几行。⚠ 只数 price>0 的：0 表示面议，不参与换算',
  `comborows`  int          NOT NULL DEFAULT 0 COMMENT 'u_s_sitegoods_combo 改了几行，同样只数 price>0',
  `itemrows`   int          NOT NULL DEFAULT 0 COMMENT 'u_s_orderitem 改了几行。⚠ 这个数含面议行：它们的 currency 列也要跟着改，哪怕金额是 0',
  `orderrows`  int          NOT NULL DEFAULT 0 COMMENT 'u_s_order 改了几行',
  `mismatch`   int          NOT NULL DEFAULT 0 COMMENT '换算前就已经 subtotal != sum(linetotal) 的订单数。>0 说明这次换算顺手把它们改一致了——那是历史数据本来就不自洽，不是这次换算造成的，但得有地方看得见',
  `operator`   varchar(50)           DEFAULT NULL COMMENT '谁改的',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '换算时间',
  PRIMARY KEY (`id`),
  KEY `idx_ccl_site_time` (`sitecode`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算币种换算流水。改币种时按当时汇率把商品价/型号价/订单金额一起乘了多少，记在这里——换算不可逆，这是唯一的还原依据';


-- ---------------------------------------------------------------------
-- 跑完登记一行（台账见 2026-09-08-schema-migration-ledger.sql）：
--
--   INSERT INTO u_s_schema_migration (filename, appliedby, source, note)
--   VALUES ('2026-09-21-currency-convert-log.sql', '你的名字', '执行', NULL);
--
-- 查某个站换过几次币种、每次用的什么汇率：
--
--   SELECT createtime, fromcode, tocode, rate, goodsrows, itemrows, operator
--     FROM u_s_currency_convert_log ORDER BY id DESC;
-- ---------------------------------------------------------------------
