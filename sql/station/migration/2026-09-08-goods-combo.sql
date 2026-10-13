-- =====================================================================
-- 商品组合（规格型号选择）：两张新表 + 单据上的两列快照
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台跑的是 sql/migration/ 下面那些。
--
-- 详情页上「Combo」那一排带缩略图的选项卡。参考站
-- （kangwanjia-website-EN/pages/product/detail.vue:349）那份是写死的常量数组，
-- 注释自己写着 until the real combo list is wired up——没有价格、没有标识、
-- 选中只换个高亮。这里做的是能用的那一版。
--
-- ---------------------------------------------------------------------
-- 为什么是两张表，不是 u_s_sitegoods 上加一列 JSON
-- ---------------------------------------------------------------------
--
-- 因为**组合名要分语言**。一列 JSON 存得下 [{title,price,image}]，但塞进七种语言
-- 之后就变成 {"en":[...],"ja":[...]}，于是「第三个组合」在两种语言里靠**下标**对齐——
-- 商家在英文里删掉中间一条，其余六种语言的对应关系全错位，而且不报错。
--
-- 拆成主表 + 语言表之后，对齐靠的是 combocode，删一条就是删一条。
-- 这和 u_s_sitegoods / u_s_sitegoods_lang 是同一个结构，没有发明新东西。
--
-- ---------------------------------------------------------------------
-- 三个刻意的取舍
-- ---------------------------------------------------------------------
--
-- 1. **不做库存。** 这个站不收款不发货，订单是「下单请求」，履约在线下；
--    而 ERP 和独立站不互通（boundary 要点 1），站内库存没有数据来源，
--    只能手工维护，很快就和实际对不上，然后没人敢信它。
--    需要「售罄置灰」的话 status 那一列就够了，不需要数量。
--
-- 2. **图片不分语言。** 装修图分语言是因为不同市场配不同的图；组合缩略图是
--    产品实拍，七种语言是同一张。放主表。
--
-- 3. **单据上存快照，不 join。** 和 u_s_orderitem 存 goodsname/price 是同一个道理：
--    商家改名、调价、删掉一个组合，都不能改变一张已经提交的询盘或订单。
--    所以下面给 u_s_orderitem 和 u_s_inquiry 各加 combocode + comboname 两列——
--    **combocode 不是外键**，指向的那条可能已经没了，这是对的。
--
-- 可重复执行：CREATE TABLE IF NOT EXISTS + 加列前查 information_schema
-- （MySQL 没有 ADD COLUMN IF NOT EXISTS）。
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- =====================================================================
-- 一、组合主表
-- =====================================================================
--
-- 主键带 shop_code / sitecode 是跟着这个库里所有商品相关表的既有形状走的
-- （u_s_sitegoods、u_s_sitegoods_lang 都是这三段起头）。一个部署一个站，
-- 这两列不是筛选维度，写入时由 SiteContext 填。

