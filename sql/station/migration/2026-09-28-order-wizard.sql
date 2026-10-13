-- =====================================================================
-- 引导式下单（下单向导）：向导定义表 + 订单上一列选配快照
-- 2026-09-28
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
-- ⚠ 先跑这份、再换 jar：新版 SiteOrderMapper 的详情查询带 wizardjson，列不在的话
--    管理台订单详情、买家中心订单详情都会 500。三个站同一套代码，哪台换 jar 哪台先跑。
--
-- 设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md
--
--   u_s_orderwizard      一个站一行：前台开关 + 向导定义（分组 → 字段 → 选项，JSON）
--   u_s_order.wizardjson 从向导下的单，提交那一刻客户选了什么（带文字的快照）。
--                        ⚠ 存快照不存编码：向导以后会改、选项会删，订单必须留下
--                        「当时看到并选了什么」。普通购物车下的单这一列为 NULL。
--
-- 本脚本只建结构、不写内容：表是空的 = 向导没开，前台入口不出现，和上线前一模一样。
-- 预置模板在管理台「引导下单」里点「载入预置模板」写进来（模板在 jar 里，
-- station-console/src/main/resources/order-wizard/massager-template.json）。
--
-- 可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `u_s_orderwizard` (
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码。一个部署一个站，这张表最多一行',
  `enabled`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '前台开关。0 = 顶栏入口不出现、/configure 显示未开放、带向导的下单被拒',
  `schemajson` mediumtext   NULL COMMENT '向导定义 JSON：{version,title,lead,groups:[{code,title,sub,showIf,fields:[{code,type,options:[…]}]}]}。结构见设计文档；后端 OrderWizardService.validateSchema 是唯一的格式校验',
  `updateuser` varchar(50)  NULL,
  `updatetime` datetime     NULL,
  PRIMARY KEY (`sitecode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引导式下单的向导定义';

DROP PROCEDURE IF EXISTS `__add_order_wizardjson`;
DELIMITER $$
CREATE PROCEDURE `__add_order_wizardjson`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_order'
                   AND column_name = 'wizardjson') THEN
    ALTER TABLE `u_s_order`
      ADD COLUMN `wizardjson` mediumtext NULL
      COMMENT '引导式下单的选配快照：[{group,field,value,code,raw,groupZh,fieldZh,valueZh}]。普通购物车下的单为 NULL'
      AFTER `buyernote`;
  END IF;
END$$
DELIMITER ;

CALL `__add_order_wizardjson`();
DROP PROCEDURE `__add_order_wizardjson`;

-- 核对
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_orderwizard') AS `向导表_应为1`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_order' AND column_name = 'wizardjson') AS `订单快照列_应为1`;

-- 登记台账（有台账表的库才登记）
SET @has_ledger := (SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name = 'u_s_schema_migration');
SET @ledger_sql := IF(@has_ledger > 0,
  'INSERT IGNORE INTO u_s_schema_migration (filename, appliedat, appliedby, source, note) VALUES (''2026-09-28-order-wizard.sql'', NOW(), CURRENT_USER(), ''执行'', ''引导式下单：u_s_orderwizard + u_s_order.wizardjson'')',
  'SELECT 1');
PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回滚：
-- ALTER TABLE `u_s_order` DROP COLUMN `wizardjson`;
-- DROP TABLE `u_s_orderwizard`;
