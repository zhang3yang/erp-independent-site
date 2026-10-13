-- =====================================================================
-- 站点订单 + 站点客户：建表
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台跑的是 sql/migration/ 下面那些。
--
-- ---------------------------------------------------------------------
-- 为什么现在建这两张，而 boundary §11 说「不建表」
-- ---------------------------------------------------------------------
--
-- §11 的原话是「不建表、不写 model、不留 TODO 接口」，理由是：
--
--     表结构定下来就会有人往上写代码，而这五个模块的需求现在只有名字。
--     空表比没表更容易引出错的实现。
--
-- 那句话对**博客 / 营销 / 站长工具**仍然成立，那三个到今天还是只有名字。
-- 但订单和客户这两个已经不是「只有名字」了：
--
--   · **订单**：前台的询价流程已经把模型定完了（station-site/app/lib/shop.ts：
--     Order / OrderItem / 状态词表 / 四段时间轴），而且 station-site/app/composables/
--     useOrders.ts 里明写着「等 station-console 有了订单接口，要换的是这里」。
--     现在那些单子只存在买家自己的浏览器里，商家只收到一封邮件——
--     下面这两张表就是把那封邮件变成台账。
--
--   · **客户**：u_s_inquiry.buyercode 那一列建库时就留好了，注释写着「本站客户编码，
--     ⚠ 不再指向 ERP 的 u_m_buyer」。§11 那张表里客户管理那行的说明也写着
--     「将来转的是这里」。
--
-- 也就是说：这两张表的结构不是现在拍的，是前台代码和已有的列注释**已经拍好了**的。
--
-- ---------------------------------------------------------------------
-- 三个刻意的设计取舍
-- ---------------------------------------------------------------------
--
-- 1. **时间轴不建第三张表。** shop.ts 里订单有一个 events[]（四个阶段各一条），
--    直觉是建 u_s_orderevent。没建：四个阶段是**固定**的，用四个时间列
--    （createtime / confirmtime / shiptime / finishtime）就能完整表达，
--    而一张事件表会立刻引出「要不要记操作人」「要不要记备注」「能不能回退」
--    这些没有需求的问题——正是 §11 警告的那件事。
--    真需要自由事件流（比如物流轨迹）时再加，那时候需求会是具体的。
--
-- 2. **订单行存商品快照，不 join 商品表。** goodsname / imgurl / price 都在
--    u_s_orderitem 上存一份。商品下架、改名、调价都不能改变一张已经下的单——
--    这和 §5.4「商品从挑变成录」是同一个道理：要的是当时那一份，不是现在那一份。
--
-- 3. **客户按邮箱去重，不按人名。** 唯一键是 (shop_code, email)。外贸场景里
--    同一个人换公司、同一个公司来好几个人都很常见，邮箱是唯一稳定的标识；
--    按人名去重会把两个同名的人合成一个。
--
-- 可重复执行：只有 CREATE TABLE IF NOT EXISTS 和 INSERT IGNORE。
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- =====================================================================
-- 一、站点订单
-- =====================================================================
--
-- ⚠ 和 ERP 的订单**没有任何隶属关系**（boundary 要点 4）。这里没有一列指向
--   sdsorder 的任何东西，也没有「同步状态」列——一期的同步方式是「像拼多多一样
--   人工在 ERP 里录一张」，那是流程，不是字段（§8：不做，也不预埋接口）。
--
-- 状态词和前台 station-site/app/lib/shop.ts 的 OrderStatus 一一对应：
--     submitted → 待确认   confirmed → 已确认
--     shipped   → 已发货   delivered → 已完成   cancelled → 已取消
-- 库里存中文，前台那边按站点语言翻——库里存英文的话，管理台要为了显示再翻一遍，
-- 而管理台只有中文界面。

