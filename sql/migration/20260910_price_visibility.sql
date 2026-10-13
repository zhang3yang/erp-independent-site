-- 金额按岗位可见：建两个权限点，并发给现有的全部角色（2026-09-10）。可重复执行。
--
-- 依据 docs/金额按岗位可见.md。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- sys_menu 加三行：一个隐藏的父目录 + 两个纯权限点。
--
--   fn9040  价格可见性   M  visible=1  perms 空      不出现在侧边栏，只在角色菜单树里
--   fn9041  采购与成本价 F             price:cost
--   fn9042  销售价       F             price:sale
--
-- 父目录 visible='1'（隐藏）：它底下一个页面都没有，出现在侧边栏就是一个点不开的空目录。
-- 隐藏之后路由里仍会生成一条 hidden 路由，前端 filterAsyncRouter 对无子节点的路由会
-- 删掉 children，不会报错。
--
-- ============================================================================
-- ⚠ 跑完之后行为必须和以前一模一样
-- ============================================================================
--
-- 所以第 3、4 节把这两个权限**发给现有的全部角色、全部商家**。不这么做的后果是
-- 脚本一落地所有人的价格列同时变空，看着就是系统坏了。
--
-- 收权是第二步，由商家管理员在「系统管理 → 角色管理」里按岗位取消勾选，
-- 想什么时候收就什么时候收，可以一个角色一个角色地来：
--
--     销售 → 只留 price:sale      采购 → 只留 price:cost
--     仓库 → 两个都取消            财务 → 两个都留
--
-- 回滚不需要回退代码，把这两个权限重新发给所有角色即可（重跑本脚本的第 3 节）。
--
-- ============================================================================
-- ⚠ 代码侧必须同时发布
-- ============================================================================
--
-- price: 这个前缀在两个地方各有一份白名单，两边都已改（见方案第 7 节）：
--   · com.ruoyi.common.constant.TenantPermissionPrefixes 的 ALL / BUSINESS_ONLY
--   · TenantShopMapper.xml 里的 10 处 like 列表
-- 只跑 SQL 不发代码，商家侧的权限集会把 price:* 过滤掉，等于没配。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260910_price_visibility.sql
--   mysql -h 124.221.220.89 -uroot -p --default-character-set=utf8mb4 < 20260910_price_visibility.sql

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
  ('fn9040', '0', '价格可见性', 900, 'pricevisible', NULL, '', '', 1, 0, 'M', '1', '0', '',
   'money', 'wczladmin', sysdate(), '金额按岗位可见的权限点容器，不出现在侧边栏');

-- ----------------------------------------------------------------------------
-- 2. 两个权限点
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn9041', 'fn9040', '采购与成本价', 1, '', '', '', '', 1, 0, 'F', '0', '0', 'price:cost',
   '#', 'wczladmin', sysdate(), '看得到采购价、库存成本、生产成本、运费'),
  ('fn9042', 'fn9040', '销售价', 2, '', '', '', '', 1, 0, 'F', '0', '0', 'price:sale',
   '#', 'wczladmin', sysdate(), '看得到销售单价、销售额、商品参考价');

-- ----------------------------------------------------------------------------
-- 3. 发给现有的全部角色
-- ----------------------------------------------------------------------------
-- 包括已删除标记的角色也一并发：它们本来就不生效，漏发反而会在恢复时出现
-- 「恢复回来的角色看不到价格」这种想不到原因的差异。
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT r.`role_code`, m.`fun_code`
  FROM `sys_role` r
  CROSS JOIN (SELECT 'fn9040' AS fun_code
              UNION ALL SELECT 'fn9041'
              UNION ALL SELECT 'fn9042') m;

-- ----------------------------------------------------------------------------
-- 4. 挂到现有的全部商家
-- ----------------------------------------------------------------------------
-- sys_shop_menu 决定「这个商家能用哪些菜单」。少了这一步，角色上勾了也不算数：
-- selectPermissionsByMember 里 join 了 sys_shop_menu。
INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT s.`shop_code`, m.`fun_code`, '0', 'wczladmin', sysdate()
  FROM (SELECT DISTINCT `shop_code` FROM `sys_shop_menu`) s
  CROSS JOIN (SELECT 'fn9040' AS fun_code
              UNION ALL SELECT 'fn9041'
              UNION ALL SELECT 'fn9042') m;

-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `visible`, `perms`
  FROM `sys_menu`
 WHERE `fun_code` IN ('fn9040', 'fn9041', 'fn9042')
 ORDER BY `fun_code`;

-- 期望：每个角色 3 行、每个商家 3 行
SELECT (SELECT count(*) FROM `sys_role`)                                        AS roles,
       (SELECT count(*) FROM `sys_role_menu`
         WHERE `fun_code` IN ('fn9040','fn9041','fn9042'))                      AS role_menu_rows,
       (SELECT count(DISTINCT `shop_code`) FROM `sys_shop_menu`)                AS shops,
       (SELECT count(*) FROM `sys_shop_menu`
         WHERE `fun_code` IN ('fn9040','fn9041','fn9042'))                      AS shop_menu_rows;
