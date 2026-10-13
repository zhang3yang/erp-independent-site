-- 商品供货关系表（2026-09-04）。可重复执行，跑两遍结果一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 新建 sdsshop.u_m_goods_partner，记录「这个商品我从哪几家供应商进过货」，
-- 给采购下单和手工入库的供应商选择做推荐。
--
--   主键 (shop_code, goodscode, partnercode)
--   inboundtimes  已审核采购入库次数
--   last*         最近一次入库的单号、单据日期、成本单价
--
-- 由 PurchaseInboundServiceImpl.confirmPurchaseInbound 在**审核通过**时累积，
-- 三条入库通道（手工直接入库、采购订单入库、采购发货入库）都收口在那个方法上，
-- 所以一处埋点覆盖全部。驳回和待审核的单不累积——没审核就不算真进过货。
--
-- ============================================================================
-- 为什么要单独一张表，不直接查历史单据
-- ============================================================================
--
-- 「这个商品的历史供应商」只能从 u_f_docmain.partnercode + u_f_docdetail.goodscode
-- 得到，而这两张是按月分表的，且 u_f_docdetail 上没有 goodscode 索引
-- （见 MonthlyTableInitializer.buildDocDetailDDL）。查一次要把所有月表 UNION 起来
-- 再全表扫，每过一个月慢一点，而这个查询是「添加商品」时同步触发的。
--
-- 换成 u_p_purchasedetail（有 idx_shop_goods）则覆盖不到手工入库——手工入库本来
-- 就是「没有采购订单」的场景，只查采购明细的话，一个只走过手工入库的商品永远
-- 推荐不出东西。所以取两条路之外的第三条：写入时累积，读时一次等值查询。
--
-- ⚠ 这张表 100% 由已审核入库单派生，不参与任何计算，只影响界面上的推荐排序。
--   对不上就重跑本脚本刷一遍，不影响账。
--
-- ============================================================================
-- ⚠ 第 2 段会先清空再重建
-- ============================================================================
--
-- 因为要跨全部月表做 COUNT(DISTINCT doccode) 和「取最近一单」，增量 UPSERT
-- 会把次数累重，只能整表重算。表是纯派生的，清空重建后状态和重算前一致，
-- 所以脚本可以反复跑；但**不要**在这张表上手工维护数据，会被下次重跑冲掉。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260904_goods_partner_recommend.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260904_goods_partner_recommend.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 建表
-- ============================================================

