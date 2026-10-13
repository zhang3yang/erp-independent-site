-- 成本调整单 CBTZ：数量不动、只改库存成本（2026-09-28）。可重复执行。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 1. u_m_doctypeconfig 加一种库存单据：
--
--      CBTZ  成本调整  C   方向 C 是新加的，意思是「只动金额不动数量」
--
--    用在：采购运费后到要补进库存成本、入库价录错要更正、生产完工后费用有变。
--    按商品计价方式落到出库时取成本的那一层：
--      移动平均               → 改库存汇总的总成本（均价跟着变）
--      批次 + 先进先出/个别计价 → 改指定批次的单价
--      序列号 + 个别计价       → 把差额摊到指定的几个序列号上
--    在库数量为 0 的不能调：已经卖出去的差额属于销售成本，要回溯出库单，不归这张单管。
--
-- 2. sdssys 菜单：库存管理 → 库存变动 下加「成本调整」。
--
--      fn5112  成本调整     C  inventory:costadjust:list
--      fn5113  调整录入     F  inventory:costadjust:add      新建、删除（接口另要 price:cost）
--      fn5114  调整审核     F  inventory:costadjust:approve  审核即改成本（接口另要 price:cost）
--
--    只发给已经有 price:cost（fn9041，看得到成本价）的角色：
--    看不到成本的人没法判断该调多少，也不该能改它。
--
-- ============================================================================
-- ⚠ 必须和代码一起发布，先跑 SQL
-- ============================================================================
--
-- 只发代码不跑 SQL：成本调整单建单报「单据类型未配置」，其余功能不受影响。
-- 只跑 SQL 不发代码：菜单点进去 404。
-- 两者都不影响已有单据，但顺序上仍按惯例先 SQL 后代码。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < 20260928_cost_adjust.sql

SET NAMES utf8mb4;

USE `sdsshop`;

INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`)
VALUES ('CBTZ', '成本调整', 'C', 1, 0, '方向 C：只改库存成本不动数量；只能在「成本调整」页面建和审');

USE `sdssys`;

INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn5112', 'fn5092', '成本调整', 9, 'costAdjust', 'inventory/costAdjust/index', '', '', 1, 0, 'C', '0', '0',
   'inventory:costadjust:list', 'money', 'wczladmin', sysdate(), '数量不动只改库存成本：运费后到、入库价录错'),
  ('fn5113', 'fn5112', '调整录入', 1, '#', '', '', '', 1, 0, 'F', '0', '0',
   'inventory:costadjust:add', '#', 'wczladmin', sysdate(), '新建、删除待审核的调整单；接口另要 price:cost'),
  ('fn5114', 'fn5112', '调整审核', 2, '#', '', '', '', 1, 0, 'F', '0', '0',
   'inventory:costadjust:approve', '#', 'wczladmin', sysdate(), '审核即改库存成本；接口另要 price:cost');

-- 只发给看得到成本价的角色
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT DISTINCT rm.`role_code`, m.`fun_code`
  FROM `sys_role_menu` rm
  CROSS JOIN (SELECT 'fn5112' AS fun_code
              UNION ALL SELECT 'fn5113'
              UNION ALL SELECT 'fn5114') m
 WHERE rm.`fun_code` = 'fn9041';

INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn5112' AS fun_code
              UNION ALL SELECT 'fn5113'
              UNION ALL SELECT 'fn5114') m;

-- 自查
SELECT `doctype`, `doctypename`, `direction` FROM `sdsshop`.`u_m_doctypeconfig` WHERE `doctype` = 'CBTZ';
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `perms` FROM `sys_menu`
 WHERE `fun_code` IN ('fn5112', 'fn5113', 'fn5114') ORDER BY `fun_code`;
SELECT `fun_code`, count(*) AS roles FROM `sys_role_menu`
 WHERE `fun_code` IN ('fn5112', 'fn5113', 'fn5114') GROUP BY `fun_code`;
