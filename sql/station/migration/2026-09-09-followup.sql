-- =====================================================================
-- 询盘跟进：u_s_followup（跟进记录）+ u_s_inquiry 两列
-- =====================================================================
--
-- 起因：docs/独立站与ERP的边界.md §11 里「营销管理」只有一个名字。
-- 落到这个站上，营销这件事最值钱的一块不是优惠券也不是群发邮件——
-- **是「询盘来了没人回」**。这是个 B2B 询盘站：前台不收款、价格 0 表示面议、
-- 成交靠人跟出来的报价单。漏掉一条询盘 = 漏掉一单，而漏掉是静悄悄的。
--
-- =====================================================================
-- ⚠ 先说这次**没有**新建什么，免得重复造
-- =====================================================================
--
-- 这三样库里已经有了，这个迁移一个字都不动它们：
--
--   · u_s_inquiry.status      待处理/已联系/已成交/无效  —— 跟进状态已经有
--   · u_s_inquiry.remark      内部备注                   —— 单条备注已经有
--   · u_s_buyer.tag           客户标签                   —— 客户分组已经有，
--                                                          管理台「客户管理」里能改
--
-- 所以真正缺的只有三样，就是下面这些：
--   1. **跟进记录**——remark 只有一条、会被覆盖，留不下「谁在什么时候说了什么」；
--   2. **下次跟进日期**——没有它就没法回答「今天该联系谁」；
--   3. **负责人**——handleuser 是「最后动过的人」，不是「这条归谁」。
--      两者不一样：一条没人动过的询盘，handleuser 是空的，
--      而「没人负责」和「没人动过」要分开看才知道是流程问题还是人手问题。
--
-- =====================================================================
-- 可重复执行：CREATE TABLE IF NOT EXISTS + 两条先查 information_schema 再加列。
-- ⚠ MySQL 没有 ADD COLUMN IF NOT EXISTS，所以用存储过程绕，跑第二遍不会报
--    "Duplicate column name"。新建的库不用跑——01_station_schema.sql 里已经带上了。
-- =====================================================================

USE `sdsstation`;


-- ---------------------------------------------------------------------
-- 跟进记录。一条询盘可以有很多条。
--
-- ⚠ 为什么不复用 u_s_inquiry.remark：那是**一个字段**，改一次盖一次。
--   而跟进最需要的恰恰是历史——「上次他说预算不够」「三周后再问一次」，
--   这些话的价值在于时间顺序，压成一段文本就没了。
--
-- ⚠ 这张表**只记人写的动作**，不记系统事件（状态变更、邮件送达）。
--   混在一起的话，真正的对话会被一堆自动流水淹掉，而这张表存在的意义
--   就是让人一眼看完这条线索的来龙去脉。系统事件要记的话另起一张。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_followup` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',

  -- ⚠ 现在只写 'inquiry' 一种。留这一列是因为订单（u_s_order）迟早也要跟进，
  --   而那时候只想加代码、不想再来一次 ALTER。
  --   **但今天不要往里写别的值**：没有任何代码读得懂它们。
  `targettype` varchar(20)  NOT NULL DEFAULT 'inquiry' COMMENT '跟进对象类型。⚠ 目前只有 inquiry 一种，留列是给订单跟进预备的，别提前写别的值',
  `targetcode` varchar(50)  NOT NULL COMMENT '对象编码。targettype=inquiry 时是 u_s_inquiry.inquirycode',

  `action`     varchar(20)  NOT NULL DEFAULT '记录' COMMENT '跟进方式：记录/邮件/电话/whatsapp/会面。只是给人看的分类，不驱动任何逻辑',
  `content`    varchar(2000) NOT NULL COMMENT '跟进内容。⚠ 纯文本，前台永不显示（这是内部记录），所以不过富文本白名单',

  `createuser` varchar(50)  DEFAULT NULL COMMENT '记录人，u_s_user.username',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间。⚠ 「多久没人管」算的就是它的最大值',

  PRIMARY KEY (`id`),
  KEY `idx_followup_target` (`sitecode`,`targettype`,`targetcode`,`createtime`) COMMENT '查一条询盘的跟进历史，以及算最后跟进时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跟进记录，只记人写的动作';


-- ---------------------------------------------------------------------
-- u_s_inquiry 加两列。用存储过程绕开「没有 ADD COLUMN IF NOT EXISTS」。
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_followup_cols`;
DELIMITER $$
CREATE PROCEDURE `add_followup_cols`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = DATABASE()
                    AND table_name = 'u_s_inquiry'
                    AND column_name = 'owner') THEN
    ALTER TABLE `u_s_inquiry`
      ADD COLUMN `owner` varchar(50) DEFAULT NULL
      COMMENT '负责人，u_s_user.username。⚠ 和 handleuser 不是一回事：那个是「最后动过的人」，这个是「这条归谁」。没人动过的询盘 handleuser 为空，而「没人负责」和「没人动过」要分开看';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = DATABASE()
                    AND table_name = 'u_s_inquiry'
                    AND column_name = 'nextfollow') THEN
    ALTER TABLE `u_s_inquiry`
      ADD COLUMN `nextfollow` date DEFAULT NULL
      COMMENT '下次跟进日期。⚠ 只到天，不到时分：销售定的是「下周三再问一次」，精确到分钟是假精度，还会让列表里全是 00:00:00';
  END IF;
END$$
DELIMITER ;

CALL `add_followup_cols`();
DROP PROCEDURE IF EXISTS `add_followup_cols`;


-- ---------------------------------------------------------------------
-- ⚠ 这个迁移**不加任何新权限**。
--
-- 跟进记录就是询盘数据，复用 site:inquiry:list / site:inquiry:edit。
-- 单开一套 site:followup:* 会造出一种没用的中间状态：能看跟进看板、
-- 却点不开背后的询盘。operator 本来就有这两个权限，所以运营开箱即用。
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- 登记台账
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
VALUES ('2026-09-09-followup.sql', NULL, '执行',
        '询盘跟进：u_s_followup 建表 + u_s_inquiry 加 owner/nextfollow 两列。不加权限，复用 site:inquiry:*');
