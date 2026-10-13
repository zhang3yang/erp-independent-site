-- =====================================================================
-- 前台买家登录：给 u_s_buyer 加凭据列
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台跑的是 sql/migration/ 下面那些。
--
-- ⚠ 前置：先跑 2026-09-04-order-and-buyer.sql —— u_s_buyer 是那个脚本建的。
--   建库脚本 01_station_schema.sql 后来也补了这张表，但**更早建起来的库里没有**，
--   顺序反了这里直接报 Table 'sdsstation.u_s_buyer' doesn't exist（本机就撞过一次）。
--
-- ---------------------------------------------------------------------
-- 这张表是买家的，不是管理台的。两张表两拨人，别搞混
-- ---------------------------------------------------------------------
--
-- 这一层最容易看错，所以摆在最前面：
--
--   | 表          | 谁                    | 怎么进来                          | 存密码吗 |
--   | ----------- | --------------------- | --------------------------------- | -------- |
--   | u_s_user    | 管理台操作员（自己人）| 只能从 ERP 点「进入管理」免密进    | **不存** |
--   | u_s_buyer   | 前台买家（客户）      | 在前台自助注册、邮箱验证、登录     | 存       |
--
-- 两张表名字都带 user/buyer，功能完全不同，**不要合并**：
-- u_s_user.rolecode 是管理台权限的来源，买家一旦和操作员共用一张表，
-- 某个查询漏一个 where 条件就是「买家拿到了管理台权限」。
-- u_s_user 那边不存密码是刻意的（docs/独立站与ERP的边界.md §6d），
-- 这个脚本加密码列**不推翻那条**——它加的是另一拨人的凭据。
--
-- ---------------------------------------------------------------------
-- 为什么凭据加在这张已有的表上，而不是新建一张
-- ---------------------------------------------------------------------
--
-- 因为**买家的身份本来就是邮箱**，而这张表已经按邮箱唯一了
-- （uk_buyer_email，见建库脚本里那条「按人名去重会把两个同名的人合成一个」）。
--
-- 直接的好处：一个先发过询盘、后来才注册的人，注册时认领的就是他自己那一行——
-- 登录进去立刻能看到自己之前的询盘和订单。凭据另起一张表的话，
-- 「档案」和「账号」就成了两个按邮箱对齐的东西，而对齐这件事迟早会错一次
-- （改邮箱、合并重复档案、删档案忘删账号）。
--
-- ---------------------------------------------------------------------
-- passwordhash 为 NULL 是常态，不是数据缺失
-- ---------------------------------------------------------------------
--
-- 这张表里绝大多数行是「长出来的」——谁发询盘、谁下单，就按邮箱落一行
-- （SiteBuyerService.resolveByEmail）。那些人从没注册过，也可能永远不注册。
--
--   passwordhash IS NULL  = 有档案，没账号（运营能看到他，他自己登不进来）
--   passwordhash 有值      = 他注册过
--
-- 所以**不要给这一列加 NOT NULL**，也不要写一个「补一个默认密码」的回填脚本。
--
-- ---------------------------------------------------------------------
-- 邮箱验证和重置密码的令牌不在这张表里
-- ---------------------------------------------------------------------
--
-- 它们进 Redis（station:buyer:verify:* / station:buyer:reset:*），带 TTL、一次性消费，
-- 照的是跳转令牌 jti 防重放那一套（JumpTokenVerifier.consumeOnce）。
--
-- 不建表的理由：令牌是**短命**的（验证 24 小时、重置 1 小时），存在表里就要自己写
-- 一个清理任务，而忘了清理的表会一直长。Redis 的 TTL 是免费的。
-- 代价是 Redis 被清空时未点击的链接会失效——那时候重新发一封就行。
--
-- 可重复执行：加列前用 information_schema 判一次（MySQL 没有 ADD COLUMN IF NOT EXISTS）。
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- =====================================================================
-- 一、加列
-- =====================================================================

DROP PROCEDURE IF EXISTS `__add_buyer_login_columns`;

DELIMITER $$
CREATE PROCEDURE `__add_buyer_login_columns`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'passwordhash') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `passwordhash` varchar(200) DEFAULT NULL
      COMMENT 'BCrypt 密文。⚠ 绝不存明文。为 NULL = 有档案没账号（发过询盘但没注册），是常态'
      AFTER `email`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'emailverified') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `emailverified` tinyint(1) NOT NULL DEFAULT 0
      COMMENT '邮箱验证过没有。0 时不许登录——注册时那个邮箱可能压根不是他的'
      AFTER `passwordhash`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'verifytime') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `verifytime` datetime DEFAULT NULL
      COMMENT '邮箱验证通过的时间'
      AFTER `emailverified`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'status') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `status` varchar(20) NOT NULL DEFAULT '正常'
      COMMENT '正常/停用。停用后登不进来，但档案、询盘、订单都留着——运营还要看'
      AFTER `verifytime`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'lastlogintime') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `lastlogintime` datetime DEFAULT NULL
      COMMENT '最后登录时间。⚠ 和 lastactive 不是一回事：那一列是最近一次询盘或下单'
      AFTER `ordercount`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'lastloginip') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `lastloginip` varchar(64) DEFAULT NULL
      COMMENT '最后登录 IP。只用于排查，不参与任何判定（这个头是客户端可伪造的）'
      AFTER `lastlogintime`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_buyer'
                   AND column_name = 'logincount') THEN
    ALTER TABLE `u_s_buyer`
      ADD COLUMN `logincount` int NOT NULL DEFAULT 0
      COMMENT '累计登录次数。只在登录成功时加——失败也加的话，它就变成了「有人在试密码」的计数'
      AFTER `lastloginip`;
  END IF;
END$$
DELIMITER ;

CALL `__add_buyer_login_columns`();
DROP PROCEDURE `__add_buyer_login_columns`;


-- =====================================================================
-- 二、验一眼
-- =====================================================================
--
-- 七列都在、而且 passwordhash 全是 NULL（谁都还没注册）才算跑对了。

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_buyer'
      AND column_name IN ('passwordhash','emailverified','verifytime','status',
                          'lastlogintime','lastloginip','logincount'))         AS `新增列数（应为7）`,
  (SELECT COUNT(*) FROM `u_s_buyer`)                                            AS `档案总数`,
  (SELECT COUNT(*) FROM `u_s_buyer` WHERE `passwordhash` IS NOT NULL)           AS `已注册账号数`;
