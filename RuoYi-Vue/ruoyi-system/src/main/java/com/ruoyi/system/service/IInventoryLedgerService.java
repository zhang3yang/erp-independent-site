package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.InventoryLedger;

/**
 * 库存流水Service接口
 */
public interface IInventoryLedgerService
{
    /**
     * 查询库存流水
     */
    public InventoryLedger selectLedgerByCode(String shopCode, String ledgerCode, String tableSuffix);

    /**
     * 查询库存流水列表
     */
    public List<InventoryLedger> selectLedgerList(InventoryLedger ledger);

    /**
     * 新增库存流水
     */
    public int insertLedger(InventoryLedger ledger);

    /**
     * 批量新增库存流水
     */
    public int batchInsertLedger(List<InventoryLedger> ledgers);

    /**
     * 生成流水号
     */
    public String generateLedgerCode();
}
