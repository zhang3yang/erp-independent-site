-- 首页经营看板：建一个权限点，并发给现有的全部角色和商家（2026-09-11）。可重复执行。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- sys_menu 加两行：一个隐藏的父目录 + 一个纯权限点。
--
--   fn9050  经营看板   M  visible=1  perms 空                  不出现在侧边栏
--   fn9051  查看看板   F             dashboard:overview:query
--
-- 写法照搬 20260910_price_visibility.sql：看板不是一个页面，它长在首页里
-- （RuoYi-Vue3/src/views/index.vue），没有自己的路由，所以父目录 visible='1' 隐藏，
-- 只在角色菜单树里作为一个可勾选的权限点存在。
--
-- ============================================================================
-- ⚠ 代码侧必须同时发布
-- ============================================================================
--
-- dashboard: 是一个新前缀，商家侧的权限集要按前缀白名单过滤，两处都已改：
--   · com.ruoyi.common.constant.TenantPermissionPrefixes 的 ALL / BUSINESS_ONLY
--   · TenantShopMapper.xml 里的 10 处 like 列表
--
-- 只跑 SQL 不发代码的后果，和当年加订单模块漏改一处时一样：
-- 角色菜单树里勾得上，接口一律 403，而且报错里看不出是被前缀过滤掉的。
--
-- ============================================================================
-- 为什么敢发给所有角色
-- ============================================================================
--
-- 这个权限点只管「首页那块看板出不出来」，卡片是在库货值、在库商品、待审核单据、
-- 未处理预警、本月入库、本月出库，都是仓库和单据口径的数，本来各模块页面上就看得到。
--
-- 钱的两个数（本月收入 / 本月支出）和收支趋势图**不归这个权限管**，
-- 它们另外要求 finance:transaction:list（见 DashboardController.FINANCE_PERMISSION）。
-- 没有财务权限的人，后端根本不去查财务月表，响应体里也没有这两个数。
-- 所以这一脚本发给所有角色，不会把谁的钱袋子露出去。
--
-- 要收回给某个角色（比如临时工账号不想让看货值）：
--
--     DELETE FROM `sys_role_menu`
--      WHERE `role_code` = '这里填角色编码' AND `fun_code` IN ('fn9050', 'fn9051');
--
-- 收回之后那个角色的首页就只剩欢迎语和快捷入口，和上线前一模一样，不会报错
-- （前端对 403 是静默吞掉的，见 index.vue 的 loadDashboard）。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260911_dashboard_menu.sql
--   mysql -h 124.221.220.89 -uroot -p --default-character-set=utf8mb4 < 20260911_dashboard_menu.sql

SET NAMES utf8mb4;
USE `sdssys`;

-- ----------------------------------------------------------------------------
-- 1. 隐藏的父目录
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn9050', '0', '经营看板', 901, 'dashboard', NULL, '', '', 1, 0, 'M', '1', '0', '',
   'chart', 'wczladmin', sysdate(), '首页经营看板的权限点容器，不出现在侧边栏');

-- ----------------------------------------------------------------------------
-- 2. 权限点
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn9051', 'fn9050', '查看看板', 1, '', '', '', '', 1, 0, 'F', '0', '0',
   'dashboard:overview:query',
   '#', 'wczladmin', sysdate(), '首页看板的卡片和趋势图；收支两项另需 finance:transaction:list');

-- ----------------------------------------------------------------------------
-- 3. 发给现有的全部角色
-- ----------------------------------------------------------------------------
-- 连带已删除标记的角色一起发，理由同 20260910_price_visibility.sql：
-- 漏发会在角色恢复时变成一个找不到原因的差异。
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT r.`role_code`, m.`fun_code`
  FROM `sys_role` r
  CROSS JOIN (SELECT 'fn9050' AS fun_code
              UNION ALL SELECT 'fn9051') m;

-- ----------------------------------------------------------------------------
-- 4. 挂到现有的全部商家
-- ----------------------------------------------------------------------------
-- sys_shop_menu 决定「这个商家能用哪些菜单」。少了这一步，角色上勾了也不算数：
-- selectPermissionsByMember 里 join 了 sys_shop_menu。
INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn9050' AS fun_code
              UNION ALL SELECT 'fn9051') m;

-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `visible`, `perms`
  FROM `sys_menu`
 WHERE `fun_code` IN ('fn9050', 'fn9051')
 ORDER BY `fun_code`;

-- 期望：每个角色 2 行、每个商家 2 行
SELECT (SELECT count(*) FROM `sys_role`)                         AS roles,
       (SELECT count(*) FROM `sys_role_menu`
         WHERE `fun_code` IN ('fn9050','fn9051'))                AS role_menu_rows,
       (SELECT count(DISTINCT `shop_code`) FROM `sys_shop_menu`) AS shops,
       (SELECT count(*) FROM `sys_shop_menu`
         WHERE `fun_code` IN ('fn9050','fn9051'))                AS shop_menu_rows;
