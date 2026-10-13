-- 库存盘点 + 盘盈入库 / 盘亏出库两种调整单据（2026-09-25）。可重复执行。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 1. sdsshop 新建两张表：盘点单头 u_m_stocktake、盘点明细 u_m_stocktake_item。
--    不按月分表：一个仓库一年盘不了几次，量级和单据月表不是一回事。
--
-- 2. u_m_doctypeconfig 加两种库存单据：
--
--      PYRK  盘盈入库  I   实盘比账面多出来的部分
--      PKCK  盘亏出库  O   实盘比账面少掉的部分
--
--    这两种单据只由盘点单审核时生成并当场过账，出入库页面上不能手工开——
--    调整库存必须留下「账面多少、实盘多少、谁盘的、谁批的」这一整条证据，
--    手工开一张盘亏出库单等于绕过盘点直接改数。
--
-- 3. sdssys 菜单：库存管理 → 库存变动 下加「库存盘点」页面和三个按钮权限。
--
--      fn5108  库存盘点     C  inventory:stocktake:list
--      fn5109  盘点查询     F  inventory:stocktake:query
--      fn5110  盘点录入     F  inventory:stocktake:add      新建、录入实盘数、提交、作废
--      fn5111  盘点审核     F  inventory:stocktake:approve  审核通过（生成调整单并过账）、驳回
--
-- ============================================================================
-- 盘点期间冻结
-- ============================================================================
--
-- 盘点单从建单到审核完成（状态「盘点中」「待审核」），单上的商品在这个仓库里
-- 不能过账任何库存单据（发货完成、入库审核、调拨审核……都会被拦下并提示盘点单号）。
-- 建单、改单不受影响，只是审核要等盘点结束。
--
-- 不冻结的话：清点到一半发走一批货，实盘数少了、出库又扣了一次，同一批货被扣两遍。
-- 判断写在 InventoryDocumentPostingService 里，所有过账都经过那一处。
--
-- ============================================================================
-- ⚠ 必须和代码一起发布
-- ============================================================================
--
-- 只跑 SQL 不发代码：菜单点进去 404，其余无影响。
-- 只发代码不跑 SQL：盘点接口报表不存在；过账时的冻结检查也查这张表，
--   会让**所有**库存单据审核失败。所以这个脚本要先跑。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260925_stocktake.sql
--   mysql -h 124.221.220.89 -uroot -p --default-character-set=utf8mb4 < 20260925_stocktake.sql

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 盘点表
-- ----------------------------------------------------------------------------
USE `sdsshop`;

