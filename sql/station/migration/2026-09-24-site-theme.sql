-- =====================================================================
-- 前台视觉主题：给 u_s_siteinfo 加 theme
-- =====================================================================
--
-- 起因：2026-09-21 法国站（esvafit）改版，把前台 site.css 和二十来个组件里的
-- 紫色直接换成了 Esva 的黑 + 电光蓝（提交 d6f9f9fa）。那是全局改的——
-- 同一份代码发到康辰，康辰也会变成黑蓝、深色顶栏。
--
-- 现在主题按站点选：
--   · NULL / 'esva' → site.css 本身那套（黑 + 电光蓝）。法国站就是它。
--   · 'classic'     → 改版前的紫色 + Fraunces 衬线标题，由
--                     station-site/app/assets/theme-classic.css 盖回去。康辰用它。
--
-- 回填规则：**每个站保持它现在线上的样子。**
--   法国站（DLZ2049739702）线上已经是黑蓝 → 留 NULL；
--   其他站（康辰等）线上还是紫色 → 'classic'。
--   只在「这一列是这次新加的」时回填，重复跑不会覆盖之后手工改过的值。
--
-- ⚠ 必须**先于**新版 station-console 跑：新版的 SiteInfoMapper 会 select theme，
--   列不存在的话站点档案整个读不出来，前台和管理台一起挂。
--
-- 可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS `__add_site_theme`;
DELIMITER $$
CREATE PROCEDURE `__add_site_theme`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_siteinfo'
                   AND column_name = 'theme') THEN
    ALTER TABLE `u_s_siteinfo`
      ADD COLUMN `theme` varchar(20) DEFAULT NULL
      COMMENT '前台视觉主题：classic=紫色+Fraunces（康辰），NULL/esva=黑+电光蓝（法国站，site.css 本身那套）。只能改库，管理台不改它'
      AFTER `consolelang`;

    UPDATE `u_s_siteinfo`
       SET `theme` = 'classic'
     WHERE `sitecode` <> 'DLZ2049739702';
  END IF;
END$$
DELIMITER ;

CALL `__add_site_theme`();
DROP PROCEDURE `__add_site_theme`;

-- 核对：康辰那台应为 classic，法国站那台应为 NULL
SELECT `sitecode`, `sitename`, `theme` FROM `u_s_siteinfo`;

-- 回滚：
-- ALTER TABLE `u_s_siteinfo` DROP COLUMN `theme`;
