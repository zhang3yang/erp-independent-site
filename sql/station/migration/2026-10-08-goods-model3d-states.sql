-- =====================================================================
-- 商品 3D 模型状态：一个商品挂多个 glb，前台按钮切换（如腿套「没气 / 半充 / 充满」）
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
-- ⚠ 必须先于新 jar：新版 SiteGoodsMapper.xml / CustomPlanMapper.xml 的列清单里有 model3d_states，
--    列没有时查商品、存方案都会 500。
--
-- 设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md
--
-- JSON 里埋着各状态 glb 的完整地址，换存储域名时要跟着替换——
-- 已加进 2026-09-06-file-url-rebase.sql 的清单和 UPDATE。
--
-- 可重复执行：加列走 information_schema 判断。


DROP PROCEDURE IF EXISTS `__add_model3d_states`;
DELIMITER $$
CREATE PROCEDURE `__add_model3d_states`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 商品：挨着 model3d_marks 放
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'model3d_states') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `model3d_states` text DEFAULT NULL
      COMMENT '3D 模型状态 JSON（第一个=主模型 model3d，其余各一个 glb）；空 = 没有状态'
      AFTER `model3d_marks`;
  END IF;

  -- 买家定制方案：状态快照，挨着 marks 放
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_custom_plan'
                   AND column_name = 'model3d_states') THEN
    ALTER TABLE `u_s_custom_plan`
      ADD COLUMN `model3d_states` text DEFAULT NULL
      COMMENT '保存方案时商品模型状态的快照（只含有模型的状态，名字全语言）；空 = 没有状态'
      AFTER `marks`;
  END IF;
END$$
DELIMITER ;

CALL `__add_model3d_states`();
DROP PROCEDURE `__add_model3d_states`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods'
      AND column_name = 'model3d_states')                                     AS `商品状态列_应为1`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_custom_plan'
      AND column_name = 'model3d_states')                                     AS `方案状态列_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods`   DROP COLUMN `model3d_states`;
-- ALTER TABLE `u_s_custom_plan` DROP COLUMN `model3d_states`;
--
-- ⚠ 回滚前先确认 jar 已经换回旧版，理由同文件头。
