-- =====================================================================
-- 在线咨询：买家 ↔ 运营的对话
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台跑的是 sql/migration/ 下面那些。
--
-- 设计文档：docs/设计稿/2026-09-04-在线咨询.md
--
-- ---------------------------------------------------------------------
-- 一个买家一条线程，运营在一个面板里处理所有线程
-- ---------------------------------------------------------------------
--
--   u_s_chat         一个访客一行（线程）
--   u_s_chatmessage  这条线程里的每一句
--
-- 匿名访客靠 CHAT_VISITOR 这个 HttpOnly Cookie 认线程。**不挡登录**：
-- 这个站的询盘和下单都不要求账号（useBuyer.ts：「挡一道登录会掉单，外贸站尤其如此」），
-- 聊天没有理由比它们更严。留了邮箱就走 SiteBuyerService.resolveByEmail 挂到 u_s_buyer 上。
--
-- ---------------------------------------------------------------------
-- ⚠ 未读数是**现算**的，所以这里没有 unreadcount 那样的列
-- ---------------------------------------------------------------------
--
-- 只存两个**已读游标**：consolereadid / buyerreadid = 那一侧读到的最大 message id。
-- 未读数由
--
--     select count(1) from u_s_chatmessage
--      where chatid = ? and id > ? and sender = '买家'
--
-- 现算。理由和 u_s_buyer 的 inquirycount 那条一模一样（见 SiteBuyerMapper.xml 文件头）：
-- 累加的计数会漂——消息删除、会话合并、并发写，每一种都要记得去减，漏一次就永远错下去。
-- 游标只增不减（写的时候套 greatest），两个标签页同时标已读也不会把它退回去。
--
-- ---------------------------------------------------------------------
-- 为什么消息表上没有 sitecode
-- ---------------------------------------------------------------------
--
-- 它由 chatid 决定。冗余一份就多一个会不一致的地方，而这张表永远是从
-- 某一条 chat 出发去查的，没有「跨会话按站点捞消息」这种查询。
--
-- 可重复执行：只有 CREATE TABLE IF NOT EXISTS、INSERT IGNORE，和一条带 NOT LIKE 的 UPDATE。
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- =====================================================================
-- 一、会话线程
-- =====================================================================

CREATE TABLE IF NOT EXISTS `u_s_chat` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`      varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`       varchar(50)  NOT NULL COMMENT '来自哪个站点',
  `chatcode`       varchar(50)  NOT NULL COMMENT '会话编码，LT + Base10 流水',

  -- ⚠ 这一列是匿名会话的**唯一凭据**，拿到就能读走整条对话。
  --   它只在 HttpOnly Cookie 里流动，绝不进 URL——query 参数会原样进 nginx 的 access log。
  `visitortoken`   varchar(64)  NOT NULL COMMENT '访客令牌，32 字节 SecureRandom 的 base64url。就是 CHAT_VISITOR Cookie 里那个值',

  -- 下面四列在访客留邮箱或登录之前全是 NULL，那是常态不是缺失
  `buyercode`      varchar(50)  DEFAULT NULL COMMENT '挂上的客户档案，指向 u_s_buyer.buyercode。留了邮箱才有',
  `email`          varchar(150) DEFAULT NULL COMMENT '访客留的邮箱（选填）。留了才能邮件回访',
  `name`           varchar(100) DEFAULT NULL COMMENT '访客填的称呼（选填）',
  `lang`           varchar(10)  DEFAULT NULL COMMENT '访客用哪种语言开的口。前台是 8 语言站，运营得知道用哪国话回',

  `pageurl`        varchar(500) DEFAULT NULL COMMENT '他在哪个页面开的口。运营一眼看出他在看哪个商品',
  `clientip`       varchar(64)  DEFAULT NULL COMMENT '排查用，和 u_s_inquiry 一致',
  `useragent`      varchar(500) DEFAULT NULL COMMENT '同上',

  `lastmessage`    varchar(500) DEFAULT NULL COMMENT '最后一条的摘要。会话列表直接显示它，不用 join 消息表',
  `lastmessagetime` datetime    DEFAULT NULL COMMENT '最后一条的时间。列表默认按它倒排',

  -- 见文件头：这两个是游标，不是计数
  `consolereadid`  bigint       NOT NULL DEFAULT 0 COMMENT '运营读到的最大 message id。未读数靠它现算',
  `buyerreadid`    bigint       NOT NULL DEFAULT 0 COMMENT '买家读到的最大 message id。同上',

  `createtime`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '第一条消息发出的时间',
  `updatetime`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_code` (`shop_code`,`chatcode`),
  -- 令牌全局唯一：认线程认的就是它，撞一次就是两个人共用一条对话
  UNIQUE KEY `uk_chat_visitor` (`visitortoken`),
  KEY `idx_chat_active` (`shop_code`,`sitecode`,`lastmessagetime`),
  KEY `idx_chat_buyer` (`buyercode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线咨询会话。一个访客一行';


-- =====================================================================
-- 二、消息
-- =====================================================================

CREATE TABLE IF NOT EXISTS `u_s_chatmessage` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键。⚠ 它同时是已读游标的取值，所以不能改成 UUID 之类',
  `shop_code`   varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `chatid`      bigint       NOT NULL COMMENT '属于哪条会话，指向 u_s_chat.id',

  -- 中文常量，和 SiteOrder.STATUS_* 同风格：它要直接显示在管理台的面板里
  `sender`      varchar(20)  NOT NULL COMMENT '买家 / 运营',
  `senderuser`  varchar(50)  DEFAULT NULL COMMENT '哪个操作员回的（u_s_user.username）。买家发的为 NULL',

  `content`     varchar(2000) NOT NULL COMMENT '纯文本。⚠ 存原文不做 HTML 过滤——过滤会把买家问的「<M8 螺丝」吃掉。两侧都用文本插值渲染，不解释 HTML',
  `createtime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发出时间',

  PRIMARY KEY (`id`),
  -- 增量拉取（where chatid = ? and id > ?）和未读现算走的都是这条
  KEY `idx_msg_chat` (`chatid`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线咨询消息';


-- =====================================================================
-- 三、发号器补一行
-- =====================================================================
-- 前缀 LT（聊天）。和 XP / DD / KH / SIMG / SVID 是同一套。

INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES ('chat', 1);


-- =====================================================================
-- 四、给运营角色补权限
-- =====================================================================
--
-- admin 的 perms 是 '*'，不用动。operator 那一行是逐条列出来的，不补就是
-- 「运营点开聊天面板，接口全 403」——而 AuthInterceptor 的 403 不会踢人下线，
-- 表现只是面板一直空着，很容易被当成前端 bug。
--
-- NOT LIKE 那一句让它可以重复执行。

UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`, ',site:chat:list,site:chat:reply')
 WHERE `rolecode` = 'operator'
   AND `perms` NOT LIKE '%site:chat:list%';


-- =====================================================================
-- 五、验一眼
-- =====================================================================
--
-- 两张表在、发号器有 chat 那一行、operator 的权限串里有 site:chat:reply，才算跑对了。

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_chat')            AS `u_s_chat（应为1）`,
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_chatmessage')     AS `u_s_chatmessage（应为1）`,
  (SELECT COUNT(*) FROM `u_s_sequence` WHERE `name` = 'chat')               AS `发号器chat（应为1）`,
  (SELECT COUNT(*) FROM `u_s_role`
    WHERE `rolecode` = 'operator' AND `perms` LIKE '%site:chat:reply%')     AS `运营有回复权（应为1）`;
