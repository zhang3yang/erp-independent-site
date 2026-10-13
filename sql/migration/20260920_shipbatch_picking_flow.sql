-- 发货批次加「备货 + 快递单号」四格（2026-09-20）。可重复执行，跑几遍结果都一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 旧：待确认 → 已提交仓库 → 已打印单据 → 已发货 → 已完成（这一步扣库存，终态）
-- 新：待确认 → 已提交仓库 → 已打印备货单 → 备货完成 → 已录入快递单号
--                        → 已打印快递单号 → 已完成（仍然只有这一步扣库存，终态）
--
-- 中间多出来的三格各自换了一次责任人：
--
--   已打印备货单   仓库照着备货单去拣货
--   备货完成       仓库传备货照片，球交给业务员（照片至少一张）
--   已录入快递单号 业务员填快递单号、承运商，并敲定本次实发数量；这一步卡客户付款额度
--   已打印快递单号 仓库把面单打出来贴箱子上，等承运商
--
-- 库存的扣减位置<b>一点没变</b>，还是「完成」那一格。这次改的全是它前面的人工环节。
--
-- ============================================================================
-- 本脚本做四件事
-- ============================================================================
--
--   1. 给每张 u_f_shipbatch_YYYYMM 加五列：
--        pickphotos     备货照片（逗号分隔 URL）
--        pickuser       备货完成人
--        picktime       备货完成时间
--        logisticsuser  录入快递单号的人
--        logisticstime  录入快递单号时间
--   2. 迁移存量状态值（下一节详述）
--   3. 同步 status 列注释
--   4. 拆分权限串，让「仓库能点的」和「业务员能点的」不再共用一个开关
--
-- ============================================================================
-- 存量状态怎么映射，以及为什么
-- ============================================================================
--
--   已打印单据 → 已打印备货单     换个名字，含义一致：仓库拿着纸在拣货
--   已发货     → 已打印快递单号   见下
--
-- 旧的「已发货」＝承运商和物流单号已录、货装好箱在等人上门，库存一分没扣。
-- 新状态里和它含义最接近的是「已打印快递单号」：面单信息齐了、货在等承运商、库存没动，
-- <b>下一步就是点「完成」</b>——和这些单子在旧流程里的下一步完全一样。
--
-- 为什么不退到「已录入快递单号」（逼仓库在系统里补点一次「打印快递单号」）：
-- 这些批次的面单现实中<b>已经贴在箱子上了</b>，让仓库为了走完系统流程再打一张纸，
-- 纯属给在途的单子添一道返工。新单子从头走完整流程，老单子按原计划发完就行。
--
-- 其余状态（待确认 / 已提交仓库 / 已完成 / 已取消）新旧同名同义，不动。
--
-- 迁过来的行 pickphotos / pickuser / logisticsuser 都是 NULL：它们没走过新增的那几步。
-- 这不影响它们往下走——「至少一张备货照片」只在「备货完成」那一步校验，
-- 而这些单子已经过了那一格。
--
-- ============================================================================
-- ⚠ 跟代码的先后顺序：怎么排都行，但两边做完之前会有单子卡住
-- ============================================================================
--
-- 和 20260828 那次不同，这次的状态映射<b>没有歧义</b>：新代码一个「已发货」都不会再写出来
-- （STATUS_SHIPPED 这个常量已经删掉了），所以什么时候跑、跑几遍，结果都一样。
--
--   只跑脚本不发代码：老代码读到「已打印备货单」认不出来，那张批次的按钮会消失。
--   只发代码不跑脚本：新代码读到「已打印单据」同样认不出来，卡住不动；
--                     而且五列缺一列，selectList 直接报 Unknown column，整页打不开。
--
-- 所以两件事要挨着做。发完代码再跑一遍脚本最稳——中间新产生的单子也会被一起带过去。
--
-- ============================================================================
-- ⚠ 历史月表必须靠这个脚本刷
-- ============================================================================
--
-- MonthlyTableInitializer 的 buildShipBatchDDL 用的是 CREATE TABLE IF NOT EXISTS，
-- 只管得了以后新建的月表；同一份列定义也登记在该类的 ADDED_COLUMNS 里（启动时逐表补列），
-- 但那只覆盖本进程会碰到的月份——早于当月的历史月表要靠这个脚本刷齐。
--
-- 缺列的后果不是少显示一个字段：selectList / selectByCode 的列清单里有这五列，
-- 缺一列整张表的查询直接报 Unknown column，那个月的发货批次页面全打不开。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260920_shipbatch_picking_flow.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260920_shipbatch_picking_flow.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 逐月表补列 + 迁移状态值 + 同步列注释
--    status 上没有 CHECK 约束，直接 UPDATE 即可。
-- ============================================================

