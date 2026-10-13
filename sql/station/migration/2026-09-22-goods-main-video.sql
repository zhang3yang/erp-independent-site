-- =====================================================================
-- 商品主图视频：图库轮播的第一格放一个视频
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
--
-- 详情页现在有两个放视频的位置，这份脚本加的是第一个：
--
--   ① u_s_sitegoods.mainvideo   主图位置的视频，图库轮播的第一格（本脚本新加）
--   ② u_s_sitegoods.sitevideos  详情底部那一排「视频」（早就有了，不动）
--
-- 两边互不影响：一个商品可以两边都配、只配一边、或者都不配。
--
-- ---------------------------------------------------------------------
-- 三个取舍
-- ---------------------------------------------------------------------
--
-- 1. 单值不是列表。主图位置只放一个视频——第一格之后跟的是图片。真要放第二个
--    视频，它的位置是 sitevideos。所以这一列是 varchar(500) 存一个 URL，
--    和 model3d 同构，不是 sitevideos 那种逗号分隔串。
--
-- 2. 封面不入库。前台拿跟在视频后面的**第一张图**当 poster（siteimgs 的第一张，
--    没有 siteimgs 时是 imgurl/mainimgs 回退出来的第一张）。存一列 cover 的话，
--    商家换了主图，封面还指着旧那张——一个必然会飘、又没人会去维护的字段。
--
-- 3. 存 URL 不存 videoid。和 siteimgs / sitevideos / model3d 一致：前台只认 URL，
--    文件库删了记录商品也不跟着裂（文件本身留在桶里）。代价是换存储域名时这一列
--    要按 2026-09-06-file-url-rebase.sql 那套做字符串替换——已加进那份脚本的清单。
--
-- 可重复执行：加列走 information_schema 判断。


-- =====================================================================
-- ① 商品上的主图视频
-- =====================================================================

DROP PROCEDURE IF EXISTS `__add_goods_mainvideo`;
DELIMITER $$
CREATE PROCEDURE `__add_goods_mainvideo`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 挨着 model3d 放：四列都是「来自文件资源库的站点媒体」
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'mainvideo') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `mainvideo` varchar(500) DEFAULT NULL
      COMMENT '主图位置的视频，完整地址，来自文件资源库；空 = 图库第一格仍是图。封面取其后第一张图'
      AFTER `model3d`;
  END IF;
END$$
DELIMITER ;

CALL `__add_goods_mainvideo`();
DROP PROCEDURE `__add_goods_mainvideo`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods'
      AND column_name = 'mainvideo')                                        AS `主图视频列_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods` DROP COLUMN `mainvideo`;
--
-- ⚠ 回滚前先确认 jar 已经换回旧版：新版 SiteGoodsMapper.xml 的列清单里有 mainvideo，
--    列没了它每次查商品都 500。