CREATE TABLE IF NOT EXISTS `u_s_sitegoods_combo` (
  `shop_code`  varchar(50)   NOT NULL DEFAULT '' COMMENT '【遗留列】恒为建站时那个商户码',
  `sitecode`   varchar(50)   NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',
  `goodscode`  varchar(50)   NOT NULL COMMENT '所属商品编码',
  `combocode`  varchar(50)   NOT NULL COMMENT '组合编码。⚠ 这是**稳定标识**：名称分七种语言、还会被商家改，只有它不变。单据快照、深链 ?combo=、按组合做统计全靠它',
  `price`      decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '该组合的价格。⚠ 0 = 面议，不是免费（和 u_s_sitegoods.price 同一约定）',
  `image`      varchar(500)  DEFAULT NULL COMMENT '组合缩略图完整 URL。各语言通用——产品实拍不分市场。留空则选项卡只显示名字',
  `sortnum`    int           NOT NULL DEFAULT 0 COMMENT '显示顺序，越小越靠前。第一条是详情页默认选中的那个',
  `status`     varchar(20)   NOT NULL DEFAULT '可选' COMMENT '可选/停用。停用的不对外返回，但历史单据里的快照还在',
  `createuser` varchar(50)   DEFAULT NULL COMMENT '创建人',
  `createtime` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50)   DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`,`combocode`),
  KEY `idx_combo_goods` (`shop_code`,`sitecode`,`goodscode`,`status`,`sortnum`) COMMENT '详情页取某个商品的可选组合走这条'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品组合（规格型号），语言无关的部分';


-- =====================================================================
-- 二、组合名称多语言
-- =====================================================================
--
-- 只有一个 title。价格、图片、排序都在主表——那些改一次七种语言一起变，
-- 放语言表就要改七行，还会出现「六种语言 199、一种语言 299」这种没人发现的错。

CREATE TABLE IF NOT EXISTS `u_s_sitegoods_combo_lang` (
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】',
  `sitecode`   varchar(50)  NOT NULL COMMENT '站点编码',
  `goodscode`  varchar(50)  NOT NULL COMMENT '所属商品编码',
  `combocode`  varchar(50)  NOT NULL COMMENT '所属组合编码',
  `lang`       varchar(20)  NOT NULL COMMENT '语言代码，如 en / ru / ja',
  `title`      varchar(200) DEFAULT NULL COMMENT '该语言下的组合名。某种语言没填时，接口退回站点默认语言那一条',
  `createtime` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`,`goodscode`,`combocode`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品组合的多语言名称';


-- =====================================================================
-- 三、单据上的快照列
-- =====================================================================

DROP PROCEDURE IF EXISTS `__add_combo_snapshot_columns`;

DELIMITER $$
CREATE PROCEDURE `__add_combo_snapshot_columns`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- ---- 订单明细 ----
  --
  -- 放在 goodsname 后面：这两列和它是一伙的，都是「下单当时长什么样」。
  -- price 那一列不用动——选了组合之后，行单价**就是**组合价，
  -- 再加一列 comboprice 就会出现两个价格字段，早晚有人取错那个。
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_orderitem'
                   AND column_name = 'combocode') THEN
    ALTER TABLE `u_s_orderitem`
      ADD COLUMN `combocode` varchar(50) DEFAULT NULL
      COMMENT '选中的组合编码（下单当时）。⚠ 不是外键：那条组合可能已经被删了，历史单不受影响'
      AFTER `goodsname`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_orderitem'
                   AND column_name = 'comboname') THEN
    ALTER TABLE `u_s_orderitem`
      ADD COLUMN `comboname` varchar(200) DEFAULT NULL
      COMMENT '组合名称快照，买家下单时看到的那个语言的那一份。商家改名不影响历史单'
      AFTER `combocode`;
  END IF;

  -- ---- 询盘 ----
  --
  -- 挨着 producttitle 放，那一列存的也是「客户看到的那个标题」，同一类东西。
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_inquiry'
                   AND column_name = 'combocode') THEN
    ALTER TABLE `u_s_inquiry`
      ADD COLUMN `combocode` varchar(50) DEFAULT NULL
      COMMENT '询盘针对的组合编码。⚠ 不是外键，理由同 u_s_orderitem.combocode'
      AFTER `producttitle`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_inquiry'
                   AND column_name = 'comboname') THEN
    ALTER TABLE `u_s_inquiry`
      ADD COLUMN `comboname` varchar(200) DEFAULT NULL
      COMMENT '组合名称快照。销售看询盘时要知道客户问的是哪一款，不能只有编码'
      AFTER `combocode`;
  END IF;
END$$
DELIMITER ;

CALL `__add_combo_snapshot_columns`();
DROP PROCEDURE `__add_combo_snapshot_columns`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_combo')      AS `组合主表_应为1`,
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods_combo_lang') AS `组合语言表_应为1`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_orderitem'
      AND column_name IN ('combocode','comboname'))                              AS `订单明细快照列_应为2`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_inquiry'
      AND column_name IN ('combocode','comboname'))                              AS `询盘快照列_应为2`;
