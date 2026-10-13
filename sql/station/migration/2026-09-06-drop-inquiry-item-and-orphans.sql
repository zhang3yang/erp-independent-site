-- =====================================================================
-- 收口结账链路：删掉被订单接口取代的询盘明细表，清掉孤儿明细行
-- =====================================================================
--
-- 库：sdsstation（独立站那台服务器上的）。可重复执行。
--
-- =====================================================================
-- 背景：同一件事曾经有三套设计并存
-- =====================================================================
--
-- 2026-09-06 盘点时发现，「买家把购物车提交给商家」这一件事，仓库里有三套实现：
--
--   ① 前台现状      checkout/index.vue → POST /openapi/site/inquiry，
--                   收货地址/运输付款偏好/N 行明细全部 buildMessage() 拼成纯文本
--                   塞进 message（上限 2000 字，超了**直接截断丢明细**），
--                   订单记录只存在买家浏览器的 localStorage 里。
--
--   ② RFQ 结构化    migration/2026-09-04-inquiry-structured-rfq.sql
--                   给 u_s_inquiry 加了 13 列 + 建了 u_s_inquiry_item 明细表，
--                   打算让 ① 不再拼文本。**那次迁移只落了库，代码一处没接**：
--                   SiteInquiry.java 和 SiteInquiryMapper.xml 一个新列都没用，
--                   u_s_inquiry_item 在整个后端零引用，建完就一直是空表。
--
--   ③ 订单接口      POST /openapi/site/order → SiteOrderService → u_s_order +
--                   u_s_orderitem。**这一套是完整的**：下单、列表、详情、
--                   状态流转校验、买家按身份自查、管理台订单管理全都有，
--                   而且价格由服务端从 u_s_sitegoods 现查、不信前端传的。
--
-- =====================================================================
-- 决定（2026-09-06）：③ 收口，② 作废
-- =====================================================================
--
-- ③ 已经写好且更完整，② 只是 ① 的改良版。同一件事留两套表，迟早出现
-- 「这单在询盘里还是在订单里」这种问不清楚的问题。
--
-- 配套改动（不在本脚本里）：
--   · 前台 checkout/index.vue 改成 POST /openapi/site/order，删掉 buildMessage()
--     那段预算截断；useOrders 从 localStorage 改成按买家身份 GET 服务端。
--   · migration/2026-09-04-inquiry-structured-rfq.sql 顶部标注 superseded。
--   · 基线 01_station_schema.sql 补上那 13 列（**保留**，理由见下）。
--
-- 那 13 列为什么保留不删：全部可空、全部是 NULL，留着成本是零；而删列不可逆，
-- 且要在每个已部署的站上各做一次 ALTER。基线里也照建，让新站和老站结构逐列一致。
-- 明细表不一样——它是一张独立的表，删掉不影响任何一行现有数据。
--
-- =====================================================================
-- 顺带：孤儿订单明细的兜底清理
-- =====================================================================
--
-- ⚠ **这一段在 49.234.194.194 上实际删了 0 行**，写进来是当兜底，不是在修一个已知故障。
--   经过值得记下来，因为踩的坑很容易再踩：
--
--   2026-09-06 盘点时用
--       SELECT table_name, table_rows FROM information_schema.tables ...
--   看到 u_s_order 是 0 行、u_s_orderitem 是 2 行，判定成「明细挂在不存在的订单上」。
--
--   **`information_schema.tables.table_rows` 对 InnoDB 是抽样估算值，不是真实行数。**
--   改用 COUNT(*) 一查，u_s_order 有 1 行，就是那两行明细的父单 DD0000000003
--   （已完成，买家 KH0000000002，小计 1560 和明细对得上）。数据一直是好的。
--
--   盘点库存量一律用 COUNT(*)。table_rows 只能用来估「这张表是大是小」。
--
-- 之所以还是留着这条 DELETE：它幂等、没有匹配就删 0 行，而在别的站上
-- （或者将来真的出现半截写入时）它是对的。
--
-- 删除条件是 **left join 找不到父单**，不是写死某个单号——写死的话在别的站上会误伤。
-- 这一点是这次没删错东西的唯一原因：判断依据错了，但删除条件是对的。
--
-- 备份（跑之前留的，事后证明用不上，留档）：
--   本机     scratchpad/u_s_orderitem-backup-20260906.sql
--   服务器   C:\wczlerp\backup\u_s_orderitem-orphan-20260906.sql
--
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 一、清掉没有父单的订单明细（兜底，2026-09-06 在康辰站实删 0 行）
--
-- 可重复执行：匹配不到就删 0 条。
-- ---------------------------------------------------------------------
DELETE i
  FROM `u_s_orderitem` i
  LEFT JOIN `u_s_order` o ON o.`ordercode` = i.`ordercode`
 WHERE o.`ordercode` IS NULL;


-- ---------------------------------------------------------------------
-- 二、删掉零引用的询盘明细表
--
-- ⚠ 跑之前确认它确实是空的。不空说明有人在本脚本写成之后接上了代码，
--   那就先把这一段注释掉、回来看 git 历史搞清楚状况，再决定。
--
--   SELECT COUNT(*) FROM u_s_inquiry_item;   -- 期望 0
--
-- 代码侧的验收（应零结果）：
--
--   grep -rn "inquiry_item\|InquiryItem" station-console/src
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `u_s_inquiry_item`;


-- ---------------------------------------------------------------------
-- 三、验一遍
-- ---------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `u_s_order`)                                    AS `订单数`,
  (SELECT COUNT(*) FROM `u_s_orderitem`)                                AS `明细数_应与订单对得上`,
  (SELECT COUNT(*) FROM `u_s_orderitem` i
     LEFT JOIN `u_s_order` o ON o.`ordercode` = i.`ordercode`
    WHERE o.`ordercode` IS NULL)                                        AS `孤儿明细_应为0`,
  (SELECT COUNT(*) FROM information_schema.`TABLES`
    WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'u_s_inquiry_item')
                                                                        AS `明细表还在吗_应为0`,
  (SELECT COUNT(*) FROM information_schema.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'u_s_inquiry'
      AND `COLUMN_NAME` IN ('inquirytype','inquiryref','lang','currency','shippingpref',
                            'paymentpref','shipcountry','shipline1','shipline2',
                            'shipcity','shipregion','shippostal','note'))
                                                                        AS `RFQ留位置列_应为13`;
