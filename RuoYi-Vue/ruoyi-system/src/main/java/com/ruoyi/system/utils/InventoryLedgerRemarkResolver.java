package com.ruoyi.system.utils;

import java.math.BigDecimal;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.StockState;

/**
 * 将库存转移的技术方向转换为客户可理解的业务流水说明。
 */
public final class InventoryLedgerRemarkResolver
{
    private InventoryLedgerRemarkResolver()
    {
    }

    /**
     * 生成流水备注。
     *
     * @param docType 单据类型
     * @param state 这一笔流水所在的三维库存状态
     * @param changeQty 变动数量，正数为增加
     * @return 备注，无对应文案时返回 null 由调用方兜底
     */
    public static String resolve(String docType, StockState state, BigDecimal changeQty)
    {
        if (docType == null || changeQty == null || changeQty.compareTo(BigDecimal.ZERO) == 0)
        {
            return null;
        }
        boolean inbound = changeQty.compareTo(BigDecimal.ZERO) > 0;

        if (InventoryDocumentTypes.WAREHOUSE_TRANSFER.equals(docType))
        {
            return inbound ? "调拨入库" : "调拨出库";
        }
        if (InventoryDocumentTypes.LOAN.equals(docType))
        {
            return inbound ? "借出中的库存增加" : "借出扣减";
        }
        if (InventoryDocumentTypes.RETURN.equals(docType))
        {
            return inbound ? "归还入库" : "借出中的库存扣减";
        }
        if (InventoryDocumentTypes.DAMAGE.equals(docType)
                || InventoryDocumentTypes.REPAIR_OUT.equals(docType)
                || InventoryDocumentTypes.REPAIR_IN.equals(docType))
        {
            String stateLabel = state == null ? "对应状态" : state.getLabel();
            return stateLabel + "库存" + (inbound ? "增加" : "扣减");
        }
        return null;
    }
}
