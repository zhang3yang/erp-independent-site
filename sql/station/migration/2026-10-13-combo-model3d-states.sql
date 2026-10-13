-- Add per-variant 3D states for products whose variants have independent models.
-- Run on the station database (sdsstation) before deploying the matching station-console jar.

DROP PROCEDURE IF EXISTS `__add_combo_model3d_states`;
DELIMITER $$
CREATE PROCEDURE `__add_combo_model3d_states`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_combo'
                   AND column_name = 'model3d_states') THEN
    ALTER TABLE `u_s_sitegoods_combo`
      ADD COLUMN `model3d_states` text DEFAULT NULL
      COMMENT 'Variant 3D state JSON; first state uses model3d as its base model'
      AFTER `model3d_marks`;
  END IF;
END$$
DELIMITER ;

CALL `__add_combo_model3d_states`();
DROP PROCEDURE `__add_combo_model3d_states`;

SELECT COUNT(*) AS `规格模型状态列_应为1`
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_combo'
   AND column_name = 'model3d_states';

-- Manual rollback after restoring the previous application version:
-- ALTER TABLE `u_s_sitegoods_combo` DROP COLUMN `model3d_states`;
