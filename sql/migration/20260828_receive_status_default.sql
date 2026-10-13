-- 存量到货月表 u_p_receive_yyyyMM.status 的默认值对齐（2026-08-28）。可重复执行。
--
-- ============================================================================
-- 问题
-- ============================================================================
--
-- 同一列在不同月份的表上默认值不一样：
--
--   u_p_receive_202608   DEFAULT '待质检'   无注释      ← 质检流程还在的时候建的
--   u_p_receive_202609   DEFAULT '待入库'   有注释      ← MonthlyTableInitializer 建的
--
-- 质检那套流程后来去掉了，MonthlyTableInitializer.buildPurchaseReceiveDDL 跟着改成了
-- 「待入库/已入库，由明细行派生」，但存量月表没人动过。于是往 8 月的表里插一行不带 status
-- 的记录，拿到的是 '待质检'——一个后端已经不认识的状态；往 9 月的表插同样一行是 '待入库'。
-- 同一段代码在跨月的两张表上行为不同，这种 bug 只在月初才出现，最难查。
--
-- 本脚本把所有存量月表拉齐到 MonthlyTableInitializer 的定义。
--
-- ============================================================================
-- 不动数据
-- ============================================================================
--
-- 8 月表里现存 '已质检' 4 行、'质检中' 2 行、'部分退货' 3 行，都是质检流程还在时留下的。
-- 本脚本一行都不改：历史单据记录的是当时实际发生的状态，把它们改写成 '已入库' 等于伪造记录。
-- 注释里写的 '待入库/已入库' 描述的是现在的模型，历史值不符合是正常的。
--
-- ============================================================================
-- 新拉取项目的人不用跑
-- ============================================================================
--
-- 月表不在 sql/01_schema_and_init.sql 里，全新的库由 MonthlyTableInitializer 建，
-- 建出来就是对的。本脚本只针对已经在跑的环境（本地、远程）。
--
-- ============================================================================
-- 执行
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260828_receive_status_default.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260828_receive_status_default.sql
--
-- 月表用 REGEXP '^u_p_receive_[0-9]{6}$' 精确匹配到 6 位月份，
-- 否则 LIKE 'u_p_receive_%' 会把 u_p_receivedetail_% 一起匹配上，与
-- finance_order_currency_20260821.sql 同口径。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdsorder.align_receive_status_default;
DELIMITER $$
CREATE PROCEDURE sdsorder.align_receive_status_default()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_p_receive_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname;
    IF done = 1 THEN LEAVE read_loop; END IF;

    -- 已经对齐的表直接跳过，这样重跑是纯读操作，不会在每张月表上都做一次 ALTER。
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
       WHERE table_schema = 'sdsorder' AND table_name = tname AND column_name = 'status'
         AND NOT (column_default <=> '待入库'
                  AND column_comment = '待入库/已入库，由明细行派生；发货单整单入库，没有中间态')
    ) THEN
      -- 用 MODIFY 而不是 ALTER COLUMN SET DEFAULT：后者改不了列注释，
      -- 而注释正是「这一列现在到底有哪几个取值」的唯一记录点，不能只对上默认值。
      -- 列定义必须与 MonthlyTableInitializer.buildPurchaseReceiveDDL 逐字一致，
      -- 改一边就要改另一边，否则下个月新建的表又会和存量表对不上。
      SET @s = CONCAT('ALTER TABLE sdsorder.`', tname,
        '` MODIFY COLUMN `status` varchar(20) DEFAULT ''待入库''',
        ' COMMENT ''待入库/已入库，由明细行派生；发货单整单入库，没有中间态''');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL sdsorder.align_receive_status_default();
DROP PROCEDURE sdsorder.align_receive_status_default;

-- 核对：所有月表应当只剩一种 (默认值, 注释) 组合。
SELECT table_name AS '月表', column_default AS '默认值', column_comment AS '注释'
  FROM information_schema.columns
 WHERE table_schema = 'sdsorder'
   AND table_name REGEXP '^u_p_receive_[0-9]{6}$'
   AND column_name = 'status'
 ORDER BY table_name;