CREATE TABLE IF NOT EXISTS `u_s_order` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`     varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】恒为建站时那个商户码，新代码不得读它',
  `sitecode`      varchar(50)   NOT NULL COMMENT '来自哪个站点，恒等于 u_s_siteinfo.sitecode',
  `ordercode`     varchar(50)   NOT NULL COMMENT '订单号，DD + Base10 流水。由后端发号，不接受前端传',

  `status`        varchar(20)   NOT NULL DEFAULT '待确认' COMMENT '待确认/已确认/已发货/已完成/已取消',
  `source`        varchar(20)   NOT NULL DEFAULT 'site' COMMENT '来源：site 前台自助提交 / console 管理台手工录',

  -- ---- 金额 ----
  `currency`      varchar(10)   NOT NULL DEFAULT 'USD' COMMENT '币种，下单时站点的币种',
  `subtotal`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '有标价那部分的小计。⚠ 不含运费、不含面议商品',
  `inquireonly`   int           NOT NULL DEFAULT 0 COMMENT '价格面议的行数。>0 时这张单的 subtotal 不是全部金额',

  -- ---- 联系人。⚠ 不 join 客户表，存快照 ----
  `contactname`   varchar(100)  NOT NULL COMMENT '联系人',
  `email`         varchar(150)  NOT NULL COMMENT '邮箱。回复靠它，也是关联客户档案的键',
  `company`       varchar(200)  DEFAULT NULL COMMENT '公司名',
  `phone`         varchar(50)   DEFAULT NULL COMMENT '电话 / WhatsApp',

  -- ---- 目的地 ----
  `addrline1`     varchar(200)  DEFAULT NULL COMMENT '地址第一行',
  `addrline2`     varchar(200)  DEFAULT NULL COMMENT '地址第二行',
  `city`          varchar(80)   DEFAULT NULL COMMENT '城市',
  `region`        varchar(80)   DEFAULT NULL COMMENT '州 / 省',
  `postal`        varchar(30)   DEFAULT NULL COMMENT '邮编',
  `country`       varchar(80)   NOT NULL COMMENT '国家。唯一必填的地址字段——估运费最少要它',

  -- ---- 买家的倾向。⚠ 只是倾向，不是定下来的条款 ----
  `shippingpref`  varchar(20)   DEFAULT NULL COMMENT '运输倾向：sea/air/express/agent，见 shop.ts SHIPPING_PREFS',
  `paymentpref`   varchar(20)   DEFAULT NULL COMMENT '付款倾向：tt/lc/paypal/other，见 shop.ts PAYMENT_PREFS',
  `buyernote`     varchar(800)  DEFAULT NULL COMMENT '买家补充说明（目标价、交期、包装、认证……）',

  -- ---- 四段时间轴。见文件头「不建第三张表」那一条 ----
  `confirmtime`   datetime      DEFAULT NULL COMMENT '报价/订单确认时间。为空表示还没确认',
  `shiptime`      datetime      DEFAULT NULL COMMENT '发货时间',
  `finishtime`    datetime      DEFAULT NULL COMMENT '完成（送达）时间',

  `buyercode`     varchar(50)   DEFAULT NULL COMMENT '本站客户编码，指向 u_s_buyer.buyercode。下单时按邮箱自动挂上',
  `clientip`      varchar(64)   DEFAULT NULL COMMENT '提交时的客户端 IP，查刷单用',
  `handleuser`    varchar(50)   DEFAULT NULL COMMENT '最后处理人',
  `handletime`    datetime      DEFAULT NULL COMMENT '最后处理时间',
  `remark`        varchar(500)  DEFAULT NULL COMMENT '内部备注，买家看不到',
  `createtime`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updatetime`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_code` (`shop_code`,`ordercode`),
  KEY `idx_order_list` (`shop_code`,`sitecode`,`status`,`createtime`),
  KEY `idx_order_buyer` (`shop_code`,`buyercode`,`createtime`) COMMENT '客户详情页要列出他的订单',
  KEY `idx_order_email` (`email`) COMMENT '按邮箱找单：客户来信问进度时用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点订单。⚠ 与 ERP 订单无任何隶属关系（boundary 要点 4）';


-- =====================================================================
-- 二、订单行
-- =====================================================================
--
-- ⚠ 每一行都是**下单当时的商品快照**，不 join u_s_sitegoods。
--   商品改名、调价、下架都不影响已下的单。理由见文件头第 2 条。

CREATE TABLE IF NOT EXISTS `u_s_orderitem` (
  `id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`  varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `ordercode`  varchar(50)   NOT NULL COMMENT '所属订单号',
  `goodscode`  varchar(50)   NOT NULL COMMENT '商品编码（下单当时）',
  `goodsname`  varchar(200)  DEFAULT NULL COMMENT '商品名快照',
  `subtitle`   varchar(200)  DEFAULT NULL COMMENT '名称下那行小字，一般是分类',
  `imgurl`     varchar(500)  DEFAULT NULL COMMENT '主图快照',
  `price`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '单价快照。⚠ 0 表示价格面议，不是免费',
  `currency`   varchar(10)   DEFAULT NULL COMMENT '币种快照',
  `qty`        decimal(18,4) NOT NULL DEFAULT '1.0000' COMMENT '数量。外贸按米/千克下单，所以是小数不是整数',
  `unit`       varchar(20)   DEFAULT NULL COMMENT '单位',
  `linetotal`  decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '行小计 = price × qty。面议行为 0',
  `sortnum`    int           NOT NULL DEFAULT 0 COMMENT '行序，保持买家清单里的顺序',
  PRIMARY KEY (`id`),
  KEY `idx_orderitem_order` (`shop_code`,`ordercode`,`sortnum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点订单明细（下单当时的商品快照）';


