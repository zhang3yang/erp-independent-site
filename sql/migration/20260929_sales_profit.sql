-- 销售利润报表 + 汇率设置（2026-09-29）。可重复执行。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 1. sdsshop 新建汇率表 u_m_exchange_rate：每个商家、每种外币按「生效日期」记一串汇率，
--    含义是 1 单位外币 = 多少人民币。本位币是 CNY，不用录。
--
--    为什么带生效日期而不是一个币种一个数：利润按每笔发货的日期取当天有效的汇率折算。
--    只存一个数的话，10 月改了汇率，9 月已经发出去的单利润也跟着变，报表前后对不上。
--
-- 2. sdssys 菜单：
--
--      fn7004  汇率设置   C  finance:rate:list    财务管理下
--      fn7005  汇率维护   F  finance:rate:edit    新增、修改、删除
--      fn8060  销售利润   C  order:profit:list    订单管理下；接口另要 price:sale 和 price:cost
--
--    汇率发给能看资金流水（fn7001）的角色；
--    销售利润只发给同时有销售价（fn9042）和成本价（fn9041）权限的角色——
--    利润 = 售价 − 成本，两边少看一边都算不出来，也不该看到。
--
-- ============================================================================
-- 口径（详见 SalesProfitServiceImpl 类注释）
-- ============================================================================
--   收入 = 发货数量 × 订单单价，按出库单日期（即发货批次完成日）计入
--   成本 = 出库单过账时的实际成本（订单明细上的成本价一直是 0，不用它）
--   退货冲减收入和成本；补发只有成本，记在原订单上
--   收客户的运费按发货进度分摊；付承运商的运费记在对应发货批次上
--   外币收入按发货当天有效的汇率折人民币；没有汇率的单不计入，在报表顶部列出来
--
-- ============================================================================
-- 执行（先 SQL 后代码；只发代码不跑 SQL，汇率页和利润页报表不存在，其余不受影响）
-- ============================================================================
--   mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < 20260929_sales_profit.sql

SET NAMES utf8mb4;

USE `sdsshop`;

CREATE TABLE IF NOT EXISTS `u_m_exchange_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码',
  `currency` varchar(10) NOT NULL COMMENT '外币代码，如 USD',
  `rate` decimal(18,6) NOT NULL COMMENT '1 单位外币 = 多少人民币',
  `effective_date` date NOT NULL COMMENT '生效日期：这一天及以后的发货用这个汇率，直到下一条生效',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注，如取自哪天的银行结汇价',
  `create_user` varchar(50) DEFAULT NULL COMMENT '录入人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate` (`shop_code`,`currency`,`effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率（外币折人民币）';

USE `sdssys`;

INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn7004', 'fn7000', '汇率设置', 4, 'rate', 'finance/rate/index', '', '', 1, 0, 'C', '0', '0',
   'finance:rate:list', 'international', 'wczladmin', sysdate(), '外币折人民币的汇率，按生效日期记；销售利润报表用它折算'),
  ('fn7005', 'fn7004', '汇率维护', 1, '#', '', '', '', 1, 0, 'F', '0', '0',
   'finance:rate:edit', '#', 'wczladmin', sysdate(), '新增、修改、删除汇率'),
  ('fn8060', 'fn8000', '销售利润', 8, 'profit', 'order/profit/index', '', '', 1, 0, 'C', '0', '0',
   'order:profit:list', 'chart', 'wczladmin', sysdate(), '按客户/业务员/国家/商品看毛利；接口另要 price:sale 和 price:cost');

-- 汇率：发给能看资金流水的角色
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT DISTINCT rm.`role_code`, m.`fun_code`
  FROM `sys_role_menu` rm
  CROSS JOIN (SELECT 'fn7004' AS fun_code UNION ALL SELECT 'fn7005') m
 WHERE rm.`fun_code` = 'fn7001';

-- 销售利润：只发给同时看得到销售价和成本价的角色
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT DISTINCT c.`role_code`, 'fn8060'
  FROM `sys_role_menu` c
  JOIN `sys_role_menu` s ON s.`role_code` = c.`role_code` AND s.`fun_code` = 'fn9042'
 WHERE c.`fun_code` = 'fn9041';

INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn7004' AS fun_code UNION ALL SELECT 'fn7005' UNION ALL SELECT 'fn8060') m;

-- 自查
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `perms` FROM `sys_menu`
 WHERE `fun_code` IN ('fn7004', 'fn7005', 'fn8060') ORDER BY `fun_code`;
SELECT `fun_code`, count(*) AS roles FROM `sys_role_menu`
 WHERE `fun_code` IN ('fn7004', 'fn7005', 'fn8060') GROUP BY `fun_code`;
