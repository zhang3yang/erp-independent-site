-- =====================================================================
-- 商品 3D 标注：模型上的 logo 贴花 / 屏幕 / 开机键
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
--
-- 模型是 AI 图生 3D 出来的「一整块网格 + 一张贴图」，屏幕和按键不是独立零件，
-- 所以位置由商家在管理台上用鼠标点出来，存成商品上的一段 JSON，前台加载模型后现场叠加。
--
-- 设计：docs/superpowers/specs/2026-09-25-product-3d-marks-design.md
--
-- JSON 里埋着 logo / 屏幕图的完整地址，换存储域名时要跟着替换——
-- 已加进 2026-09-06-file-url-rebase.sql 的清单和 UPDATE。
--
-- 可重复执行：加列走 information_schema 判断。


DROP PROCEDURE IF EXISTS `__add_goods_model3d_marks`;
DELIMITER $$
CREATE PROCEDURE `__add_goods_model3d_marks`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 挨着 model3d 放：标注离开模型没有意义
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'model3d_marks') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `model3d_marks` text DEFAULT NULL
      COMMENT '3D 标注 JSON（logo 贴花/屏幕/开机键），坐标是 glb 场景自身坐标；空 = 没有标注'
      AFTER `model3d`;
  END IF;
END$$
DELIMITER ;

CALL `__add_goods_model3d_marks`();
DROP PROCEDURE `__add_goods_model3d_marks`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods'
      AND column_name = 'model3d_marks')                                      AS `标注列_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods` DROP COLUMN `model3d_marks`;
--
-- ⚠ 回滚前先确认 jar 已经换回旧版：新版 SiteGoodsMapper.xml 的列清单里有 model3d_marks，
--    列没了它每次查商品都 500。
