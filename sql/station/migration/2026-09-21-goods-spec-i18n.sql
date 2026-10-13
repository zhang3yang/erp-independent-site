-- =====================================================================
-- 商品规格参数多语言：u_s_sitegoods_lang 加一列 specsjson
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**(库 sdsstation)。
--
-- 规格原来只在主表 u_s_sitegoods.specsjson 存一份(默认语言),所有语言共用、显示英文原样。
-- 这一列给每个「商品+语言」存一份译文,按【默认语言的键】当身份对齐:
--   {"Material":{"k":"Material","v":"silicone curado com platina"},
--    "Structure":{"k":"Estrutura","v":"4 cavidades, fila única"}}
-- 主表那份仍是「有几行、什么顺序」的源头;详情接口按语言逐键回退。
--
-- 设计:docs/superpowers/specs/2026-09-21-product-spec-i18n-design.md
--
-- 列宽 varchar(4000):主表 specsjson 是 varchar(2000),译文带 {k,v} 包装约 2.5 倍,放宽到 4000。
-- 可重复执行:走 information_schema 判断(MySQL 8 的 ADD COLUMN 没有 IF NOT EXISTS)。

DROP PROCEDURE IF EXISTS `__add_goodslang_specsjson`;
DELIMITER $$
CREATE PROCEDURE `__add_goodslang_specsjson`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods_lang'
                   AND column_name = 'specsjson') THEN
    ALTER TABLE `u_s_sitegoods_lang`
      ADD COLUMN `specsjson` varchar(4000) DEFAULT NULL
      COMMENT '规格译文,按默认语言的键当身份:{"默认键":{"k":"译键","v":"译值"}}。缺键回退主表'
      AFTER `keywords`;
  END IF;
END$$
DELIMITER ;

CALL `__add_goodslang_specsjson`();
DROP PROCEDURE `__add_goodslang_specsjson`;

-- 验一遍
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_lang'
      AND column_name = 'specsjson')                                          AS `规格译文列_应为1`,
  (SELECT COUNT(*) FROM information_schema.routines
    WHERE routine_schema = DATABASE() AND routine_name = '__add_goodslang_specsjson') AS `临时过程_应为0`;

-- 回滚(手工,按需):ALTER TABLE `u_s_sitegoods_lang` DROP COLUMN `specsjson`;
--   回滚前先把 jar 换回旧版——新版 upsertLang/selectLangs 引用了这一列。
