-- =====================================================================
-- u_s_sitegoods：补上商品档案四列（goodsname / spec / imgurl / mainimgs）
-- =====================================================================
--
-- 依据：docs/独立站与ERP的边界.md §5.4「商品：从『挑』变成『录』」。
--
-- 原来商品名、规格、图片都在 ERP 的 sdsshop.u_m_waregoods 里，站点侧只存售价、
-- 分类、推荐位这些站点属性，取数时 join 回去。那条 join 是一条跨库互通——库一搬走
-- 它连语法上都成立不了，所以商品必须变成站点自己的完整档案。
--
-- =====================================================================
-- 什么时候需要跑这一份
-- =====================================================================
--
-- **只在「库是这次改动之前建的」时候需要。**
--
-- sql/station/01_station_schema.sql 里 u_s_sitegoods 的定义已经带上这四列了，
-- 但那个脚本全文是 `CREATE TABLE IF NOT EXISTS`——它对**已经存在**的表什么都不做
-- （脚本头部那句「跑在已有数据的库上不会毁数据，但也修不好结构」就是指这个）。
--
-- 所以：
--   · 全新的站点服务器：跑 01_station_schema.sql 就够了，**不用**跑这一份；
--   · ERP 那台机器上那个老的 sdsstation：需要跑这一份，否则站点后端一查商品就报
--         Unknown column 'sg.goodsname' in 'field list'
--     （表现是 /openapi/site/products 返回 500，而 /openapi/site/info 是好的）。
--
-- =====================================================================
-- 跑的顺序：必须在 mysqldump 之前
-- =====================================================================
--
-- 按 boundary §10.4 第⑦步，数据是「ERP 那台 mysqldump → 站点服务器 restore」。
-- 下面第二段的回填要 join sdsshop，而**站点服务器上没有 sdsshop**。所以顺序是：
--
--     ① 在 ERP 那台机器上跑这一份（加列 + 回填）
--     ② 再 mysqldump sdsstation --ignore-table=sdsstation.u_s_site
--     ③ 在站点服务器上 restore
--
-- 顺序反了的话，搬过去的商品全都没有名字，而 u_m_waregoods 已经不在那台机器上了，
-- 补不回来——只能人工重录（§10.4⑧d 那句「人工录了几个月的商品文案没有别的地方有」
-- 说的就是这批数据）。
--
-- =====================================================================


-- =====================================================================
-- 一、加列
-- =====================================================================
--
-- MySQL 没有 `ADD COLUMN IF NOT EXISTS`（那是 MariaDB 的扩展），所以用
-- information_schema 判一下再拼语句。这么写是为了**可重复执行**：
-- 部署脚本被跑两遍是常态，第二遍直接报 Duplicate column name 会让人以为出事了。

USE `sdsstation`;

DROP PROCEDURE IF EXISTS `__add_sitegoods_profile_columns`;

DELIMITER $$
CREATE PROCEDURE `__add_sitegoods_profile_columns`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'goodsname') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `goodsname` varchar(200) DEFAULT NULL
      COMMENT '商品名称（内部叫法）。搬迁前来自 sdsshop.u_m_waregoods.goodsname，现在本站自己录'
      AFTER `goodscode`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'spec') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `spec` varchar(200) DEFAULT NULL
      COMMENT '规格型号。同上，原 u_m_waregoods.spec'
      AFTER `goodsname`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'imgurl') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `imgurl` varchar(500) DEFAULT NULL
      COMMENT '主图。原 u_m_waregoods.imgurl，siteimgs 为空时的回退来源'
      AFTER `spec`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'mainimgs') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `mainimgs` varchar(2000) DEFAULT NULL
      COMMENT '档案图组，逗号分隔。原 u_m_waregoods.mainimgs'
      AFTER `imgurl`;
  END IF;
END$$
DELIMITER ;

CALL `__add_sitegoods_profile_columns`();
DROP PROCEDURE `__add_sitegoods_profile_columns`;


-- =====================================================================
-- 二、从 ERP 的商品档案回填一次
-- =====================================================================
--
-- ⚠ 这一段**只能在 ERP 那台机器上跑**（那里 sdsstation 和 sdsshop 在同一个实例，
--   跨库限定名才成立）。站点服务器上没有 sdsshop，跑这一段会报
--   Unknown database 'sdsshop'——那是对的，不是配错了。
--
-- ⚠ 只回填**空值**（`WHERE sg.goodsname IS NULL`）。加列之后如果已经有人在管理台里
--   录过商品名，再跑一遍这个脚本不该把他录的覆盖回 ERP 那份旧的。
--
-- 这是**最后一次**从 ERP 读商品数据。跑完之后 u_s_sitegoods 就是商品的正本，
-- 站点侧的 mapper 里不会再有任何 join sdsshop。

UPDATE `u_s_sitegoods` sg
JOIN `sdsshop`.`u_m_waregoods` w ON w.`goodscode` = sg.`goodscode`
SET sg.`goodsname` = COALESCE(sg.`goodsname`, w.`goodsname`),
    sg.`spec`      = COALESCE(sg.`spec`,      w.`spec`),
    sg.`imgurl`    = COALESCE(sg.`imgurl`,    w.`imgurl`),
    sg.`mainimgs`  = COALESCE(sg.`mainimgs`,  w.`mainimgs`)
WHERE sg.`goodsname` IS NULL
   OR sg.`spec`      IS NULL
   OR sg.`imgurl`    IS NULL
   OR sg.`mainimgs`  IS NULL;


-- =====================================================================
-- 三、验一遍
-- =====================================================================
--
-- 回填完还剩多少条没名字。>0 说明那些 goodscode 在 ERP 的商品档案里也找不到
-- （历史脏数据，比如测试时手插的），要人工在管理台里补上，否则前台商品列表里
-- 会出现没有标题的卡片。

SELECT COUNT(*) AS `没有商品名的条数`
FROM `u_s_sitegoods`
WHERE `goodsname` IS NULL OR `goodsname` = '';
