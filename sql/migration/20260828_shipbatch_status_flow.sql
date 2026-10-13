-- 发货批次状态机改造（2026-08-28）。可重复执行，先跑后跑都安全。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 旧：待确认 → 已确认 → 已备货 → 已发货（这一步扣库存，终态）
-- 新：待确认 → 已提交仓库 → 已打印单据 → 已发货 → 已完成（这一步才扣库存，终态）
--
-- 拆开的理由：仓库拿到批次先打单拣货，货交给承运商叫「已发货」，但那时还可能有变数，
-- 而扣库存不可逆，所以出库单、库存扣减、订单已发量回写整体推迟到「完成」那一刻。
-- 「待确认」也换了含义：现在是「业务员还没提交给仓库」，提交之前仓库根本看不到这张批次。
--
-- ============================================================================
-- ⚠「已发货」这三个字在新旧两套状态机里含义相反，不能照字面搬
-- ============================================================================
--
--   旧的已发货 = 出库单已过账、库存已扣、shippedqty 已加  → 对应新的「已完成」
--   新的已发货 = 货刚交给承运商，一分库存都没动          → 原样留着，等人点「完成」
--
-- 光按状态字符串搬，会把新后端刚产生的「已发货」误升成「已完成」：那些批次的库存
-- 永远不会被扣，订单已发量也永远不会涨，而界面上「完成」按钮已经消失，人工也补不回来。
-- （本脚本第一版就踩了这个坑，所以现在改成按「有没有一张已审核的销售出库单挂在这张
--   批次上」来判断，而不是按状态字符串——出库单是旧流程扣过库存的铁证。）
--
-- 因此本脚本对部署顺序没有要求：后端先上线也好、脚本先跑也好，结果都一样。
-- 第 1.4 段还会把上一版脚本误升过的行退回「已发货」，让它们能重新走「完成」。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260828_shipbatch_status_flow.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260828_shipbatch_status_flow.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 逐月表迁移状态值 + 同步列注释
--    status 上没有 CHECK 约束，直接 UPDATE 即可。
-- ============================================================

