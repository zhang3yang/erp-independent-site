-- =====================================================================
-- 商品规格维度：组合从「一排型号」变成「尺码 × 款式」两个维度
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
-- ⚠ 先跑这份、再换 jar：新版 SiteGoodsMapper / SiteGoodsComboMapper 的列清单里
--    有 optionsjson / opt1 / opt2，列不在的话每次查商品、查组合都 500。
--    三个站是同一套代码（康辰、统领、esvafit），哪台换 jar 哪台先跑这份。
--
-- 原来详情页上的「组合」是一排平铺的型号：尺码 S/M、尺码 L……
-- 商家要的是 1688 那样的两排：一排选尺码、一排选款式，两者搭起来才是一个货。
--
-- ---------------------------------------------------------------------
-- 模型：维度 + SKU，SKU 还是原来那张组合表
-- ---------------------------------------------------------------------
--
--   u_s_sitegoods.optionsjson      这个商品有哪几个维度、每个维度有哪些取值（本脚本新加）
--   u_s_sitegoods_combo.opt1/opt2  这一条组合（SKU）是第一维的哪个值、第二维的哪个值（本脚本新加）
--
-- **组合表的身份和用法一点没变**：一条组合仍然是一个能下单的货，有自己的
-- combocode、价格、图、状态；订单、询盘、购物车、邮件照旧只认 combocode + 名称快照。
-- 二维模式下组合名由后端按「S/M · 黑色」自动拼好写进 u_s_sitegoods_combo_lang，
-- 所以下游一行都不用改。
--
-- 为什么不是「尺码表 + 款式表」各一张、价格挂在交叉表上：价格和状态本来就是
-- 按「具体哪个货」定的（XL 黑色可能不做），那正是组合表的一行。再建交叉表等于
-- 把组合表重新发明一遍，还得把单据快照那一套也复制过去。
--
-- ---------------------------------------------------------------------
-- 为什么维度定义是一列 JSON，不是两张表
-- ---------------------------------------------------------------------
--
-- 2026-09-08 做组合时拒绝过 JSON，理由是「多语言塞进 JSON 后靠**下标**对齐，
-- 删一条就全错位」。这里不存在那个问题：每个维度、每个取值都有自己的**编码**，
-- 各语言名称挂在编码下面（{"code":"BLK","names":{"zh":"黑色","en":"Black"}}），
-- 删一个取值就是删那个编码，不会让别的错位。
--
-- 而拆表的代价很实在：维度 + 维度名多语言 + 取值 + 取值名多语言 = 四张表，
-- 存一次商品要整份替换四张表。一个商品最多两个维度、每维十来个值，
-- 一列 JSON 装得下，也整份读、整份写——和它的使用方式正好对上。
--
-- 形状（后端 SiteGoodsService.normalizeOptions 会校验并整理成这样）：
--
--   {"dims":[
--     {"code":"size","names":{"zh":"尺码","en":"Size"},
--      "values":[{"code":"SM","names":{"zh":"S/M","en":"S/M"}}, ...]},
--     {"code":"style","names":{"zh":"款式","en":"Style"},
--      "values":[{"code":"BLK","names":{"zh":"黑色","en":"Black"},"image":"https://..."}, ...]}
--   ]}
--
-- 最多两个维度。只有一个维度也行（等于「有名字的一排」）。
-- optionsjson 为空 = 老的平铺模式，opt1/opt2 不看，详情页照旧一排。
--
-- opt1/opt2 **不是外键**，存的是取值编码。商家删掉一个取值时，后端会连带删掉
-- 用到它的组合（整份替换组合时它们不在提交里）；历史单据里的快照不受影响。
--
-- 可重复执行：加列走 information_schema 判断。


DROP PROCEDURE IF EXISTS `__add_goods_options`;
DELIMITER $$
CREATE PROCEDURE `__add_goods_options`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- text 不是 varchar(2000)：两个维度 × 十几个取值 × 七种语言的名称，
  -- 再带上款式图的 URL，两千字符不够用
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'optionsjson') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `optionsjson` text DEFAULT NULL
      COMMENT '组合的规格维度 JSON（最多两维，如尺码/款式），取值按编码挂多语言名称。空 = 组合是平铺的一排'
      AFTER `specsjson`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_combo'
                   AND column_name = 'opt1') THEN
    ALTER TABLE `u_s_sitegoods_combo`
      ADD COLUMN `opt1` varchar(50) DEFAULT NULL
      COMMENT '第一维的取值编码（对应 u_s_sitegoods.optionsjson 的 dims[0].values[].code）。平铺模式下为空'
      AFTER `combocode`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_combo'
                   AND column_name = 'opt2') THEN
    ALTER TABLE `u_s_sitegoods_combo`
      ADD COLUMN `opt2` varchar(50) DEFAULT NULL
      COMMENT '第二维的取值编码。只有一个维度时为空'
      AFTER `opt1`;
  END IF;
END$$
DELIMITER ;

CALL `__add_goods_options`();
DROP PROCEDURE `__add_goods_options`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods'
      AND column_name = 'optionsjson')                                      AS `规格维度列_应为1`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_combo'
      AND column_name IN ('opt1','opt2'))                                   AS `组合取值列_应为2`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods` DROP COLUMN `optionsjson`;
-- ALTER TABLE `u_s_sitegoods_combo` DROP COLUMN `opt1`, DROP COLUMN `opt2`;
--
-- ⚠ 回滚前先把 jar 换回旧版，理由同文件头。
