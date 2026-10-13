package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;

/** 单据库存锁协调器，集中处理待审单据锁定和审核过账资源锁。 */
@Component
public class InventoryDocumentLockCoordinator
{
    @Autowired
    private DocTypeConfigMapper docTypeConfigMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryStockRedisLockService stockRedisLockService;

    /** 新增或修改待审核出库/调拨单时锁定可用库存。 */
    public void lockPendingOutboundOrTransfer(DocumentMain document)
    {
        adjustPendingLockedQty(document, document.getDetails(), BigDecimal.ONE,
                "可用库存不足，无法锁定库存");
    }

    /** 审核通过前重新获取待审核单据涉及的库存锁。 */
    public void acquirePendingOutboundOrTransferLocks(DocumentMain document)
    {
        lockForPendingQuantities(document, document.getDetails());
    }

    /** 修改或删除待审核单据时释放原有库存锁定量。 */
    public void unlockPendingOutboundOrTransfer(DocumentMain document, List<DocumentDetail> details)
    {
        adjustPendingLockedQty(document, details, BigDecimal.ONE.negate(),
                "库存锁定释放失败，请检查库存数据后重试");
    }

    /** 审核过账前锁定入库、出库或调拨涉及的库存行。 */
    public void lockStockOperationResources(DocumentMain document, String direction,
            List<DocumentDetail> details)
    {
        Map<String, InventoryStockRedisLockService.StockLockKey> keys = new LinkedHashMap<>();
        for (DocumentDetail detail : details)
        {
            String goodsCode = detail.getGoodsCode();
            if ("I".equals(direction))
            {
                addStockOperationKey(keys, document.getShopCode(), goodsCode,
                        document.getToWareCode(), document.getToState());
            }
            else if ("O".equals(direction) || "C".equals(direction))
            {
                // 成本调整不动数量，但改的是同一行库存的总成本，照样要和出入库互斥
                addStockOperationKey(keys, document.getShopCode(), goodsCode,
                        document.getFromWareCode(), document.getFromState());
            }
            else if ("N".equals(direction))
            {
                addStockOperationKey(keys, document.getShopCode(), goodsCode,
                        document.getFromWareCode(), document.getFromState());
                addStockOperationKey(keys, document.getShopCode(), goodsCode,
                        document.getToWareCode(), document.getToState());
            }
        }
        stockRedisLockService.lockForTransaction(document.getShopCode(), new ArrayList<>(keys.values()));
    }

    private void lockForPendingQuantities(DocumentMain document, List<DocumentDetail> details)
    {
        Map<String, BigDecimal> quantities = summarizeLockedQty(document, details);
        if (quantities.isEmpty())
        {
            return;
        }
        List<InventoryStockRedisLockService.StockLockKey> keys = new ArrayList<>();
        for (String key : quantities.keySet())
        {
            keys.add(toStockLockKey(key));
        }
        stockRedisLockService.lockForTransaction(document.getShopCode(), keys);
    }

    private void adjustPendingLockedQty(DocumentMain document, List<DocumentDetail> details,
            BigDecimal multiplier, String failureMessage)
    {
        Map<String, BigDecimal> quantities = summarizeLockedQty(document, details);
        if (quantities.isEmpty())
        {
            return;
        }
        List<InventoryStockRedisLockService.StockLockKey> keys = new ArrayList<>();
        for (String key : quantities.keySet())
        {
            keys.add(toStockLockKey(key));
        }
        stockRedisLockService.lockForTransaction(document.getShopCode(), keys);
        for (Map.Entry<String, BigDecimal> entry : quantities.entrySet())
        {
            String[] parts = splitKey(entry.getKey());
            int rows = stockMapper.adjustStockLockedQtyNoVersion(parts[0], parts[1], parts[2],
                    stateOfKey(parts), entry.getValue().multiply(multiplier));
            if (rows == 0)
            {
                throw new ServiceException(failureMessage);
            }
        }
    }

    private Map<String, BigDecimal> summarizeLockedQty(DocumentMain document, List<DocumentDetail> details)
    {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        if (!isOutboundOrTransfer(document) || details == null)
        {
            return quantities;
        }
        String wareCode = document.getFromWareCode();
        StockState state = document.getFromState();
        for (DocumentDetail detail : details)
        {
            if (detail == null || detail.getQty() == null
                    || detail.getQty().compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            String key = document.getShopCode() + "|" + detail.getGoodsCode() + "|"
                    + wareCode + "|" + state.getQualityFlag() + "|" + state.getOnhandFlag();
            quantities.merge(key, detail.getQty(), BigDecimal::add);
        }
        return quantities;
    }

    private boolean isOutboundOrTransfer(DocumentMain document)
    {
        return locksPendingQty(document.getDocType());
    }

    /**
     * 该单据类型在待审核阶段是否预占库存（写 u_m_stock.lockedqty）。
     *
     * <p>只有销售出库和仓库调拨会预占：销售订单需要提前锁货，调拨在途也要占用。
     * 采购退货 RO、生产领料 MO、其他出库 OO、借出与维修等状态调拨都不预占，
     * 审核时直接扣减物理库存。</p>
     *
     * <p>过账时必须据此选择库存更新语句：预占过的用「扣减并释放锁定」，
     * 未预占的用「仅扣减」。用错会让 lockedqty 减成负数，
     * 被 SQL 的 {@code lockedqty + adjustLockedQty >= 0} 条件挡下，
     * 更新 0 行并抛出「库存数据已被其他人修改」这种与实际原因无关的错误。</p>
     */
    public boolean locksPendingQty(String docType)
    {
        if (docType == null)
        {
            return false;
        }
        // 这里的名单决定"哪些类型在建单时预占 lockedqty"，必须与
        // InventoryDocumentPostingService 中"哪些类型在审核时释放 lockedqty"保持一致，
        // 新增出库类型时两处要一起改，否则过账会因锁定量为负而更新 0 行。
        DocTypeConfig config = docTypeConfigMapper.selectDocTypeByCode(docType);
        return config != null
                && (InventoryDocumentTypes.SALES_OUTBOUND.equals(docType)
                    || InventoryDocumentTypes.WAREHOUSE_TRANSFER.equals(docType));
    }

    private InventoryStockRedisLockService.StockLockKey toStockLockKey(String key)
    {
        String[] parts = splitKey(key);
        return new InventoryStockRedisLockService.StockLockKey(parts[0], parts[1], parts[2],
                stateOfKey(parts));
    }

    private String[] splitKey(String key)
    {
        return key.split("\\|", -1);
    }

    /** 从 shop|goods|ware|良品|在库 形式的聚合键还原二维状态 */
    private StockState stateOfKey(String[] parts)
    {
        return StockState.of(Integer.valueOf(parts[3]), Integer.valueOf(parts[4]));
    }

    private void addStockOperationKey(Map<String, InventoryStockRedisLockService.StockLockKey> keys,
            String shopCode, String goodsCode, String wareCode, StockState state)
    {
        String key = shopCode + "|" + goodsCode + "|" + wareCode + "|" + state;
        keys.put(key, new InventoryStockRedisLockService.StockLockKey(shopCode, goodsCode, wareCode, state));
    }
}
