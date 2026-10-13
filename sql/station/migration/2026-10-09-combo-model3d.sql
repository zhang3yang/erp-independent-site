-- =====================================================================
-- 商品规格（组合）各自的 3D 模型 + 定制方案记规格
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
-- ⚠ 必须先于新 jar：新版 SiteGoodsComboMapper.xml / CustomPlanMapper.xml 的列清单里有新列，
--    列没有时查商品详情、存方案都会 500。
--
-- 设计：docs/superpowers/specs/2026-10-09-combo-model3d-design.md
--
-- 组合的 model3d / model3d_marks 里埋着模型、图片地址，换存储域名时要跟着替换——
-- 已加进 2026-09-06-file-url-rebase.sql 的清单和 UPDATE。
--
-- 可重复执行：加列走 information_schema 判断。


DROP PROCEDURE IF EXISTS `__add_combo_model3d`;
DELIMITER $$
CREATE PROCEDURE `__add_combo_model3d`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_combo'
                   AND column_name = 'model3d') THEN
    ALTER TABLE `u_s_sitegoods_combo`
      ADD COLUMN `model3d` varchar(500) DEFAULT NULL
      COMMENT '这个规格自己的 3D 模型 .glb 地址；空 = 用商品主模型'
      AFTER `image`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_combo'
                   AND column_name = 'model3d_marks') THEN
    ALTER TABLE `u_s_sitegoods_combo`
      ADD COLUMN `model3d_marks` text DEFAULT NULL
      COMMENT '这个规格自己的 3D 标注；空 = logo 跟随主模型（前台自动投影）'
      AFTER `model3d`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_custom_plan'
                   AND column_name = 'combocode') THEN
    ALTER TABLE `u_s_custom_plan`
      ADD COLUMN `combocode` varchar(50) DEFAULT NULL
      COMMENT '定制的是哪个规格；空 = 主模型'
      AFTER `goodscode`;
  END IF;
END$$
DELIMITER ;

CALL `__add_combo_model3d`();
DROP PROCEDURE `__add_combo_model3d`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_combo'
      AND column_name IN ('model3d', 'model3d_marks'))                        AS `组合两列_应为2`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_custom_plan'
      AND column_name = 'combocode')                                          AS `方案规格列_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods_combo` DROP COLUMN `model3d_marks`, DROP COLUMN `model3d`;
-- ALTER TABLE `u_s_custom_plan` DROP COLUMN `combocode`;
--
-- ⚠ 回滚前先确认 jar 已经换回旧版，理由同文件头。
