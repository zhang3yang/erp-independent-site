-- 删除 ERP 内置的独立站管理台（2026-08-28）。可重复执行。
--
-- ============================================================================
-- 背景
-- ============================================================================
--
-- 独立站管理台原来有两套：
--   内置：ERP 前端里的 /station-console/:siteCode，页面在 views/site/{console,goods,inquiry,file}，
--         走 ERP 内部接口 /site/goods/** /site/inquiry/** /site/file/**，跟着 ERP 的登录态；
--   外部：station-console + station-console-ui，独立部署在别的域名，走一次性票 SSO，
--         数据接口是 /openapi/console/**（StationConsoleApiController）。
--
-- 两套功能完全重叠（概览/装修/商品上架/询盘/文件五个页面一一对应），现在只留外部那套，
-- 内置的前端页面和路由已经删掉。外部管理台没配好时不再静默回退，直接报错说清缺哪个配置。
--
-- ============================================================================
-- ⚠ 权限行一行都不能删
-- ============================================================================
--
-- 外部管理台的接口鉴权用的是<b>同一批权限串</b>：StationConsoleApiController 上挂的是
-- @ConsolePermi("site:goods:add") 这种，由 ConsoleApiAspect 拿 userCode 现查数据库。
-- 也就是说 sys_menu 里的 site:goods:* / site:inquiry:* / site:file:* / site:storage:*
-- 正是外部管理台用户权限的来源，删掉等于把管理台里所有人的权限清空。
--
-- 所以本脚本只做一件事：把 fn9010 / fn9020 / fn9030 从「菜单(C)」改成「按钮(F)」。
-- 理由是 SysMenuMapper 的路由查询是 `menu_type in ('M','C')`，只要它们还是 C，
-- 后端就会给前端下发一条 component='site/goods/index' 的路由，而那个文件已经删了——
-- permission.js 的 loadView 找不到组件会返回 undefined，Vue Router 拿到 undefined 直接报错。
-- 改成 F 之后不再进路由，权限串照常下发。
--
-- 顺带把三组按钮重新挂到 fn9001（站点控制台）下：它们的原父级自己变成了按钮，
-- 按钮挂按钮在「菜单管理」界面上看着是坏的，虽然不影响鉴权。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260828_drop_builtin_station_console.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260828_drop_builtin_station_console.sql

SET NAMES utf8mb4;
USE `sdssys`;

-- ============================================================
-- 1. 三个原「菜单」降级为「按钮」，清掉指向已删页面的路由字段
--    名字加「管理台·」前缀：这些权限现在只在外部管理台里起作用，
--    分配角色时得让人一眼看出它不是 ERP 里的一个页面
-- ============================================================
UPDATE sys_menu SET menu_type='F', parent_id='fn9001', path='#', component='', route_name='',
       menu_name='管理台·商品上架', visible='0', icon='#',
       remark='外部站点管理台的商品上架权限，ERP 内无对应页面'
 WHERE fun_code='fn9010';

UPDATE sys_menu SET menu_type='F', parent_id='fn9001', path='#', component='', route_name='',
       menu_name='管理台·询盘管理', visible='0', icon='#',
       remark='外部站点管理台的询盘管理权限，ERP 内无对应页面'
 WHERE fun_code='fn9020';

UPDATE sys_menu SET menu_type='F', parent_id='fn9001', path='#', component='', route_name='',
       menu_name='管理台·文件资源', visible='0', icon='#',
       remark='外部站点管理台的文件资源权限，ERP 内无对应页面'
 WHERE fun_code='fn9030';

-- ============================================================
-- 2. 原来挂在这三个下面的按钮，改挂到 fn9001，避免「按钮挂按钮」
-- ============================================================
UPDATE sys_menu SET parent_id='fn9001'
 WHERE fun_code IN ('fn9011','fn9012','fn9013','fn9014',
                    'fn9021','fn9022','fn9023',
                    'fn9031','fn9032','fn9033','fn9034','fn9035','fn9036');

-- ============================================================
-- 3. 校验
-- ============================================================

-- 3.1 期望 0 行：独立站下不该再有 component 指向已删页面的菜单
SELECT fun_code, menu_name, menu_type, component FROM sys_menu
 WHERE component IN ('site/goods/index','site/inquiry/index','site/file/index',
                     'site/console/index','site/console/overview','site/console/decorate');

-- 3.2 独立站菜单树：期望 fn9000(M) → fn9001(C) → 其余全是 F
SELECT fun_code, menu_name, parent_id, menu_type, visible, perms
  FROM sys_menu WHERE fun_code BETWEEN 'fn9000' AND 'fn9036' ORDER BY fun_code;

-- 3.3 权限串必须一个不少（外部管理台的 @ConsolePermi 依赖它们）
SELECT COUNT(*) AS perms_kept FROM sys_menu
 WHERE perms LIKE 'site:goods:%' OR perms LIKE 'site:inquiry:%'
    OR perms LIKE 'site:file:%'  OR perms LIKE 'site:storage:%';

-- 3.4 授权行不该受影响
SELECT COUNT(*) AS shop_grants FROM sys_shop_menu WHERE fun_code BETWEEN 'fn9000' AND 'fn9036';
SELECT COUNT(*) AS role_grants FROM sys_role_menu WHERE fun_code BETWEEN 'fn9000' AND 'fn9036';
