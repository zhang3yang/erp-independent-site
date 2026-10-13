-- =====================================================================
-- 买家头像：u_s_buyer 加一列 avatar
-- =====================================================================
--
-- 库：sdsstation。可重复执行（先判列在不在）。
--
-- 账户页照参考站（kangwanjia-website-EN 的 pages/profile）改成了左栏 + 面板的版式，
-- 其中「Profile」那一块要头像：要么传一张图，要么从六个色块里挑一个。
--
-- =====================================================================
-- ⚠ 和参考站不一样的地方：那边存 localStorage，这边存库
-- =====================================================================
--
-- 参考站是个纯前端 stub，头像存在 localStorage 里，页面上那句提示写着
-- 「nothing is uploaded anywhere」。照搬会和这个站的登录自相矛盾——买家账号的
-- 全部意义就是「换台电脑还在」，而 localStorage 里的头像换台电脑就没了。
--
-- 所以存库。前台那句提示也跟着改成实话（「在浏览器里缩到 256px 之后保存到你的账号」）。
--
-- =====================================================================
-- 为什么是 mediumtext 直接存 data URL，而不是传对象存储存个 URL
-- =====================================================================
--
--   · 图在浏览器里已经被裁成正方形、缩到 256px、按 JPEG 0.82 编码，
--     落地十几到几十 KB。为这个体量接一遍上传接口 + COS 不划算；
--   · 走 SiteFileService.uploadImage 会往 u_s_image 里落行，那张表是**运营的图片库**，
--     商家进去要看到的是自己的商品图，不是几百个买家的头像；
--   · COS 是可配的（u_s_storage 里 provider 可能是 local），买家头像不该依赖那一层配好。
--
-- 这一列也可能只存一个 `preset:teal` 这样的短串（挑了色块而不是传图）。
--
-- =====================================================================
-- ⚠ 这一列不进任何列清单
-- =====================================================================
--
-- SiteBuyerMapper.xml 里的 `columns` / `authColumns` **都不含它**，取它的只有
-- selectAvatar 一条。理由是这两份清单用在管理台的客户列表（一页 20 行）和
-- requireBuyer（每个要身份的请求都跑一次）上——把几十 KB 的 base64 挂上去，
-- 那两条路径就白白重了几百 KB。
-- =====================================================================

USE `sdsstation`;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_buyer'
     AND COLUMN_NAME  = 'avatar'
);

SET @sql := IF(@has_col = 0,
  'ALTER TABLE `u_s_buyer` ADD COLUMN `avatar` mediumtext DEFAULT NULL
     COMMENT ''头像。两种取值：preset:<色块id>，或浏览器裁好缩到 256px 的 JPEG data URL。⚠ 不进 mapper 的列清单，只有 selectAvatar 取它''
     AFTER `country`',
  'SELECT ''u_s_buyer.avatar 已存在，跳过'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


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
     VALUES (''2026-09-09-buyer-avatar.sql'', ''claude'', ''执行'',
             ''u_s_buyer 加 avatar 列（买家头像，data URL 或 preset:<id>）'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');

PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 跑完要重启 station-console（读写这一列的是新代码），前台也要重新部署。
-- 顺序和上一条迁移一样：先跑这个，再换 jar，最后发前台。
--
-- ⚠ 顺序反了同样不报警：MyBatis 启动不校验列存不存在，
--   炸点是第一个买家点开账户页。换 jar 前确认一句：
--
--       select count(*) from information_schema.columns
--        where table_schema = 'sdsstation' and table_name = 'u_s_buyer'
--          and column_name = 'avatar';
--
--   不是 1 就停。
-- ---------------------------------------------------------------------
