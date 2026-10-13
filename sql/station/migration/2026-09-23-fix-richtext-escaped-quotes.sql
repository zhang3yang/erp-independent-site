-- =====================================================================
-- 修复「富文本里的属性被写成 \"」的历史数据
-- =====================================================================
--
-- ⚠ 只跑在独立站那台（库 sdsstation）。可重复执行。
--
-- 背景：station-console 的 HTMLFilter.processTag 从 ruoyi-common 抄过来时多带了一个
--   反斜杠，属性被写成 src=\"https://…\"。反斜杠是真写进库的，于是前台
--   v-html 出来的每个 <img> / <a> 都废掉（src 被浏览器解析成裸的 `\`，图片一律 404）。
--
--   代码那半边已经修了（HTMLFilter 里那一行的注释记着原因，回归测试见
--   RichTextCleanerTest）。这个脚本把**已经存进库的**坏数据捞回来。
--
-- ---------------------------------------------------------------------
-- ⚠ 判定为什么用 LOCATE(CONCAT(CHAR(92),'"')) 而不是 LIKE '%\\"%'
-- ---------------------------------------------------------------------
--   两者在 SQL 里**不等价**，2026-09-23 实测踩过：
--     'A"B'（正常内容，没有反斜杠） LIKE '%\\"%'  →  1（命中！）
--     LOCATE(CONCAT(CHAR(92),'"'), 'A"B')         →  0
--   LIKE 那个写法里的 `\\"` 经客户端 + 服务端两层转义之后落到模式里的是一个普通的
--   双引号，于是**把所有正常内容都判成坏的**。用它当 WHERE，等于给整库富文本做一次
--   无意义的 REPLACE；用它当复查，永远返回非 0，等于没有复查。
--
--   CHAR(92) 是反斜杠，CONCAT 拼出来的模式不经过任何转义，是什么就是什么。
--
--   REPLACE 那边用 '\\"' 是可以的（实测能正确把 \" 换成 "），但为了和判定统一、
--   少一层要人脑解转义的地方，这里也一律用 CHAR(92) 拼。
--
-- 顺序：① 普查看影响面 → ② 修商品详情 → ③ 修博客（表不在就跳过）→ ④ 复查。

-- ---------------------------------------------------------------------
-- ① 普查：哪些行、多少个坏属性
-- ---------------------------------------------------------------------
SELECT '商品详情' AS 位置, sitecode, goodscode, lang,
       (CHAR_LENGTH(`description`) - CHAR_LENGTH(REPLACE(`description`, CONCAT(CHAR(92), '"'), ''))) / 2 AS 坏属性数
  FROM `u_s_sitegoods_lang`
 WHERE LOCATE(CONCAT(CHAR(92), '"'), `description`) > 0;

-- ---------------------------------------------------------------------
-- ② 商品详情正文
-- ---------------------------------------------------------------------
START TRANSACTION;

UPDATE `u_s_sitegoods_lang`
   SET `description` = REPLACE(REPLACE(`description`,
                        CONCAT(CHAR(92), '"'), '"'),
                        CONCAT(CHAR(92), ''''), '''')
 WHERE LOCATE(CONCAT(CHAR(92), '"'), `description`) > 0
    OR LOCATE(CONCAT(CHAR(92), ''''), `description`) > 0;

COMMIT;

-- ---------------------------------------------------------------------
-- ③ 博客正文
-- ---------------------------------------------------------------------
-- 和商品详情是同一条写入路径（SitePostService → RichTextCleaner），所以理论上同病。
--
-- ⚠ 2026-09-23 在 esvafit.vclinker.com 上实测：u_s_post 和 u_s_post_lang **都是 0 行**
--   （这个站没发过博客文章），所以这一段当时是空跑。留着是为了别的站/以后有文章时
--   一起修干净，别出现「商品好了、博客还是坏的」这种半修状态。
--
-- ⚠ u_s_post_lang 的主键是 (postcode, lang)，**没有 sitecode 列**——
--   站点归属在 u_s_post 上。所以这里只按 postcode/lang，不要照商品那张表的写法加 sitecode，
--   加了会报 1054。
--
-- 跑之前先确认表在不在：
--     SELECT table_name FROM information_schema.tables
--      WHERE table_schema = DATABASE() AND table_name IN ('u_s_post','u_s_post_lang');
--   返回 0 行 = 本站没开博客，整段跳过。

-- START TRANSACTION;
--
-- UPDATE `u_s_post_lang`
--    SET `content` = REPLACE(REPLACE(`content`,
--                       CONCAT(CHAR(92), '"'), '"'),
--                       CONCAT(CHAR(92), ''''), '''')
--  WHERE LOCATE(CONCAT(CHAR(92), '"'), `content`) > 0
--     OR LOCATE(CONCAT(CHAR(92), ''''), `content`) > 0;
--
-- COMMIT;

-- ---------------------------------------------------------------------
-- ④ 复查：每一条都应返回 0
-- ---------------------------------------------------------------------
SELECT '商品详情残留' AS 项, COUNT(*) AS 应为0
  FROM `u_s_sitegoods_lang`
 WHERE LOCATE(CONCAT(CHAR(92), '"'), `description`) > 0
    OR LOCATE(CONCAT(CHAR(92), ''''), `description`) > 0;

-- 博客（表在、且有坏数据时才跑）
-- SELECT '博客正文残留' AS 项, COUNT(*) AS 应为0
--   FROM `u_s_post_lang`
--  WHERE LOCATE(CONCAT(CHAR(92), '"'), `content`) > 0
--     OR LOCATE(CONCAT(CHAR(92), ''''), `content`) > 0;

-- ---------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------
-- ⚠ 没法精确回滚（这一步是有损的：把 \" 变回 " 之后就区分不出来了）。
--   要保险就先备份再跑 ②：
--     CREATE TABLE bak_20260923_goodslang_desc AS SELECT * FROM u_s_sitegoods_lang;
--   回滚：
--     UPDATE u_s_sitegoods_lang t
--       JOIN bak_20260923_goodslang_desc b USING (shop_code, sitecode, goodscode, lang)
--        SET t.description = b.description;