CREATE TABLE IF NOT EXISTS `u_m_stocktake` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `stocktake_code` varchar(50) NOT NULL COMMENT '盘点单号，PD 前缀',
  `ware_code` varchar(50) NOT NULL COMMENT '盘点仓库',
  `ware_name` varchar(100) DEFAULT NULL COMMENT '仓库名称快照',
  `scope` varchar(20) NOT NULL DEFAULT 'all' COMMENT '范围：all 全仓 / goods 指定商品',
  `status` varchar(20) NOT NULL COMMENT '盘点中 / 待审核 / 已完成 / 已作废',
  `line_count` int NOT NULL DEFAULT '0' COMMENT '明细行数',
  `diff_count` int NOT NULL DEFAULT '0' COMMENT '有差异的行数，审核时回写',
  `gain_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '盘盈数量合计',
  `gain_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '盘盈金额合计',
  `loss_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '盘亏数量合计（正数）',
  `loss_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '盘亏金额合计（正数，按出库实际成本）',
  `adjust_doc_codes` varchar(500) DEFAULT NULL COMMENT '审核生成的盘盈入库/盘亏出库单号，逗号分隔',
  `adjust_table_suffix` varchar(6) DEFAULT NULL COMMENT '调整单所在月表后缀',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '最近一次驳回原因',
  `create_user` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即账面快照时间）',
  `submit_user` varchar(50) DEFAULT NULL COMMENT '提交人',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `confirm_user` varchar(50) DEFAULT NULL COMMENT '审核人',
  `confirm_time` datetime DEFAULT NULL COMMENT '审核时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_code` (`shop_code`,`stocktake_code`),
  KEY `idx_stocktake_ware_status` (`shop_code`,`ware_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存盘点单';

CREATE TABLE IF NOT EXISTS `u_m_stocktake_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码',
  `stocktake_code` varchar(50) NOT NULL COMMENT '盘点单号',
  `goods_code` varchar(50) NOT NULL COMMENT '商品代码',
  `goods_name` varchar(100) DEFAULT NULL COMMENT '商品名称快照',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格快照',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位快照',
  `qualityflag` tinyint NOT NULL DEFAULT '1' COMMENT '良品状态：1 良品 / 0 不良品。只盘在库的货',
  `book_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '账面数量：建单时快照，审核时按当前库存刷新',
  `counted_qty` decimal(18,4) DEFAULT NULL COMMENT '实盘数量，NULL 表示还没盘',
  `diff_qty` decimal(18,4) DEFAULT NULL COMMENT '差异 = 实盘 - 账面，审核时回写',
  `cost_price` decimal(18,6) DEFAULT NULL COMMENT '盘盈入库成本单价，默认取账面均价；盘亏按出库实际成本结算',
  `diff_amount` decimal(18,2) DEFAULT NULL COMMENT '差异金额（盘盈为正、盘亏为负），审核时回写',
  `batch_code` varchar(50) DEFAULT NULL COMMENT '盘亏出自哪个批次，只有个别计价的批次商品要填',
  `serial_nos` text COMMENT '序列号商品：盘亏丢了哪几个 / 盘盈多出哪几个，逗号或换行分隔',
  `remark` varchar(500) DEFAULT NULL COMMENT '差异原因等',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_item` (`shop_code`,`stocktake_code`,`goods_code`,`qualityflag`),
  KEY `idx_stocktake_item_goods` (`shop_code`,`goods_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存盘点明细';

-- ----------------------------------------------------------------------------
-- 2. 两种调整单据
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`)
VALUES ('PYRK', '盘盈入库', 'I', 1, 0, '只由盘点单审核生成并当场过账，出入库页面不能手工开');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`)
VALUES ('PKCK', '盘亏出库', 'O', 1, 0, '只由盘点单审核生成并当场过账，出入库页面不能手工开');

-- ----------------------------------------------------------------------------
-- 3. 菜单
-- ----------------------------------------------------------------------------
USE `sdssys`;

INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn5108', 'fn5092', '库存盘点', 8, 'stocktake', 'inventory/stocktake/index', '', '', 1, 0, 'C', '0', '0',
   'inventory:stocktake:list', 'checkbox', 'wczladmin', sysdate(), '盘点单：账面快照→录实盘→审核生成盘盈入库/盘亏出库'),
  ('fn5109', 'fn5108', '盘点查询', 1, '#', '', '', '', 1, 0, 'F', '0', '0',
   'inventory:stocktake:query', '#', 'wczladmin', sysdate(), ''),
  ('fn5110', 'fn5108', '盘点录入', 2, '#', '', '', '', 1, 0, 'F', '0', '0',
   'inventory:stocktake:add', '#', 'wczladmin', sysdate(), '新建、录入实盘数、提交、作废'),
  ('fn5111', 'fn5108', '盘点审核', 3, '#', '', '', '', 1, 0, 'F', '0', '0',
   'inventory:stocktake:approve', '#', 'wczladmin', sysdate(), '审核通过即生成调整单并过账，改的是真库存');

-- 页面、查询、录入：发给现在能开出库单的角色（库管员）
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT DISTINCT rm.`role_code`, m.`fun_code`
  FROM `sys_role_menu` rm
  CROSS JOIN (SELECT 'fn5108' AS fun_code
              UNION ALL SELECT 'fn5109'
              UNION ALL SELECT 'fn5110') m
 WHERE rm.`fun_code` = 'fn5018';

-- 审核：发给现在能审核出库单的角色。盘亏审核和出库审核改的是同一样东西
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT DISTINCT rm.`role_code`, m.`fun_code`
  FROM `sys_role_menu` rm
  CROSS JOIN (SELECT 'fn5108' AS fun_code
              UNION ALL SELECT 'fn5109'
              UNION ALL SELECT 'fn5111') m
 WHERE rm.`fun_code` = 'fn5019';

-- 挂到现有的全部商家，否则角色上勾了也不算数（见 20260911_dashboard_menu.sql 第 4 步）
INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn5108' AS fun_code
              UNION ALL SELECT 'fn5109'
              UNION ALL SELECT 'fn5110'
              UNION ALL SELECT 'fn5111') m;

-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
SELECT `doctype`, `doctypename`, `direction` FROM `sdsshop`.`u_m_doctypeconfig` WHERE `doctype` IN ('PYRK', 'PKCK');
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `perms` FROM `sys_menu`
 WHERE `fun_code` IN ('fn5108', 'fn5109', 'fn5110', 'fn5111') ORDER BY `fun_code`;
SELECT `fun_code`, count(*) AS roles FROM `sys_role_menu`
 WHERE `fun_code` IN ('fn5108', 'fn5109', 'fn5110', 'fn5111') GROUP BY `fun_code`;
