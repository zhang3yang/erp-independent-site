-- =====================================================================
-- ⚠⚠ 本脚本已作废（superseded，2026-09-06）。已经跑过的库不用回滚，别再新跑。
-- =====================================================================
--
-- 它设想的是「结账页继续走询盘接口，但把数据结构化」。**那条路没走成**：
-- 脚本落了库、代码一处没接（SiteInquiry.java 和 SiteInquiryMapper.xml 一个新列
-- 都没用，u_s_inquiry_item 在整个后端零引用），而与此同时另一条路已经写完了——
-- POST /openapi/site/order → SiteOrderService → u_s_order + u_s_orderitem，
-- 下单/列表/详情/状态流转/买家自查/管理台订单管理全都有。
--
-- 2026-09-06 决定由订单接口收口。处置：
--   · u_s_inquiry_item  →  删掉（零引用、空表）
--   · 那 13 个列          →  **保留**，全部可空且为 NULL，留着成本是零；
--                            删列不可逆，且要在每个已部署的站上各做一次 ALTER。
--                            基线 01_station_schema.sql 里也照建，标注为「RFQ 留位置」，
--                            让新站和老站结构逐列一致。
--
-- 执行脚本和完整理由：migration/2026-09-06-drop-inquiry-item-and-orphans.sql
--
-- 下面的原文保留不动——它把「前台正在丢数据」这个问题记录得很清楚，
-- 那个问题是真的，只是解法换了。
--
-- =====================================================================
-- 以下为 2026-09-04 的原文
-- =====================================================================
--
-- u_s_inquiry：让它装得下一个 RFQ（购物车下单请求），不再靠 message 塞文本
-- =====================================================================
--
-- 依据不是文档，是前台代码现在的行为。
--
-- =====================================================================
-- 问题：前台正在丢数据
-- =====================================================================
--
-- `station-site` 的结账页（app/pages/checkout/index.vue）走的是询盘接口
-- （app/lib/shop.ts 文件头："买家填收货信息和需求 → 提交 → 走现有的询盘接口"），
-- 但一个 RFQ 要带的东西 u_s_inquiry 一列都装不下：
--
--     收货地址（6 个字段）      运输偏好 sea/air/express/agent
--     付款偏好 tt/lc/paypal/other  N 行商品明细（编码/标题/单价/数量/单位）
--     买家备注                  提交语言
--
-- 所以前台的 buildMessage()（checkout/index.vue:355）把这一整套**拼成纯文本**
-- 塞进 `message`。而后端 `message` 上限 2000 字且是**直接截断**
-- （StationOpenServiceImpl 里 `require(..., 2000)`），于是前台不得不自己算预算：
--
--     const fixed = [...head, 'ITEMS', ...foot, ...tail].join('\n').length
--     const budget = MESSAGE_LIMIT - fixed
--     for (const line of lines) {
--       if (used + line.length + 1 > budget) {
--         kept.push(`  ... ${lines.length - kept.length} more item(s), see request ${ref}`)
--         break                            // ← 明细行从这里开始被丢掉
--       }
--       ...
--     }
--
-- 两个后果：
--
--  1. **明细会丢。** 清单长一点，尾巴上的商品行就进不了库、也进不了那封通知邮件。
--     商家不知道买家还问了什么，买家以为问了。
--
--  2. **那句 "see request RFQ-2026-1043" 指向一个不存在的地方。** 这个单号是前台
--     生成的（nextOrderRef），只存在买家浏览器的 localStorage 里（useOrders）。
--     服务端从来没收到过它。商家照着这句话去查，什么都查不到。
--
-- 前台那段代码写得很克制——注释里说明了「顺序上把地址和条款放在明细之前：万一要截，
-- 被截掉的是明细的尾巴，不是收货地址」。那是在没法改后端的前提下能做的最好安排。
-- 但取舍本身不该存在：这些是结构化数据，本来就该有列。
--
-- =====================================================================
-- 改法
-- =====================================================================
--
-- 一张主表加 13 列（都可空，不影响现有单品询盘），加一张明细子表。
-- `message` 保留原样、原语义：**买家自己写的那段话**。RFQ 的结构化部分不再往里拼。
--
-- 刻意**没有**加的两列，因为算得出来：
--   · subtotal    = SUM(price*qty) FROM u_s_inquiry_item WHERE price > 0
--   · inquireonly = COUNT(*)       FROM u_s_inquiry_item WHERE price = 0
-- 存下来就等于给自己留两个能和明细对不上的字段。
--
-- =====================================================================
-- ⚠ 跑完之后它是「空的」，要配代码才生效
-- =====================================================================
--
-- 这份迁移只开出位置。真正让它有用还要三处改动，不做的话新列恒为 NULL、
-- 明细表恒为空，等于白加：
--
--   1. 后端 OpenApiController / StationOpenService 的 inquiry 入参加上这些字段
--      （含明细数组），并保留对旧入参的兼容——单品询盘不带它们
--   2. 前台 checkout/index.vue 的 submit()：改成把结构化字段和明细直接发过去，
--      **删掉 buildMessage() 里那段预算截断**，message 只放 form.note
--   3. 管理台的询盘详情页：RFQ 类型要能展示明细和收货地址
--
-- 在这三处落地之前，前台的行为不变（照旧拼文本、照旧会截），只是不再丢得那么难看——
-- 因为库里已经有地方放了。所以顺序上建议：**这份迁移和上面第 1、2 步一起上**。
--
-- =====================================================================