DROP PROCEDURE IF EXISTS sdsorder.migrate_shipbatch_status;
DELIMITER $$
CREATE PROCEDURE sdsorder.migrate_shipbatch_status()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur_doc CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_docmain_[0-9]{6}$';
  DECLARE cur_batch CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  -- ---- 1.1 先攒出「真的过过账」的批次号 ----
  -- 销售出库单的 sourcedoccode 存批次号、sourcedoctype 存 XSFH
  -- （InventoryDocumentTypes.SHIP_BATCH_SOURCE），已审核＝库存真扣过。
  -- 出库单按单据日期落月表，跟批次的月份不一定是同一张，所以要全月表扫一遍。
  -- 排序规则必须显式跟月表对齐：临时表默认吃服务器的 utf8mb4_general_ci，
  -- 而月表是 utf8mb4_0900_ai_ci，两边直接比会报 1267 Illegal mix of collations。
  DROP TEMPORARY TABLE IF EXISTS tmp_posted_ship;
  CREATE TEMPORARY TABLE tmp_posted_ship (
    shop_code varchar(50) NOT NULL,
    shipcode  varchar(50) NOT NULL,
    PRIMARY KEY (shop_code, shipcode)
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

  OPEN cur_doc;
  doc_loop: LOOP
    FETCH cur_doc INTO tname;
    IF done = 1 THEN LEAVE doc_loop; END IF;

    SET @s = CONCAT('INSERT IGNORE INTO tmp_posted_ship ',
      'SELECT DISTINCT shop_code, sourcedoccode FROM sdsorder.`', tname,
      '` WHERE doctype = ''CK'' AND sourcedoctype = ''XSFH''',
      '   AND confirmstate = ''已审核'' AND sourcedoccode IS NOT NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur_doc;
  SET done = 0;

  OPEN cur_batch;
  batch_loop: LOOP
    FETCH cur_batch INTO tname;
    IF done = 1 THEN LEAVE batch_loop; END IF;

    -- ---- 1.2 旧「已发货」→「已完成」，只搬过过账的那些 ----
    -- 没有出库单的「已发货」是新流程刚发出去、还没点完成的批次，必须原样留着
    SET @s = CONCAT('UPDATE sdsorder.`', tname, '` b SET b.status = ''已完成''',
      ' WHERE b.status = ''已发货''',
      '   AND EXISTS (SELECT 1 FROM tmp_posted_ship p',
      '                WHERE p.shop_code = b.shop_code AND p.shipcode = b.shipcode)');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    -- ---- 1.3 中间两态改名。这两个新旧含义一致，直接搬 ----
    -- 旧「已确认」= 仓库确认能发 → 新「已提交仓库」
    SET @s = CONCAT('UPDATE sdsorder.`', tname,
      '` SET status = ''已提交仓库'' WHERE status = ''已确认''');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    -- 旧「已备货」= 货备好等物流 → 新「已打印单据」（下一步都是发货）
    SET @s = CONCAT('UPDATE sdsorder.`', tname,
      '` SET status = ''已打印单据'' WHERE status = ''已备货''');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    -- ---- 1.4 修复被上一版脚本误升的行：已完成但没有出库单 → 退回「已发货」----
    -- 这种行的库存根本没扣、订单已发量也没涨，退回去人才能重新点「完成」。
    -- 正常完成的批次必定挂着一张已审核的出库单（同一个事务里开的），
    -- 而已审核的出库单在本系统里删不掉，所以这条不会误伤真正完成过的批次。
    SET @s = CONCAT('UPDATE sdsorder.`', tname, '` b SET b.status = ''已发货''',
      ' WHERE b.status = ''已完成''',
      '   AND NOT EXISTS (SELECT 1 FROM tmp_posted_ship p',
      '                    WHERE p.shop_code = b.shop_code AND p.shipcode = b.shipcode)');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    -- ---- 1.5 列注释与 MonthlyTableInitializer.buildShipBatchDDL 保持一致 ----
    SET @s = CONCAT('ALTER TABLE sdsorder.`', tname,
      '` MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT ''待确认''',
      ' COMMENT ''待确认/已提交仓库/已打印单据/已发货/已完成/已取消。库存只在「已完成」时扣''');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur_batch;

  DROP TEMPORARY TABLE tmp_posted_ship;
END$$
DELIMITER ;

CALL sdsorder.migrate_shipbatch_status();
DROP PROCEDURE sdsorder.migrate_shipbatch_status;

-- ============================================================
-- 2. 校验
-- ============================================================

DROP PROCEDURE IF EXISTS sdsorder.check_shipbatch_status;
DELIMITER $$
CREATE PROCEDURE sdsorder.check_shipbatch_status()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur_doc CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_docmain_[0-9]{6}$';
  DECLARE cur_batch CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  DROP TEMPORARY TABLE IF EXISTS tmp_posted_ship;
  CREATE TEMPORARY TABLE tmp_posted_ship (
    shop_code varchar(50) NOT NULL, shipcode varchar(50) NOT NULL,
    PRIMARY KEY (shop_code, shipcode)
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_legacy;
  CREATE TEMPORARY TABLE tmp_shipbatch_legacy (
    table_name varchar(64), shipcode varchar(50), status varchar(20)
  );
  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_orphan;
  CREATE TEMPORARY TABLE tmp_shipbatch_orphan (
    table_name varchar(64), shipcode varchar(50), status varchar(20)
  );
  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_stats;
  CREATE TEMPORARY TABLE tmp_shipbatch_stats (
    table_name varchar(64), status varchar(20), rows_kept bigint
  );

  OPEN cur_doc;
  doc_loop: LOOP
    FETCH cur_doc INTO tname;
    IF done = 1 THEN LEAVE doc_loop; END IF;
    SET @s = CONCAT('INSERT IGNORE INTO tmp_posted_ship ',
      'SELECT DISTINCT shop_code, sourcedoccode FROM sdsorder.`', tname,
      '` WHERE doctype = ''CK'' AND sourcedoctype = ''XSFH''',
      '   AND confirmstate = ''已审核'' AND sourcedoccode IS NOT NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur_doc;
  SET done = 0;

  OPEN cur_batch;
  batch_loop: LOOP
    FETCH cur_batch INTO tname;
    IF done = 1 THEN LEAVE batch_loop; END IF;

    SET @s = CONCAT('INSERT INTO tmp_shipbatch_legacy ',
      'SELECT ''', tname, ''', shipcode, status FROM sdsorder.`', tname,
      '` WHERE status IN (''已确认'', ''已备货'')');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    SET @s = CONCAT('INSERT INTO tmp_shipbatch_orphan ',
      'SELECT ''', tname, ''', b.shipcode, b.status FROM sdsorder.`', tname, '` b',
      ' WHERE b.status = ''已完成''',
      '   AND NOT EXISTS (SELECT 1 FROM tmp_posted_ship p',
      '                    WHERE p.shop_code = b.shop_code AND p.shipcode = b.shipcode)');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

    SET @s = CONCAT('INSERT INTO tmp_shipbatch_stats ',
      'SELECT ''', tname, ''', status, COUNT(*) FROM sdsorder.`', tname, '` GROUP BY status');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur_batch;

  -- 2.1 期望 0 行：不该再有旧状态值
  SELECT * FROM tmp_shipbatch_legacy;
  -- 2.2 期望 0 行：「已完成」却没有已审核出库单的，就是没扣过库存的假完成
  SELECT * FROM tmp_shipbatch_orphan;
  -- 2.3 迁移后各状态的行数分布
  SELECT * FROM tmp_shipbatch_stats ORDER BY table_name, status;

  DROP TEMPORARY TABLE tmp_posted_ship;
  DROP TEMPORARY TABLE tmp_shipbatch_legacy;
  DROP TEMPORARY TABLE tmp_shipbatch_orphan;
  DROP TEMPORARY TABLE tmp_shipbatch_stats;
END$$
DELIMITER ;

CALL sdsorder.check_shipbatch_status();
DROP PROCEDURE sdsorder.check_shipbatch_status;

-- ============================================================
-- 回滚
-- ============================================================
-- 只在确认没有新流程产生的数据时可用：新的「已发货」与旧的「已发货」含义相反，
-- 回滚会把没扣过库存的批次说成扣过了。逐月表执行，下面以 202608 为例。
--
-- UPDATE sdsorder.u_f_shipbatch_202608 SET status = '已发货' WHERE status = '已完成';
-- UPDATE sdsorder.u_f_shipbatch_202608 SET status = '已确认' WHERE status = '已提交仓库';
-- UPDATE sdsorder.u_f_shipbatch_202608 SET status = '已备货' WHERE status = '已打印单据';
