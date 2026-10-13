-- 单据「商品内容」摘要列（2026-09-03）。可重复执行，跑两遍结果一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 采购订单、入库单、出库单、销售订单四个列表页只显示单号、往来单位、金额，
-- 看不出这张单装的是什么货，非点进详情不可。给三张主表各加一列 goodssummary，
-- 存一句「可乐等3种」，列表直接读。
--
--   采购订单  sdsshop.u_p_purchase          ← sdsshop.u_p_purchasedetail
--   入库/出库  sdsorder.u_f_docmain_YYYYMM   ← sdsorder.u_f_docdetail_YYYYMM
--   销售订单  sdsorder.u_o_order_YYYYMM     ← sdsorder.u_o_orderdetail_YYYYMM
--
-- 入库和出库共用 u_f_docmain，靠 doctype 区分，所以只有三张表不是四张。
--
-- ============================================================================
-- 为什么是冗余列，不是查询时 GROUP_CONCAT
-- ============================================================================
--
-- 列表是跨月表 UNION 出来的，每多一张月表就多一次明细连接；而这个值只在建单和
-- 改单时变，读远多于写。列由服务层在写入时整体重算（GoodsSummaryBuilder），
-- 明细本来就是「全删再重建」，不做增量维护。
--
-- ⚠ 它是展示用的，不参与任何计算。对不上就重跑本脚本刷一遍，不影响账。
--
-- ============================================================================
-- ⚠ 历史月表必须靠这个脚本刷
-- ============================================================================
--
-- MonthlyTableInitializer 的 ADDED_COLUMNS 只补它启动时碰到的当月和下月，
-- 更早的月表它不会打开。而列表查询是把所有月表 UNION 起来的——少一张表少一列，
-- 整个列表页直接报 Unknown column。所以本脚本按 information_schema 遍历全部月表。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260903_goods_summary.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260903_goods_summary.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 采购订单主表（不是月表，单独处理）
-- ============================================================

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'sdsshop' AND table_name = 'u_p_purchase'
      AND column_name = 'goodssummary') > 0,
  'SELECT ''u_p_purchase.goodssummary 已存在，跳过'' AS msg',
  'ALTER TABLE `sdsshop`.`u_p_purchase`
     ADD COLUMN `goodssummary` varchar(255) DEFAULT NULL
     COMMENT ''商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算''
     AFTER `partnername`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sdsshop`.`u_p_purchase` m
   SET m.goodssummary = (
       SELECT CASE
                WHEN COUNT(*) = 0 THEN NULL
                WHEN COUNT(DISTINCT d.goodscode) = 1
                     THEN LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''), f.goodscode)
                                  FROM `sdsshop`.`u_p_purchasedetail` f
                                 WHERE f.shop_code = m.shop_code
                                   AND f.purchasecode = m.purchasecode
                                 ORDER BY f.lineno, f.detailcode LIMIT 1), 60)
                ELSE CONCAT(
                       LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''), f.goodscode)
                               FROM `sdsshop`.`u_p_purchasedetail` f
                              WHERE f.shop_code = m.shop_code
                                AND f.purchasecode = m.purchasecode
                              ORDER BY f.lineno, f.detailcode LIMIT 1), 60),
                       '等', COUNT(DISTINCT d.goodscode), '种')
              END
         FROM `sdsshop`.`u_p_purchasedetail` d
        WHERE d.shop_code = m.shop_code
          AND d.purchasecode = m.purchasecode);

-- ============================================================
-- 2. 月表：u_f_docmain_YYYYMM 和 u_o_order_YYYYMM
--    加列 + 按各自的明细月表回填。列已存在就只回填。
-- ============================================================

DROP PROCEDURE IF EXISTS sdsorder.fill_goods_summary;
DELIMITER $$
CREATE PROCEDURE sdsorder.fill_goods_summary()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE suffix CHAR(6);
  DECLARE detail_missing INT;

  DECLARE cur_docmain CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_docmain_[0-9]{6}$';
  DECLARE cur_order CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  -- ---- 2.1 单据主表（入库 / 出库共用） ----
  OPEN cur_docmain;
  doc_loop: LOOP
    FETCH cur_docmain INTO tname;
    IF done = 1 THEN LEAVE doc_loop; END IF;
    SET suffix = RIGHT(tname, 6);

    -- 明细月表缺失（历史遗留的空壳主表）就只加列不回填，不然拼出来的 SQL 会报表不存在
    IF (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = 'sdsorder'
           AND table_name = CONCAT('u_f_docdetail_', suffix)) = 0 THEN
      SET detail_missing = 1;
    ELSE
      SET detail_missing = 0;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'goodssummary') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `goodssummary` varchar(255) DEFAULT NULL
        COMMENT ''商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算''
        AFTER `partnername`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF detail_missing = 0 THEN
      SET @s = CONCAT(
        'UPDATE `sdsorder`.`', tname, '` m SET m.goodssummary = (
           SELECT CASE
                    WHEN COUNT(*) = 0 THEN NULL
                    WHEN COUNT(DISTINCT d.goodscode) = 1
                         THEN LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''''), f.goodscode)
                                      FROM `sdsorder`.`u_f_docdetail_', suffix, '` f
                                     WHERE f.shop_code = m.shop_code AND f.doccode = m.doccode
                                     ORDER BY f.lineno, f.detailcode LIMIT 1), 60)
                    ELSE CONCAT(
                           LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''''), f.goodscode)
                                   FROM `sdsorder`.`u_f_docdetail_', suffix, '` f
                                  WHERE f.shop_code = m.shop_code AND f.doccode = m.doccode
                                  ORDER BY f.lineno, f.detailcode LIMIT 1), 60),
                           ''等'', COUNT(DISTINCT d.goodscode), ''种'')
                  END
             FROM `sdsorder`.`u_f_docdetail_', suffix, '` d
            WHERE d.shop_code = m.shop_code AND d.doccode = m.doccode)');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_docmain;
  SET done = 0;

  -- ---- 2.2 销售订单主表 ----
  -- 列位置跟 MonthlyTableInitializer.buildOrderDDL 一致，放在 goodsinfo 后面。
  -- goodsinfo 是历史遗留的自由文本「商品详情」，和这一列不是一回事，别混用。
  OPEN cur_order;
  ord_loop: LOOP
    FETCH cur_order INTO tname;
    IF done = 1 THEN LEAVE ord_loop; END IF;
    SET suffix = RIGHT(tname, 6);

    IF (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = 'sdsorder'
           AND table_name = CONCAT('u_o_orderdetail_', suffix)) = 0 THEN
      SET detail_missing = 1;
    ELSE
      SET detail_missing = 0;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'goodssummary') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `goodssummary` varchar(255) DEFAULT NULL
        COMMENT ''商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算''
        AFTER `goodsinfo`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF detail_missing = 0 THEN
      SET @s = CONCAT(
        'UPDATE `sdsorder`.`', tname, '` m SET m.goodssummary = (
           SELECT CASE
                    WHEN COUNT(*) = 0 THEN NULL
                    WHEN COUNT(DISTINCT d.goodscode) = 1
                         THEN LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''''), f.goodscode)
                                      FROM `sdsorder`.`u_o_orderdetail_', suffix, '` f
                                     WHERE f.shop_code = m.shop_code AND f.ordercode = m.ordercode
                                     ORDER BY f.lineno, f.detailcode LIMIT 1), 60)
                    ELSE CONCAT(
                           LEFT((SELECT COALESCE(NULLIF(TRIM(f.goodsname), ''''), f.goodscode)
                                   FROM `sdsorder`.`u_o_orderdetail_', suffix, '` f
                                  WHERE f.shop_code = m.shop_code AND f.ordercode = m.ordercode
                                  ORDER BY f.lineno, f.detailcode LIMIT 1), 60),
                           ''等'', COUNT(DISTINCT d.goodscode), ''种'')
                  END
             FROM `sdsorder`.`u_o_orderdetail_', suffix, '` d
            WHERE d.shop_code = m.shop_code AND d.ordercode = m.ordercode)');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_order;
END$$
DELIMITER ;

CALL sdsorder.fill_goods_summary();
DROP PROCEDURE IF EXISTS sdsorder.fill_goods_summary;

-- ============================================================
-- 3. 核对
-- ============================================================
SELECT 'u_p_purchase' AS tbl,
       COUNT(*) AS rows_total,
       SUM(goodssummary IS NOT NULL) AS filled
  FROM `sdsshop`.`u_p_purchase`;