USE `sdsstation`;


-- =====================================================================
-- 一、u_s_inquiry 加列
-- =====================================================================
--
-- MySQL 没有 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的扩展），照
-- 2026-09-04-sitegoods-own-profile.sql 的写法用 information_schema 判一下再拼，
-- 让脚本可以重复执行——部署脚本被跑两遍是常态。

DROP PROCEDURE IF EXISTS `__add_inquiry_rfq_columns`;

DELIMITER $$
CREATE PROCEDURE `__add_inquiry_rfq_columns`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 询盘类型。区分「详情页问一个商品」和「购物车提交的下单请求」，
  -- 管理台列表要按它分开显示（RFQ 要看明细，单品询盘不用）
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='inquirytype') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `inquirytype` varchar(20) NOT NULL DEFAULT 'product'
      COMMENT '类型：product 单品询盘（详情页 InquiryForm）/ rfq 下单请求（结账页）'
      AFTER `sitecode`;
  END IF;

  -- ⚠ 这一列是修那个「查不到的单号」的。前台 nextOrderRef 生成 RFQ-2026-1043，
  --   正文里引用它，但它只在买家的 localStorage 里。收下来，商家才查得到
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='inquiryref') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `inquiryref` varchar(50) DEFAULT NULL
      COMMENT '前台生成的对外单号，如 RFQ-2026-1043。买家手里就是这个号，客服靠它对单'
      AFTER `inquirycode`;
  END IF;

  -- 提交语言。七语言站，回复客户该用哪种语言不该靠猜
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='lang') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `lang` varchar(20) DEFAULT NULL
      COMMENT '提交时的站点语言（URL 前缀那个）。决定回复用哪种语言写'
      AFTER `useragent`;
  END IF;

  -- 币种快照。站点改币种后，历史询盘里的报价不该被重新解释
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='currency') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `currency` varchar(10) DEFAULT NULL
      COMMENT '提交时的站点币种快照。明细里的单价按它解释'
      AFTER `lang`;
  END IF;

  -- 运输 / 付款偏好。注意是**偏好**不是已定的方式：外贸运费按目的港、体积重、
  -- 当期舱位算，站上给不出真实运费（前台 SHIPPING_PREFS 注释就是这个意思）
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='shippingpref') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `shippingpref` varchar(20) DEFAULT NULL
      COMMENT '运输偏好：sea 海运 / air 空运 / express 快递 / agent 买家自己的货代。只是倾向，真实运费在报价单里'
      AFTER `currency`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='paymentpref') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `paymentpref` varchar(20) DEFAULT NULL
      COMMENT '付款偏好：tt 电汇 / lc 信用证 / paypal / other 面议。站上不收款'
      AFTER `shippingpref`;
  END IF;

  -- 收货地址六列。为什么不合成一个 JSON 或一个长串：
  --   · shipcountry 要单独查（「询盘来自哪些国家」对外贸站是核心报表），下面给它建了索引
  --   · 其余五列拼起来存的话，将来做形式发票 / 运单要再解析一次，
  --     而前台本来就是六个独立表单字段——在库这一层丢掉结构没有好处
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='shipcountry') THEN
    ALTER TABLE `u_s_inquiry`
      ADD COLUMN `shipcountry` varchar(100) DEFAULT NULL COMMENT '收货国家/地区。⚠ 有索引，按国家统计询盘来源用' AFTER `paymentpref`,
      ADD COLUMN `shipline1`   varchar(200) DEFAULT NULL COMMENT '收货地址第一行' AFTER `shipcountry`,
      ADD COLUMN `shipline2`   varchar(200) DEFAULT NULL COMMENT '收货地址第二行' AFTER `shipline1`,
      ADD COLUMN `shipcity`    varchar(100) DEFAULT NULL COMMENT '城市' AFTER `shipline2`,
      ADD COLUMN `shipregion`  varchar(100) DEFAULT NULL COMMENT '州/省。很多国家没有，可空' AFTER `shipcity`,
      ADD COLUMN `shippostal`  varchar(30)  DEFAULT NULL COMMENT '邮编' AFTER `shipregion`;
  END IF;

  -- 买家备注。和 message 分开：message 是「买家自己写的那段话」，
  -- 现在被 buildMessage 拼进了地址和明细。改造后 message 回归原义，note 留给结账页那个备注框
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND column_name='note') THEN
    ALTER TABLE `u_s_inquiry` ADD COLUMN `note` varchar(2000) DEFAULT NULL
      COMMENT '买家补充说明（结账页的备注框）。message 是单品询盘的留言，两者不混'
      AFTER `message`;
  END IF;
