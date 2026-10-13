-- 发货批次「仓库驳回」（2026-09-11）。可重复执行，跑两遍结果一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 给发货批次月表 u_f_shipbatch_YYYYMM 加三列：
--
--   rejectuser    驳回人（仓库）
--   rejecttime    驳回时间
--   rejectreason  驳回原因，最近一次
--
-- 没有数据要回填：历史批次从来没被驳回过，三列一律为 NULL。
--
-- ============================================================================
-- 为什么要有「驳回」
-- ============================================================================
--
-- 这条链上真正不可逆的只有最后一格：「完成」才开销售出库单、才扣库存、才回写订单
-- 已发量。在那之前——包括「已发货」——货其实一直还在仓库里，「已发货」只表示装了箱、
-- 承运商和物流单号录好了，在等人上门。
--
-- 而现实里出岔子恰恰在这段等待期：装箱装错了货、面单贴错了箱、客户临时改数量。
-- 承运商还没来，拆开重装就是了，可系统里这张批次已经被推到「已发货」，
-- 改不了、也取消不了（cancelBatch 挡的是「已发货」），只能硬着头皮点「完成」
-- 把错的货扣掉，再走一遍销售退货——账面上凭空多出一进一出两笔，
-- 库存流水里留下一对根本没发生过的出入库。
--
-- 加一个驳回口子，把「已提交仓库 / 已打印单据 / 已发货」三格都退回「待确认」。
--
-- 为什么退到「待确认」而不是退一格：批次的修改入口（updatePending）只认「待确认」，
-- 退到别的状态等于退了个寂寞——人还是改不了数量、改不了仓库、改不了计划日期。
--
-- ============================================================================
-- 为什么驳回原因单独落列，不塞进 remark
-- ============================================================================
--
-- 批次退回「待确认」之后，处理它的是业务员，而按驳回按钮的是仓库。业务员打开这张单
-- 第一件事是问「为什么退回来」，没有答案就改不动。
--
-- remark 是业务员自己建单时写的备注（发货注意事项之类），仓库往里写会把它冲掉；
-- 追加又会越滚越长，几次来回之后没人分得清哪句是谁写的。单独三列干净得多。
--
-- 只留最近一次、下次驳回整体覆盖：完整的驳回历史在 sys_oper_log 里（接口带
-- @Log(title = "发货批次驳回")，请求体一起记了），批次上要的只是「这张单现在为什么
-- 回来了」。批次重新走完流程后这三列不清空，留作痕迹。
--
-- ============================================================================
-- 驳回时抹掉哪些字段
-- ============================================================================
--
-- confirmuser / confirmtime / shipuser / shiptime / actualshiptime 一并置空。
-- 这几列记的是「这些事发生过」，批次退回草稿之后它们就不成立了，留着会出现一张
-- 「待确认但实际发货时间是上周二」的单子。重走流程时 updateStatus 会重新写上。
--
-- carrier / logisticsno 保留。典型场面是拆箱重装，面单多半还贴在箱子上、照用不误；
-- 真要换，批次回到「待确认」后在订单侧的修改弹窗里就能改。
--
-- ============================================================================
-- ⚠ 历史月表必须靠这个脚本刷
-- ============================================================================
--
-- MonthlyTableInitializer 的 buildShipBatchDDL 用的是 CREATE TABLE IF NOT EXISTS，
-- 只管得了以后新建的月表，已经存在的一张都不会动。同一份 DDL 也登记在该类的
-- ADDED_COLUMNS 里（启动时逐表补列），但那只覆盖本进程会碰到的月份——
-- 早于当月的历史月表要靠这个脚本刷齐。
--
-- 缺列的后果不是少显示一个字段：selectList / selectByCode 的列清单里有这三列，
-- 缺一列整张表的查询直接报 Unknown column，那个月的发货批次页面全打不开。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdsorder.add_shipbatch_reject_columns;
DELIMITER $$
CREATE PROCEDURE sdsorder.add_shipbatch_reject_columns()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);

  DECLARE cur_batch CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur_batch;
  batch_loop: LOOP
    FETCH cur_batch INTO tname;
    IF done = 1 THEN LEAVE batch_loop; END IF;

    -- 三列分别判断，不合成一条 ALTER：脚本可能被跑过一半（上次执行中断、
    -- 或者某张表已经被 ADDED_COLUMNS 补过一列），合起来判断会整条跳过
    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'rejectuser') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `rejectuser` varchar(50) DEFAULT NULL
        COMMENT ''驳回人（仓库）''
        AFTER `shiptime`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'rejecttime') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `rejecttime` datetime DEFAULT NULL
        COMMENT ''驳回时间''
        AFTER `rejectuser`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'rejectreason') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `rejectreason` varchar(500) DEFAULT NULL
        COMMENT ''驳回原因（最近一次）。仓库把批次退回待确认时必填''
        AFTER `rejecttime`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_batch;
END$$
DELIMITER ;

CALL sdsorder.add_shipbatch_reject_columns();
DROP PROCEDURE IF EXISTS sdsorder.add_shipbatch_reject_columns;

SELECT CONCAT('已处理发货批次月表 ', COUNT(*), ' 张') AS msg
  FROM information_schema.tables
 WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$';