DROP PROCEDURE IF EXISTS sdsorder.upgrade_shipbatch_picking_flow;
DELIMITER $$
CREATE PROCEDURE sdsorder.upgrade_shipbatch_picking_flow()
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

    -- ---- 1.1 五列逐列判断后补 ----
    -- 不合成一条 ALTER：脚本可能被跑过一半（上次执行中断，或者某张表已经被
    -- MonthlyTableInitializer.ADDED_COLUMNS 补过一两列），合起来判断会整条跳过。
    -- AFTER 的位置与 buildShipBatchDDL 里的列序一致：全部排在 shiptime 之后、
    -- rejectuser 之前。
    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'pickphotos') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `pickphotos` varchar(2000) DEFAULT NULL
        COMMENT ''备货照片，逗号分隔的 URL。备货完成时至少一张（备货照片与产品照片不分类别，一列存完）''
        AFTER `shiptime`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'pickuser') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `pickuser` varchar(50) DEFAULT NULL
        COMMENT ''备货完成人（库管员）''
        AFTER `pickphotos`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'picktime') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `picktime` datetime DEFAULT NULL
        COMMENT ''备货完成时间''
        AFTER `pickuser`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'logisticsuser') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `logisticsuser` varchar(50) DEFAULT NULL
        COMMENT ''录入快递单号的人（业务员）''
        AFTER `picktime`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'logisticstime') = 0 THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '`
        ADD COLUMN `logisticstime` datetime DEFAULT NULL
        COMMENT ''录入快递单号时间''
        AFTER `logisticsuser`');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    -- ---- 1.2 状态值迁移 ----
    -- 两条都是纯改名，没有歧义：新代码一个「已打印单据」「已发货」都不会再写出来。
    SET @s = CONCAT('UPDATE sdsorder.`', tname,
      '` SET status = ''已打印备货单'' WHERE status = ''已打印单据''');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- 旧「已发货」＝面单已录、货在等承运商、库存没动，下一步就是点完成。
    -- 新流程里下一步同样是点完成的那一格，是「已打印快递单号」。
    SET @s = CONCAT('UPDATE sdsorder.`', tname,
      '` SET status = ''已打印快递单号'' WHERE status = ''已发货''');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- ---- 1.3 列注释与 MonthlyTableInitializer.buildShipBatchDDL 保持一致 ----
    SET @s = CONCAT('ALTER TABLE sdsorder.`', tname,
      '` MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT ''待确认''',
      ' COMMENT ''待确认/已提交仓库/已打印备货单/备货完成/已录入快递单号/已打印快递单号/已完成/已取消。库存只在「已完成」时扣''');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- ---- 1.4 发货人列的注释也跟着改 ----
    -- 口径变了：旧流程在「已发货」那一格写 shipuser，而那时货还在仓库里等承运商；
    -- 新流程挪到「完成」那一刻，和 actualshiptime 对齐。已有数据不动——
    -- 那些行记的是「谁把这批货交出去的」，人没变，只是时点的说法更准了。
    SET @s = CONCAT('ALTER TABLE sdsorder.`', tname,
      '` MODIFY COLUMN `shipuser` varchar(50) DEFAULT NULL',
      ' COMMENT ''发货人（在「完成」那一步落，那一刻货才真的离开仓库）''');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur_batch;
END$$
DELIMITER ;

CALL sdsorder.upgrade_shipbatch_picking_flow();
DROP PROCEDURE sdsorder.upgrade_shipbatch_picking_flow;

-- ============================================================
-- 2. 权限串拆分：仓库能点的和业务员能点的不再共用一个开关
-- ============================================================
--
-- 起因：旧流程里仓库的「打单」沿用了业务员「提交仓库」的权限串
-- （order:shipbatch:confirm），两个岗位共用一个开关，后端分不出谁该点哪个按钮。
-- 新流程两边来回交接四次，再共用就说不清责任了。
--
--   fn5105  仓库发货确认备货  order:shipbatch:confirm  →  order:shipbatch:print
--                             （改名为「仓库打印备货单/快递单」，两个打印动作共用）
--   fn5107  仓库备货完成      order:shipbatch:picking      ← 新增
--   fn8017  录入快递单号      order:shipbatch:logistics    ← 新增
--
-- 改完之后：
--   业务员  confirm（提交仓库）+ logistics（录入快递单号）+ add/edit/cancel
--   仓库    print（两个打印）  + picking（备货完成）      + ship（完成 / 驳回）
--
-- fn5105 是<b>原地改</b>而不是新建：原来勾了它的仓库角色会自动拿到新的打印权限，
-- 同时自动失去 confirm——而 confirm 本来就不该在仓库手里。
--
-- order:shipbatch:ship（fn5106 / fn8015）保持不变，现在它只管「完成」和「驳回」。

