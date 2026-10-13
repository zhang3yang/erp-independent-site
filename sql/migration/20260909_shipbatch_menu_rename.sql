-- 仓库发货菜单改名，对齐当前前端「打单 → 发货 → 完成」口径（2026-09-09）。可重复执行。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- sys_menu 里仓库发货管理（fn5103）下的两个按钮，显示名统一成前端在用的叫法：
--
--   fn5105  仓库发货确认备货  →  仓库发货打单     (perms order:shipbatch:confirm)
--   fn5106  仓库发货发货      →  仓库发货与完成   (perms order:shipbatch:ship)
--
-- 起因：对比本地(127.0.0.1)和远程(82.156.158.174)时发现这两条名字不一致——
-- 远程早就改成了新叫法，本地还停在旧名。前端 RuoYi-Vue3 里
-- order:shipbatch:confirm 那个按钮现在就叫「打单」
-- （views/order/shipbatch/index.vue，整条流程是「打单 → 发货 → 完成」），
-- 所以新叫法才是准的。本脚本把落后的库拉齐。
--
-- 只改 menu_name，不动 perms / 路由 / 授权。值已经对的库是 no-op。
--
-- ============================================================================
-- 执行（哪个库的名字还是旧的就跑哪个；跑在已对齐的库上无副作用）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260909_shipbatch_menu_rename.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260909_shipbatch_menu_rename.sql

SET NAMES utf8mb4;
USE `sdssys`;

UPDATE `sys_menu` SET `menu_name` = '仓库发货打单'  WHERE `fun_code` = 'fn5105';
UPDATE `sys_menu` SET `menu_name` = '仓库发货与完成' WHERE `fun_code` = 'fn5106';

-- 自查
SELECT `fun_code`, `menu_name`, `perms`
  FROM `sys_menu`
 WHERE `fun_code` IN ('fn5105', 'fn5106')
 ORDER BY `fun_code`;
