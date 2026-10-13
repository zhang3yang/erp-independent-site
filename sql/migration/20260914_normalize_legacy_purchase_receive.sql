-- 清理 2026 年 8 月遗留的采购质检状态（2026-09-14）。可重复执行。
--
-- 当前采购发货流程只使用「待入库 / 已入库」。本脚本只处理已经逐张对账的
-- 商家 sp708xm02q 的九张旧单，不修改采购订单 receivedqty 与采购订单状态。
-- 执行前应先备份 u_p_receive_202608 与 u_p_receivedetail_202608 的目标记录。

SET NAMES utf8mb4;
START TRANSACTION;

-- 旧终态已经完成质检或退货处理。以旧 goodqty/badqty 作为历史实收与短装口径，
-- LEGACY-* 只作为「该行已结束」的标记，不冒充真实库存明细号。
UPDATE sdsorder.u_p_receivedetail_202608 d
JOIN sdsorder.u_p_receive_202608 m
  ON m.shop_code = d.shop_code AND m.receivecode = d.receivecode
SET d.actualqty = COALESCE(d.actualqty, d.goodqty, 0),
    d.inventorydetailcode = COALESCE(d.inventorydetailcode, CONCAT('LEGACY-', d.receivecode)),
    d.updatetime = SYSDATE()
WHERE m.shop_code = 'sp708xm02q'
  AND m.receivecode IN (
    'DH2049614906', 'DH2049615204', 'DH2049615406', 'DH2049615409',
    'DH2049615504', 'DH2049615609', 'DH20496178023'
  )
  AND m.status IN ('已质检', '部分退货');

-- 这张「质检中」发货单所属采购单早已完成，不能再进入采购入库。
-- 按实际到货 0、全额短装关闭，保留记录但不增加库存或采购已收数量。
UPDATE sdsorder.u_p_receivedetail_202608 d
JOIN sdsorder.u_p_receive_202608 m
  ON m.shop_code = d.shop_code AND m.receivecode = d.receivecode
SET d.actualqty = 0,
    d.goodqty = 0,
    d.badqty = d.qty,
    d.inventorydetailcode = COALESCE(d.inventorydetailcode, CONCAT('LEGACY-', d.receivecode)),
    d.updatetime = SYSDATE()
WHERE m.shop_code = 'sp708xm02q'
  AND m.receivecode = 'DH2049626602'
  AND m.status IN ('待质检', '质检中');

-- 七张旧终态和一张已完成采购单上的遗留中间态，归一成当前终态。
UPDATE sdsorder.u_p_receive_202608
SET status = '已入库',
    updateuser = 'system-migration',
    updatetime = SYSDATE()
WHERE shop_code = 'sp708xm02q'
  AND receivecode IN (
    'DH2049614906', 'DH2049615204', 'DH2049615406', 'DH2049615409',
    'DH2049615504', 'DH2049615609', 'DH20496178023', 'DH2049626602'
  )
  AND status IN ('待质检', '质检中', '已质检', '部分退货');

-- 这张发货单的采购单仍少收 1 件，转成当前流程的「待入库」。
UPDATE sdsorder.u_p_receive_202608
SET status = '待入库',
    updateuser = 'system-migration',
    updatetime = SYSDATE()
WHERE shop_code = 'sp708xm02q'
  AND receivecode = 'DH20496154035'
  AND status IN ('待质检', '质检中');

COMMIT;

-- 验证：第一项应为 0；第二项应只列出当前两种状态。
SELECT COUNT(*) AS legacy_status_count
FROM sdsorder.u_p_receive_202608
WHERE shop_code = 'sp708xm02q'
  AND status IN ('待质检', '质检中', '已质检', '部分退货');

SELECT status, COUNT(*) AS receive_count
FROM sdsorder.u_p_receive_202608
WHERE shop_code = 'sp708xm02q'
GROUP BY status
ORDER BY status;
