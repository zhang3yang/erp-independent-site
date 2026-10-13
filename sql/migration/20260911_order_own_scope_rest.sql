-- 横向权限补齐：发货批次 / 销售退货 / 销售补发（2026-09-11）。可重复执行。
--
-- 接着 20260911_sales_order_own_scope.sql（销售订单）做，把同一套「只看自己的」
-- 铺到订单管理下剩下的三个页面。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
--   fn8043  查看全部发货批次  F  order:shipbatch:all   挂在 fn8010 发货批次 下
--   fn8044  查看全部退货单     F   order:salesreturn:all  挂在 fn8020 销售退货 下
--   fn8045  查看全部补发单     F   order:salesreship:all  挂在 fn8030 销售补发 下
--
-- 语义与销售订单那条完全一致：勾上＝看全店，不勾＝只看自己建的单。
-- 归属认各自表上的 createuser（建单时写的登录账号）。
-- 平台超管在代码里直接豁免。
--
-- ============================================================================
-- ⚠ 仓库侧不看这个权限点，见 20260911_shipbatch_warehouse_permi.sql
-- ============================================================================
--
-- 本脚本刚落地时，order:shipbatch:all 同时管着「仓库发货管理」和「订单管理→发货批次」
-- 两个页面（它们共用同一个 /order/shipbatch/list）。那样配一个库管员角色，
-- 要跨到订单管理树下勾这一项，侧边栏还会被父子联动带出一个用不上的订单管理菜单。
--
-- 后一个脚本把仓库发货页拆成了独立权限串 order:shipbatch:warehouse，
-- 持有它即代表看得到全部批次。**所以 order:shipbatch:all 现在只管订单管理那个页面。**
--
-- 销售补发同理：仓库要在补发页点「发货」，仓库角色也得有 order:salesreship:all。
--
-- 销售退货：「提交退货」按钮就在销售退货页的记录行上（库存的入库信息页只查 RK，
-- 退货单 XSTHRK 不在那儿）。提交动作本身不认归属，但**要先看得见才点得到**——
-- 所以如果退货是由业务员之外的人提交的，那个角色也得勾上 order:salesreturn:all。
--
-- ============================================================================
-- 跑完之后行为必须和以前一模一样
-- ============================================================================
--
-- 第 2、3 节把三个权限发给现有的全部角色、全部商家。收权是第二步，由商家管理员
-- 到「系统管理 → 角色管理」里给业务员那类角色取消勾选。
--
-- 回滚：重新发给所有角色即可，不需要回退代码。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260911_order_own_scope_rest.sql
--   mysql -h 124.221.220.89 -uroot -p --default-character-set=utf8mb4 < 20260911_order_own_scope_rest.sql

SET NAMES utf8mb4;
USE `sdssys`;

-- ----------------------------------------------------------------------------
-- 1. 三个权限点
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn8043', 'fn8010', '查看全部发货批次', 10, '', '', '', '', 1, 0, 'F', '0', '0',
   'order:shipbatch:all', '#', 'wczladmin', sysdate(),
   '只管「订单管理 → 发货批次」这个页面；不勾＝只看得到自己建的批次。仓库侧不需要它'),
  ('fn8044', 'fn8020', '查看全部退货单', 10, '', '', '', '', 1, 0, 'F', '0', '0',
   'order:salesreturn:all', '#', 'wczladmin', sysdate(),
   '不勾＝只看得到自己建的退货单。提交（审核即过账）不受它限制，入库员不勾也能审'),
  ('fn8045', 'fn8030', '查看全部补发单', 10, '', '', '', '', 1, 0, 'F', '0', '0',
   'order:salesreship:all', '#', 'wczladmin', sysdate(),
   '不勾＝只看得到自己建的补发单。⚠ 仓库要在补发页点发货，仓库角色也得勾上');

-- ----------------------------------------------------------------------------
-- 2. 发给现有的全部角色（跑完行为不变）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT r.`role_code`, m.`fun_code`
  FROM `sys_role` r
  CROSS JOIN (SELECT 'fn8043' AS fun_code
              UNION ALL SELECT 'fn8044'
              UNION ALL SELECT 'fn8045') m;

-- ----------------------------------------------------------------------------
-- 3. 挂到现有的全部商家
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn8043' AS fun_code
              UNION ALL SELECT 'fn8044'
              UNION ALL SELECT 'fn8045') m;

-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `perms`
  FROM `sys_menu` WHERE `fun_code` IN ('fn8043', 'fn8044', 'fn8045')
 ORDER BY `fun_code`;

-- 期望：role_menu_rows = 角色数 × 3，shop_menu_rows = 商家数 × 3
SELECT (SELECT count(*) FROM `sys_role`)                                 AS roles,
       (SELECT count(*) FROM `sys_role_menu`
         WHERE `fun_code` IN ('fn8043','fn8044','fn8045'))               AS role_menu_rows,
       (SELECT count(DISTINCT `shop_code`) FROM `sys_shop_menu`)         AS shops,
       (SELECT count(*) FROM `sys_shop_menu`
         WHERE `fun_code` IN ('fn8043','fn8044','fn8045'))               AS shop_menu_rows;
