-- =====================================================================
-- 买家 3D 定制方案：前台 /customize 页上买家自己贴 logo、换开机画面，保存成一个方案链接
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
-- ⚠ 必须先于新 jar：CustomPlanMapper 查这张表，表不在 = 定制页保存 500、管理台列表 500。
--
-- 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md
--
-- planid 是 16 位随机 base62，**不走发号器**（防挨个猜），所以没有 u_s_sequence 那一行。
--
-- 可重复执行：建表 IF NOT EXISTS，权限串 NOT LIKE。本脚本不写迁移台账，跑完手工补一条
-- （⚠ 台账 source 列只有 20 字符，填「执行」别填路径）。


CREATE TABLE IF NOT EXISTS `u_s_custom_plan` (
  `planid`     varchar(16)   NOT NULL COMMENT '16 位随机 base62，方案链接里那一段',
  `shop_code`  varchar(50)   DEFAULT NULL COMMENT '同其他 u_s_* 表，新代码不读不筛',
  `sitecode`   varchar(50)   DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `goodscode`  varchar(50)   NOT NULL COMMENT '定制的是哪个商品',
  `model3d`    varchar(500)  NOT NULL COMMENT '保存时商品模型地址的快照',
  `marks`      text          NOT NULL COMMENT '完整 v1 标注 JSON 快照（商家的屏幕/开机键 + 买家的 logo/开机画面）',
  `email`      varchar(200)  DEFAULT NULL COMMENT '买家选填；公开接口不下发',
  `note`       varchar(500)  DEFAULT NULL COMMENT '买家选填；公开接口不下发',
  `parentid`   varchar(16)   DEFAULT NULL COMMENT '从哪个方案「在此基础上修改」来的',
  `iphash`     char(64)      DEFAULT NULL COMMENT '提交 IP 的 SHA-256（加盐），不存明文',
  `createtime` datetime      NOT NULL,
  PRIMARY KEY (`planid`),
  KEY `idx_customplan_goods` (`goodscode`,`createtime`),
  KEY `idx_customplan_time` (`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='买家 3D 定制方案（保存后不可改）';


-- 运营能看定制方案（管理台只读列表）。admin 是 '*'，不用加
UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`, ',site:customplan:list')
 WHERE `rolecode` = 'operator'
   AND `perms` NOT LIKE '%site:customplan:list%';


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_custom_plan')      AS `方案表_应为1`,
  (SELECT COUNT(*) FROM `u_s_role`
    WHERE `rolecode` = 'operator' AND `perms` LIKE '%site:customplan:list%') AS `运营可看_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- DROP TABLE `u_s_custom_plan`;
-- UPDATE `u_s_role` SET `perms` = REPLACE(`perms`, ',site:customplan:list', '') WHERE `rolecode` = 'operator';
--
-- ⚠ 回滚前先把 jar 换回旧版。COS 上 custom/ 目录下的图片不会跟着删（CosClient 没有删除能力）。
