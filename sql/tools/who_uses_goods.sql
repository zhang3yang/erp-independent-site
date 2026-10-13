-- =====================================================================
-- 查商品到底被谁占用着（删除按钮为什么是灰的）
-- =====================================================================
--
-- 起因：商品信息列表里删除按钮变灰、提示「已产生单据或库存记录，不能删除」，
-- 但看不出是哪张单据或哪条库存在挡着。
--
-- 前端的灰化只看一个字段：sdsshop.u_m_waregoods.usedflag = 1
--   （RuoYi-Vue3/src/views/inventory/goods/index.vue:93）
-- 后端真删的时候再多查两道：库存余额 + 生产引用
--   （InventoryGoodsServiceImpl.isGoodsUsed / assertNotUsedByProduction）
--
-- 关键：usedflag 是只涨不落的闩。任何模块保存单据明细时就置 1
--   （DocumentServiceImpl.markGoodsUsed，保存即置位，不等审核），
-- 单据后来被删掉、驳回、作废，这一位都不会回到 0。
-- 所以「按钮是灰的」不等于「现在还有东西引用它」——
-- 下面第 2、3 段就是用来分辨这两种情况的。
--
-- ⚠ 只读。全程只 SELECT，不改任何数据，生产库上跑是安全的。
--
-- 用法（先把下面的商品编码换掉，多个用英文逗号隔开、不要留空格）：
--   本地：   mysql -uroot -p --default-character-set=utf8mb4 --table < who_uses_goods.sql
--   连远程： mysql -h <主机> -uroot -p --default-character-set=utf8mb4 --table < who_uses_goods.sql
-- =====================================================================

SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 4194304;

-- ↓↓↓ 要查的商品编码，多个用逗号隔开（不要空格）↓↓↓
SET @goods := '12023924,12023925,12023926';

-- ---------------------------------------------------------------------
-- 1. 商品自身：闩位到底是不是 1
-- ---------------------------------------------------------------------
SELECT '=== 1. 商品自身 ===' AS `段落`;

SELECT shop_code  AS `商家`,
       goodscode  AS `商品编码`,
       goodsname  AS `商品名称`,
       spec       AS `规格型号`,
       usedflag   AS `usedflag(1=按钮变灰)`,
       activeflag AS `activeflag(1=启用)`
  FROM sdsshop.u_m_waregoods
 WHERE FIND_IN_SET(goodscode, @goods) > 0
 ORDER BY shop_code, goodscode;

-- ---------------------------------------------------------------------
-- 2. 全库扫一遍：哪些表里真的还有这些商品编码
--
--    自动覆盖 sdsshop / sdsorder 下所有带 goodscode / productcode / goods_code
--    / product_code 的表（两种命名都有：u_f_ledger_* 和 u_m_goods_position 用蛇形），
--    包括按月分表的 u_f_docdetail_yyyyMM、u_o_orderdetail_yyyyMM、
--    u_f_ledger_yyyyMM、u_p_receivedetail_yyyyMM 等，新月份不用改脚本。
--    某个编码在这一段没有出现 = 没有任何地方引用，那它的 usedflag 就是个过期的闩。
-- ---------------------------------------------------------------------
SELECT '=== 2. 还有哪些表引用着它 ===' AS `段落`;