-- =====================================================================
-- 三、站点客户
-- =====================================================================
--
-- ⚠ 这是**站点自己的**买家档案，和 ERP 的销售客户（sdsshop.u_m_buyer）没有任何关系
--   （boundary §6c）。没有一列指向那边，也没有「转 ERP 客户」这个动作——
--   那个动作是一条互通，§10.1 里已经删掉了。
--
-- 档案是**长出来的**，不是建出来的：谁发了询盘或者下了单，就按邮箱为他落一行。
-- 管理台里能改的只有备注和标签，联系方式跟着最近一次提交更新。
-- 这和 u_s_user「谁从 ERP 跳进来就落一行」是同一个思路：没有「新建」按钮，
-- 就没有「建了一个谁都用不上的档案」这种状态。

CREATE TABLE IF NOT EXISTS `u_s_buyer` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `shop_code`    varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`     varchar(50)  NOT NULL COMMENT '来自哪个站点',
  `buyercode`    varchar(50)  NOT NULL COMMENT '客户编码，KH + Base10 流水',

  `email`        varchar(150) NOT NULL COMMENT '邮箱。⚠ 客户的身份就是它，见下面的唯一键',
  `name`         varchar(100) DEFAULT NULL COMMENT '联系人（最近一次提交时填的）',
  `company`      varchar(200) DEFAULT NULL COMMENT '公司名（同上）',
  `phone`        varchar(50)  DEFAULT NULL COMMENT '电话 / WhatsApp（同上）',
  `country`      varchar(80)  DEFAULT NULL COMMENT '国家。只有下过单才有——询盘表单不收这一项',

  `inquirycount` int          NOT NULL DEFAULT 0 COMMENT '累计询盘数',
  `ordercount`   int          NOT NULL DEFAULT 0 COMMENT '累计下单数',
  `lastactive`   datetime     DEFAULT NULL COMMENT '最近一次询盘或下单的时间。客户列表默认按它排',

  `tag`          varchar(50)  DEFAULT NULL COMMENT '运营自己打的标签，如「重点」「已寄样」。一期就一个自由文本，不做标签表',
  `remark`       varchar(500) DEFAULT NULL COMMENT '内部备注',
  `createtime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现的时间',
  `updatetime`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_code` (`shop_code`,`buyercode`),
  -- ⚠ 客户按邮箱去重。见文件头第 3 条：按人名去重会把两个同名的人合成一个
  UNIQUE KEY `uk_buyer_email` (`shop_code`,`email`),
  KEY `idx_buyer_active` (`shop_code`,`sitecode`,`lastactive`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点客户档案。⚠ 与 ERP 的 u_m_buyer 无任何关系（boundary §6c）';


-- =====================================================================
-- 四、发号器补两行
-- =====================================================================
-- 前缀 DD（订单）/ KH（客户）。KH 和 ERP 的客户前缀是同一个字母组合，
-- 但那是**另一个库里另一张表**的号，两边各自计数、互不相干。

INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES
  ('order', 1), ('buyer', 1);


-- =====================================================================
-- 五、验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = 'sdsstation' AND table_name = 'u_s_order')     AS `u_s_order`,
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = 'sdsstation' AND table_name = 'u_s_orderitem') AS `u_s_orderitem`,
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = 'sdsstation' AND table_name = 'u_s_buyer')     AS `u_s_buyer`,
  (SELECT COUNT(*) FROM `u_s_sequence` WHERE name IN ('order','buyer')) AS `发号器行数`;


-- =====================================================================
-- 六、给 operator 角色补上新模块的权限
-- =====================================================================
--
-- ⚠ 出厂角色是 INSERT IGNORE 插的，已经存在的行不会被更新，所以这里要 UPDATE。
--
-- 给运营的是**看得到、能跟进，但不能改单**：
--   site:order:list / query / edit   —— 订单要能推进状态，那是运营的日常
--   site:buyer:list / query / edit   —— 客户备注和标签要能改
--
-- 站长（perms = '*'）不用动。
--
-- ⚠ 只在没有这些串的时候追加，避免重复跑出 'site:order:list,site:order:list'。
UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`,
         ',site:order:list,site:order:query,site:order:edit',
         ',site:buyer:list,site:buyer:query,site:buyer:edit')
 WHERE `rolecode` = 'operator'
   AND `perms` NOT LIKE '%site:order:list%';

SELECT `rolecode`, `perms` FROM `u_s_role` ORDER BY `sortnum`;