END$$
DELIMITER ;

CALL `__add_inquiry_rfq_columns`();
DROP PROCEDURE `__add_inquiry_rfq_columns`;


-- 索引：按国家统计询盘来源；按对外单号对单
DROP PROCEDURE IF EXISTS `__add_inquiry_rfq_indexes`;

DELIMITER $$
CREATE PROCEDURE `__add_inquiry_rfq_indexes`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND index_name='idx_inquiry_country') THEN
    ALTER TABLE `u_s_inquiry` ADD KEY `idx_inquiry_country` (`shop_code`,`shipcountry`,`createtime`)
      COMMENT '按收货国家统计询盘来源';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
                 AND table_name='u_s_inquiry' AND index_name='idx_inquiry_ref') THEN
    ALTER TABLE `u_s_inquiry` ADD KEY `idx_inquiry_ref` (`inquiryref`)
      COMMENT '客服拿买家手里的 RFQ 号反查';
  END IF;
END$$
DELIMITER ;

CALL `__add_inquiry_rfq_indexes`();
DROP PROCEDURE `__add_inquiry_rfq_indexes`;


-- =====================================================================
-- 二、询盘明细
-- =====================================================================
--
-- 每一列都是**提交当时的快照**，不 join 商品表：
-- 商品改名、改价、下架，历史询盘必须还显示买家当时看到的东西——
-- 客服要照着它报价，看到的必须和买家看到的一致。
-- goodscode 只是弱引用，用来反查「这个商品被问过多少次」。
--
-- 单品询盘（inquirytype='product'）**不产生明细行**：那种询盘的商品信息在主表的
-- goodscode / producttitle 上，加一行明细只是重复。
--
-- 没有 shop_code：它在搬过来的表上是遗留列（boundary §5.3），新表不带。
-- 没有 sitecode：父表有，子表按 inquiryid 定位就够。
--
CREATE TABLE IF NOT EXISTS `u_s_inquiry_item` (
  `id`        bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `inquiryid` bigint        NOT NULL COMMENT '所属询盘，指向 u_s_inquiry.id',
  `goodscode` varchar(50)   DEFAULT NULL COMMENT '商品编码。弱引用，仅供反查；商品删了不影响本行展示',
  `title`     varchar(300)  NOT NULL COMMENT '商品标题快照。⚠ 存的是买家当时那个语言看到的标题，不是内部品名',
  `spec`      varchar(200)  DEFAULT NULL COMMENT '规格快照',
  `price`     decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '加入时单价。⚠ 0 = 价格面议，不是免费——外贸站大量商品是面议，算小计时要跳过',
  `qty`       decimal(18,4) NOT NULL DEFAULT '1.0000' COMMENT '数量。用 decimal 不用 int：起订量常带小数（0.5 吨）',
  `unit`      varchar(20)   DEFAULT NULL COMMENT '单位快照',
  `imgurl`    varchar(500)  DEFAULT NULL COMMENT '缩略图快照，管理台看明细时有图好认',
  `sortnum`   int           NOT NULL DEFAULT '0' COMMENT '明细顺序，照买家购物车里的顺序',
  PRIMARY KEY (`id`),
  KEY `idx_inqitem_inquiry` (`inquiryid`,`sortnum`) COMMENT '取一个询盘的明细',
  KEY `idx_inqitem_goods`   (`goodscode`) COMMENT '反查某商品被问过多少次——外贸站最想知道的就是这个'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='询盘明细（RFQ 的商品清单，全部快照）';


-- =====================================================================
-- 三、验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_inquiry'
      AND column_name IN ('inquirytype','inquiryref','lang','currency','shippingpref',
                          'paymentpref','shipcountry','shipline1','shipline2',
                          'shipcity','shipregion','shippostal','note'))          AS `新增列数_应为13`,
  -- COUNT(DISTINCT index_name)：statistics 是按索引**列**计行的，
  -- 复合索引会算好几行，直接 COUNT(*) 出来的数字看着莫名其妙
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'u_s_inquiry'
      AND index_name IN ('idx_inquiry_country','idx_inquiry_ref'))               AS `新增索引数_应为2`,
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_inquiry_item')         AS `明细表_应为1`;
