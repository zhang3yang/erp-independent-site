-- =====================================================================
-- 一次性清理：删掉 12023924 / 12023925 / 12023926 以便重新录入
-- 商家 sp708xm02q（天津康万家）
-- =====================================================================
--
-- 为什么必须动库：这三个商品的占用全部来自一张【已审核】的采购入库单
-- RK20496983032（2026-09-10，天津仓库，供应商「生产部」，成本额 0）。
-- DocumentServiceImpl.deleteDocument 只允许删「待审核」的单，系统也没有
-- 反审核 / 红冲，所以界面上无论如何退不回去。
--
-- 这张单的全部足迹（已逐表核对，2026-09-11）：
--   sdsorder.u_f_docmain_202609    1 行  单头（sourcedoccode = DIRECT-…，没有上游采购单）
--   sdsorder.u_f_docdetail_202609  3 行  三个商品各一行，25 / 40 / 8 条
--   sdsorder.u_f_ledger_202609     3 行  入库过账流水
--   sdsshop.u_m_stock              3 行  余额行 25 / 40 / 8，成本额 0
--   sdsshop.u_m_goods_partner      3 行  供应商绑定（lastdoccode 指向这张单）
--   sdsshop.u_m_waregoods          3 行  商品本身
-- 没有财务流水、没有采购单 / 收货单、没有批次、没有预留、没有生产引用，
-- 所以整条链可以整体撤掉，不会留下对不上的账。
--
-- ⚠ 会删数据，且跑在生产库上。第 1 段先做备份表，第 2 段才删。
-- ⚠ 成本额全是 0，删掉不影响任何金额；但 73 条实物库存会跟着消失，
--    确认这批货本来就是错录的、仓库里不存在，再往下跑。
--
-- 用法：
--   mysql -h <主机> -uroot -p --default-character-set=utf8mb4 < purge_goods_12023924_26.sql
-- =====================================================================

SET NAMES utf8mb4;
SET @shop  := 'sp708xm02q';
SET @goods := '12023924,12023925,12023926';
SET @doc   := 'RK20496983032';

-- ---------------------------------------------------------------------
-- 1. 备份：把要删的行原样存到 _bak_20260911 表里，万一录错了能贴回去
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sdsshop.bak_20260911_waregoods;
CREATE TABLE sdsshop.bak_20260911_waregoods AS
  SELECT * FROM sdsshop.u_m_waregoods
   WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

DROP TABLE IF EXISTS sdsshop.bak_20260911_stock;
CREATE TABLE sdsshop.bak_20260911_stock AS
  SELECT * FROM sdsshop.u_m_stock
   WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

DROP TABLE IF EXISTS sdsshop.bak_20260911_goods_partner;
CREATE TABLE sdsshop.bak_20260911_goods_partner AS
  SELECT * FROM sdsshop.u_m_goods_partner
   WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

DROP TABLE IF EXISTS sdsshop.bak_20260911_docmain;
CREATE TABLE sdsshop.bak_20260911_docmain AS
  SELECT * FROM sdsorder.u_f_docmain_202609
   WHERE shop_code = @shop AND doccode = @doc;

DROP TABLE IF EXISTS sdsshop.bak_20260911_docdetail;
CREATE TABLE sdsshop.bak_20260911_docdetail AS
  SELECT * FROM sdsorder.u_f_docdetail_202609
   WHERE shop_code = @shop AND doccode = @doc;

DROP TABLE IF EXISTS sdsshop.bak_20260911_ledger;
CREATE TABLE sdsshop.bak_20260911_ledger AS
  SELECT * FROM sdsorder.u_f_ledger_202609
   WHERE shop_code = @shop AND doc_code = @doc;