SELECT GROUP_CONCAT(
         CONCAT('SELECT ''', c.table_schema, '.', c.table_name, ''' AS tbl, ''',
                c.column_name, ''' AS col, shop_code AS shop, `', c.column_name,
                '` AS goods, COUNT(*) AS hits FROM `',
                c.table_schema, '`.`', c.table_name, '` WHERE FIND_IN_SET(`',
                c.column_name, '`, @goods) > 0 GROUP BY shop_code, `',
                c.column_name, '`')
         SEPARATOR ' UNION ALL ')
  INTO @scan
  FROM information_schema.columns c
 WHERE c.table_schema IN ('sdsshop', 'sdsorder')
   AND c.column_name IN ('goodscode', 'productcode', 'goods_code', 'product_code')
   AND NOT (c.table_schema = 'sdsshop' AND c.table_name = 'u_m_waregoods')
   AND EXISTS (SELECT 1 FROM information_schema.columns s
                WHERE s.table_schema = c.table_schema
                  AND s.table_name = c.table_name
                  AND s.column_name = 'shop_code');

SET @scan = IF(@scan IS NULL,
               'SELECT ''没找到任何带商品编码字段的表'' AS `说明`',
               CONCAT('SELECT goods AS `商品编码`, tbl AS `表`, col AS `字段`, ',
                      'shop AS `商家`, hits AS `命中行数` FROM (', @scan,
                      ') t ORDER BY goods, tbl'));
PREPARE stmt FROM @scan;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 落到单据号：库存单据的哪几张单把它写进了明细
--
--    连 u_f_docmain_yyyyMM 取单据类型和审核状态。
--    confirmstate 存的是中文状态（待审核 / 已审核 / 已驳回）。
--    单据类型为空说明主表里已经没有这张单，只剩一条孤立明细。
-- ---------------------------------------------------------------------
SELECT '=== 3. 具体是哪几张库存单据 ===' AS `段落`;

SELECT GROUP_CONCAT(
         CONCAT('SELECT d.shop_code AS shop, d.goodscode AS goods, d.doccode AS doc, ',
                'm.doctypename AS dtype, m.docdate AS ddate, m.confirmstate AS cstate, ',
                'd.qty AS qty, d.warename AS ware, d.batchcode AS batch FROM `sdsorder`.`',
                t.table_name, '` d LEFT JOIN `sdsorder`.`u_f_docmain_',
                SUBSTRING(t.table_name, -6), '` m ON m.shop_code = d.shop_code ',
                'AND m.doccode = d.doccode WHERE FIND_IN_SET(d.goodscode, @goods) > 0')
         SEPARATOR ' UNION ALL ')
  INTO @docs
  FROM information_schema.tables t
 WHERE t.table_schema = 'sdsorder'
   AND t.table_name REGEXP '^u_f_docdetail_[0-9]{6}$';

SET @docs = IF(@docs IS NULL,
               'SELECT ''这个库里没有 u_f_docdetail_yyyyMM 月表'' AS `说明`',
               CONCAT('SELECT goods AS `商品编码`, shop AS `商家`, doc AS `单据号`, ',
                      'dtype AS `单据类型`, ddate AS `单据日期`, cstate AS `审核状态`, ',
                      'qty AS `数量`, ware AS `仓库`, batch AS `批次` FROM (', @docs,
                      ') t ORDER BY goods, ddate, doc'));
PREPARE stmt FROM @docs;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 结果怎么读
--
--    第 2 段有输出 → 就是那些表在占用，按表名对号去解绑：
--      sdsshop.u_m_stock              库存余额行（数量为 0 也算占用，得先清掉这行）
--      sdsshop.u_m_batchinfo          批次
--      sdsshop.u_p_bom                它自己是某个配方的产品
--      sdsshop.u_p_bomdetail          配方物料（生产管理 → 产品配方里解绑）
--      sdsshop.u_p_production         生产工单主表（productcode）
--      sdsshop.u_p_productiondetail   生产工单产出
--      sdsshop.u_p_productionmaterial 生产工单物料
--      sdsshop.u_p_purchasedetail     采购单明细
--      sdsshop.u_m_goods_position     商品货位
--      sdsshop.u_m_goods_partner      商品供应商绑定
--      sdsshop.u_m_warning_rule       库存预警规则
--      sdsshop.u_o_reservation        订单预留占用
--      sdsorder.u_f_docdetail_*       库存单据明细（单据号见第 3 段）
--      sdsorder.u_o_orderdetail_*     销售订单明细
--      sdsorder.u_f_ledger_*          库存流水（历史账，不该删）
--      sdsorder.u_f_productionlog_*   生产过账日志（历史账，不该删）
--      sdsorder.u_f_shipbatchdetail_* 发货批次明细
--      sdsorder.u_p_receivedetail_*   采购收货明细
--
--    某个编码在第 2 段完全没出现 → 没有任何地方引用，usedflag 是历史残留的闩。
--      这时只剩 isGoodsUsed 的第一道判据在挡，业务上确实可以删。两条路：
--      a) 按提示把状态改成「停用」——推荐，账面最干净，也不用动数据库；
--      b) 确认过第 2 段真的没有它之后手动放闩：
--           UPDATE sdsshop.u_m_waregoods SET usedflag = 0
--            WHERE shop_code = '<第 1 段查出来的商家>' AND goodscode = '<编码>';
--         放闩后商品的名称/规格/单位/商品类型/批次策略/成本方法也会重新变成可改，
--         所以只在确认为空时才这么做。
-- ---------------------------------------------------------------------