CREATE TABLE IF NOT EXISTS `sdsshop`.`u_m_goods_partner` (
  `shop_code`     varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `goodscode`     varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品代码',
  `partnercode`   varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '供应商代码',
  `partnername`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '供应商名称（冗余，展示用）',
  `inboundtimes`  int NOT NULL DEFAULT '0' COMMENT '已审核采购入库次数',
  `lastdoccode`   varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次入库单号',
  `lastdocdate`   datetime DEFAULT NULL COMMENT '最近一次入库的单据日期',
  `lastcostprice` decimal(18,6) DEFAULT NULL COMMENT '最近一次入库的成本单价',
  `createtime`    datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
  `updatetime`    datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`goodscode`,`partnercode`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC
  COMMENT='商品供货关系（推荐供应商用，由已审核采购入库单累积，纯派生表）';

-- 主键前缀 (shop_code, goodscode) 就是推荐查询的条件，一个商品的供应商行数是个位数，
-- 排序在内存里做，不再额外建索引。

-- ============================================================
-- 2. 从历史月表回刷
-- ============================================================
--    入库单主表按月分表，doctype='RK' 是采购入库，只取已审核的。
--    先把全部月表的「单据 × 商品 × 供应商」摊进临时表，再一次聚合。

DELETE FROM `sdsshop`.`u_m_goods_partner`;

DROP PROCEDURE IF EXISTS sdsshop.fill_goods_partner;
DELIMITER $$
CREATE PROCEDURE sdsshop.fill_goods_partner()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE suffix CHAR(6);

  DECLARE cur_docmain CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_f_docmain_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  -- 中转表故意不用 TEMPORARY：下面的聚合要在同一条 SELECT 里引用它两次
  -- （一次取最近一单，一次数次数），而临时表在一条语句里只能打开一次。
  -- 用完就 DROP，脚本跑完库里不留东西。
  DROP TABLE IF EXISTS `sdsshop`.`tmp_goods_partner_src`;
  CREATE TABLE `sdsshop`.`tmp_goods_partner_src` (
    shop_code   varchar(50)  NOT NULL,
    goodscode   varchar(50)  NOT NULL,
    partnercode varchar(50)  NOT NULL,
    partnername varchar(100) NOT NULL,
    doccode     varchar(50)  NOT NULL,
    docdate     datetime     NOT NULL,
    costprice   decimal(18,6) DEFAULT NULL,
    KEY idx_src (shop_code, goodscode, partnercode)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

  OPEN cur_docmain;
  doc_loop: LOOP
    FETCH cur_docmain INTO tname;
    IF done = 1 THEN LEAVE doc_loop; END IF;
    SET suffix = RIGHT(tname, 6);

    -- 明细月表缺失（历史遗留的空壳主表）就跳过，不然拼出来的 SQL 会报表不存在
    IF (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = 'sdsorder'
           AND table_name = CONCAT('u_f_docdetail_', suffix)) > 0 THEN
      SET @s = CONCAT(
        'INSERT INTO `sdsshop`.`tmp_goods_partner_src`
           (shop_code, goodscode, partnercode, partnername, doccode, docdate, costprice)
         SELECT m.shop_code, d.goodscode, m.partnercode,
                COALESCE(m.partnername, ''''), m.doccode, m.docdate, d.costprice
           FROM `sdsorder`.`', tname, '` m
           JOIN `sdsorder`.`u_f_docdetail_', suffix, '` d
             ON d.shop_code = m.shop_code AND d.doccode = m.doccode
          WHERE m.doctype = ''RK''
            AND m.confirmstate = ''已审核''
            AND m.partnercode IS NOT NULL AND m.partnercode <> ''''
            AND d.goodscode IS NOT NULL AND d.goodscode <> ''''');
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_docmain;

  -- last* 取单据日期最大的那一单（同日期按单号倒序定序，保证结果稳定）；
  -- 次数按单据去重计 —— 同一张单同一商品只会有一行，去重是防历史脏数据。
  INSERT INTO `sdsshop`.`u_m_goods_partner`
    (shop_code, goodscode, partnercode, partnername,
     inboundtimes, lastdoccode, lastdocdate, lastcostprice)
  SELECT last_doc.shop_code, last_doc.goodscode, last_doc.partnercode, last_doc.partnername,
         times.doc_count, last_doc.doccode, last_doc.docdate, last_doc.costprice
    FROM (
      SELECT s.*,
             ROW_NUMBER() OVER (PARTITION BY s.shop_code, s.goodscode, s.partnercode
                                    ORDER BY s.docdate DESC, s.doccode DESC) AS rn
        FROM `sdsshop`.`tmp_goods_partner_src` s
    ) last_doc
    JOIN (
      SELECT shop_code, goodscode, partnercode, COUNT(DISTINCT doccode) AS doc_count
        FROM `sdsshop`.`tmp_goods_partner_src`
       GROUP BY shop_code, goodscode, partnercode
    ) times
      ON times.shop_code = last_doc.shop_code
     AND times.goodscode = last_doc.goodscode
     AND times.partnercode = last_doc.partnercode
   WHERE last_doc.rn = 1;

  DROP TABLE IF EXISTS `sdsshop`.`tmp_goods_partner_src`;
END $$
DELIMITER ;

CALL sdsshop.fill_goods_partner();
DROP PROCEDURE IF EXISTS sdsshop.fill_goods_partner;

SELECT CONCAT('u_m_goods_partner 回刷完成，共 ', COUNT(*), ' 条供货关系') AS msg
  FROM `sdsshop`.`u_m_goods_partner`;