SELECT '--- 备份行数（应为 3 / 3 / 3 / 1 / 3 / 3）---' AS `段落`;
SELECT (SELECT COUNT(*) FROM sdsshop.bak_20260911_waregoods)     AS `商品`,
       (SELECT COUNT(*) FROM sdsshop.bak_20260911_stock)         AS `库存`,
       (SELECT COUNT(*) FROM sdsshop.bak_20260911_goods_partner) AS `供应商绑定`,
       (SELECT COUNT(*) FROM sdsshop.bak_20260911_docmain)       AS `单头`,
       (SELECT COUNT(*) FROM sdsshop.bak_20260911_docdetail)     AS `单据明细`,
       (SELECT COUNT(*) FROM sdsshop.bak_20260911_ledger)        AS `流水`;

-- ---------------------------------------------------------------------
-- 2. 删除。顺序：先流水 → 明细 → 单头 → 绑定 → 库存 → 商品
--    （先删引用方再删被引用方，中途中断也不会剩下指向空商品的行）
-- ---------------------------------------------------------------------
START TRANSACTION;

DELETE FROM sdsorder.u_f_ledger_202609
 WHERE shop_code = @shop AND doc_code = @doc;

DELETE FROM sdsorder.u_f_docdetail_202609
 WHERE shop_code = @shop AND doccode = @doc;

DELETE FROM sdsorder.u_f_docmain_202609
 WHERE shop_code = @shop AND doccode = @doc;

DELETE FROM sdsshop.u_m_goods_partner
 WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

DELETE FROM sdsshop.u_m_stock
 WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

DELETE FROM sdsshop.u_m_waregoods
 WHERE shop_code = @shop AND FIND_IN_SET(goodscode, @goods) > 0;

COMMIT;

-- ---------------------------------------------------------------------
-- 3. 复核：三个编码应该在全库都查不到了
--    （复核逻辑与 who_uses_goods.sql 第 2 段相同）
-- ---------------------------------------------------------------------
SET SESSION group_concat_max_len = 4194304;
SELECT '--- 残留引用（应该一行都没有）---' AS `段落`;

SELECT GROUP_CONCAT(
         CONCAT('SELECT ''', c.table_schema, '.', c.table_name, ''' AS tbl, `',
                c.column_name, '` AS goods, COUNT(*) AS hits FROM `',
                c.table_schema, '`.`', c.table_name, '` WHERE FIND_IN_SET(`',
                c.column_name, '`, @goods) > 0 GROUP BY `', c.column_name, '`')
         SEPARATOR ' UNION ALL ')
  INTO @scan
  FROM information_schema.columns c
 WHERE c.table_schema IN ('sdsshop', 'sdsorder')
   AND c.column_name IN ('goodscode', 'productcode', 'goods_code', 'product_code')
   AND c.table_name NOT LIKE 'bak_%'
   AND EXISTS (SELECT 1 FROM information_schema.columns s
                WHERE s.table_schema = c.table_schema
                  AND s.table_name = c.table_name
                  AND s.column_name = 'shop_code');

SET @scan = CONCAT('SELECT goods AS `商品编码`, tbl AS `表`, hits AS `命中行数` FROM (',
                   @scan, ') t ORDER BY goods, tbl');
PREPARE stmt FROM @scan;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 重录之后，别忘了这两件事
--    a) 商品编码是主键的一部分，重录可以沿用原来的 12023924/25/26；
--       新录进去的 usedflag 是 0，字段随便改，直到第一张单据引用它。
--    b) 库存那 73 条是这张错单带进来的。重录完商品，如果这批货实际存在，
--       重新开一张采购入库单把量和成本录对；如果本来就不存在，就不用补单。
--    c) 备份表确认不需要之后再删：
--       DROP TABLE sdsshop.bak_20260911_waregoods, sdsshop.bak_20260911_stock,
--                  sdsshop.bak_20260911_goods_partner, sdsshop.bak_20260911_docmain,
--                  sdsshop.bak_20260911_docdetail, sdsshop.bak_20260911_ledger;
-- ---------------------------------------------------------------------