USE `sdssys`;

-- ----------------------------------------------------------------------------
-- 2.1 fn5105 原地改成打印权限
-- ----------------------------------------------------------------------------
UPDATE `sys_menu`
   SET `menu_name` = '仓库打印备货单/快递单',
       `perms` = 'order:shipbatch:print',
       `remark` = '仓库侧两个打印动作共用：打印备货单（已提交仓库→已打印备货单）、打印快递单号（已录入快递单号→已打印快递单号）'
 WHERE `fun_code` = 'fn5105';

-- ----------------------------------------------------------------------------
-- 2.1b 顺手把 fn5106 的名字也改准，权限串不动
-- ----------------------------------------------------------------------------
-- 它现在叫「仓库发货与完成」，是 20260909_shipbatch_menu_rename.sql 留下的名字，
-- 而「发货」这一格在本次改造里已经没了——这个权限点现在只管「完成」和「驳回」两件事。
--
-- ⚠ 那次改名只改了库、<b>没同步回 sql/01_schema_and_init.sql</b>，于是种子文件里
--   fn5105/fn5106 一直还是更早的老名字。本脚本把库和种子文件一起拉到同一个说法上，
--   免得下次有人照种子文件建库又建出第三种名字。
UPDATE `sys_menu`
   SET `menu_name` = '仓库发货完成/驳回',
       `remark` = '点「完成」开销售出库单扣库存，以及把批次「驳回」退给业务员'
 WHERE `fun_code` = 'fn5106';

-- ----------------------------------------------------------------------------
-- 2.2 新增两个权限点
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`fun_code`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `query`,
   `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`,
   `icon`, `create_by`, `create_time`, `remark`)
VALUES
  ('fn5107', 'fn5103', '仓库备货完成', 4, '#', '', '', '', 1, 0, 'F', '0', '0',
   'order:shipbatch:picking',
   '#', 'wczladmin', sysdate(), '上传备货照片并点备货完成（已打印备货单→备货完成）。照片至少一张'),
  ('fn8017', 'fn8010', '发货批次录入快递单号', 7, '#', '', '', '', 1, 0, 'F', '0', '0',
   'order:shipbatch:logistics',
   '#', 'wczladmin', sysdate(), '业务员填快递单号、承运商与本次实发数量（备货完成→已录入快递单号）。这一步卡客户付款额度');

-- ----------------------------------------------------------------------------
-- 2.3 发给对应岗位的现有角色
-- ----------------------------------------------------------------------------
-- 按<b>最接近的老权限点</b>推，不按页面推：页面权限（fn5103 / fn8010）里可能混着
-- 只读角色，把动作权限发给他们等于凭空放权。
--
--   fn5107（备货完成）← 谁本来就能在仓库发货页往前推批次，就是 fn5106（ship）
--   fn8017（录快递单号）← 谁本来就能在订单侧确认批次，就是 fn8014（confirm）
--
-- 连带已删除标记的角色一起发，理由同 20260910_price_visibility.sql：
-- 漏发会在角色恢复时变成一个找不到原因的差异。
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT `role_code`, 'fn5107' FROM `sys_role_menu` WHERE `fun_code` = 'fn5106';

INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`)
SELECT `role_code`, 'fn8017' FROM `sys_role_menu` WHERE `fun_code` = 'fn8014';

-- ----------------------------------------------------------------------------
-- 2.4 挂到对应的商家
-- ----------------------------------------------------------------------------
-- sys_shop_menu 决定「这个商家能用哪些菜单」。少了这一步，角色上勾了也不算数：
-- selectPermissionsByMember 里 join 了 sys_shop_menu。
INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT `shop_code`, 'fn5107', '0', 'wczladmin', sysdate()
  FROM `sys_shop_menu` WHERE `fun_code` = 'fn5103';

INSERT IGNORE INTO `sys_shop_menu`
  (`shop_code`, `fun_code`, `status`, `create_by`, `create_time`)
SELECT `shop_code`, 'fn8017', '0', 'wczladmin', sysdate()
  FROM `sys_shop_menu` WHERE `fun_code` = 'fn8010';

-- ============================================================
-- 3. 自查
-- ============================================================

-- 3.1 期望 0 行：不该再有旧状态值
DROP PROCEDURE IF EXISTS sdsorder.check_shipbatch_picking_flow;
DELIMITER $$
CREATE PROCEDURE sdsorder.check_shipbatch_picking_flow()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur_batch CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_legacy;
  CREATE TEMPORARY TABLE tmp_shipbatch_legacy (
    table_name varchar(64), shipcode varchar(50), status varchar(20)
  );
  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_missing_col;
  CREATE TEMPORARY TABLE tmp_shipbatch_missing_col (
    table_name varchar(64), column_name varchar(64)
  );
  DROP TEMPORARY TABLE IF EXISTS tmp_shipbatch_stats;
  CREATE TEMPORARY TABLE tmp_shipbatch_stats (
    table_name varchar(64), status varchar(20), rows_kept bigint
  );

  OPEN cur_batch;
  batch_loop: LOOP
    FETCH cur_batch INTO tname;
    IF done = 1 THEN LEAVE batch_loop; END IF;

    SET @s = CONCAT('INSERT INTO tmp_shipbatch_legacy ',
      'SELECT ''', tname, ''', shipcode, status FROM sdsorder.`', tname,
      '` WHERE status IN (''已打印单据'', ''已发货'')');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    INSERT INTO tmp_shipbatch_missing_col
    SELECT tname, c.name FROM (
      SELECT 'pickphotos' AS name UNION ALL SELECT 'pickuser' UNION ALL SELECT 'picktime'
      UNION ALL SELECT 'logisticsuser' UNION ALL SELECT 'logisticstime') c
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns ic
                        WHERE ic.table_schema = 'sdsorder' AND ic.table_name = tname
                          AND ic.column_name = c.name);

    SET @s = CONCAT('INSERT INTO tmp_shipbatch_stats ',
      'SELECT ''', tname, ''', status, COUNT(*) FROM sdsorder.`', tname, '` GROUP BY status');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur_batch;

  -- 期望 0 行：不该再有旧状态值
  SELECT * FROM tmp_shipbatch_legacy;
  -- 期望 0 行：五列一张表都不该缺
  SELECT * FROM tmp_shipbatch_missing_col;
  -- 迁移后各状态的行数分布
  SELECT * FROM tmp_shipbatch_stats ORDER BY table_name, status;

  DROP TEMPORARY TABLE tmp_shipbatch_legacy;
  DROP TEMPORARY TABLE tmp_shipbatch_missing_col;
  DROP TEMPORARY TABLE tmp_shipbatch_stats;
END$$
DELIMITER ;

CALL sdsorder.check_shipbatch_picking_flow();
DROP PROCEDURE sdsorder.check_shipbatch_picking_flow;

-- 3.2 权限点现状
USE `sdssys`;
SELECT `fun_code`, `parent_id`, `menu_name`, `menu_type`, `perms`
  FROM `sys_menu`
 WHERE `fun_code` IN ('fn5103','fn5104','fn5105','fn5106','fn5107',
                      'fn8010','fn8014','fn8015','fn8017')
 ORDER BY `fun_code`;

-- 3.3 哪些角色拿到了新权限。期望：fn5107 的角色集合 = fn5106 的，fn8017 = fn8014 的
SELECT `fun_code`, COUNT(*) AS roles
  FROM `sys_role_menu`
 WHERE `fun_code` IN ('fn5106','fn5107','fn8014','fn8017')
 GROUP BY `fun_code` ORDER BY `fun_code`;

-- ============================================================
-- 回滚
-- ============================================================
-- 只在确认没有新流程产生的数据时可用：新增的三格（已打印备货单之后到已打印快递单号
-- 之间）在旧状态机里没有对应的格子，硬退会把「货还没备好」的批次说成「已发货」。
-- 逐月表执行，下面以 202609 为例。
--
-- UPDATE sdsorder.u_f_shipbatch_202609 SET status = '已打印单据' WHERE status = '已打印备货单';
-- UPDATE sdsorder.u_f_shipbatch_202609 SET status = '已发货'     WHERE status = '已打印快递单号';
-- -- ↓ 这两格旧流程没有，只能按「货还在仓库、面单没录」就近退到已打印单据
-- UPDATE sdsorder.u_f_shipbatch_202609 SET status = '已打印单据' WHERE status IN ('备货完成', '已录入快递单号');
--
-- 五个新列留着不删：旧代码不查它们，留着不碍事，删了再回滚回来数据就没了。
--
-- 权限回滚：
-- UPDATE sdssys.sys_menu SET menu_name = '仓库发货确认备货', perms = 'order:shipbatch:confirm',
--        remark = '仓库发货确认备货' WHERE fun_code = 'fn5105';
-- DELETE FROM sdssys.sys_role_menu WHERE fun_code IN ('fn5107', 'fn8017');
-- DELETE FROM sdssys.sys_shop_menu WHERE fun_code IN ('fn5107', 'fn8017');
-- DELETE FROM sdssys.sys_menu      WHERE fun_code IN ('fn5107', 'fn8017');
