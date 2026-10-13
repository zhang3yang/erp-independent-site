package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryLedger;

/**
 * 库存流水Mapper接口
 */
public interface InventoryLedgerMapper
{
    /**
     * 查询库存流水
     */
    public InventoryLedger selectLedgerByCode(@Param("shopCode") String shopCode,
            @Param("ledgerCode") String ledgerCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 查询库存流水列表（单月）
     */
    public List<InventoryLedger> selectLedgerList(@Param("query") InventoryLedger ledger);

    /**
     * 查询库存流水列表（跨月）。
     *
     * <p>命中的月表 union all 起来一次查完。逐月查再拼会和 PageHelper 打架：
     * 它只作用在第一条 SQL 上，每个月都会各自返回第一页。</p>
     *
     * @param ledger   查询条件，其中的 tableSuffix 不参与
     * @param suffixes 要扫的月表后缀，必须已校验且确实存在
     */
    public List<InventoryLedger> selectLedgerListAcrossMonths(@Param("query") InventoryLedger ledger,
            @Param("suffixes") List<String> suffixes);

    /**
     * 新增库存流水
     */
    public int insertLedger(InventoryLedger ledger);

    /**
     * 批量新增库存流水
     */
    public int batchInsertLedger(@Param("ledgers") List<InventoryLedger> ledgers,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 查询流水数量
     */
    public int selectLedgerCount(InventoryLedger ledger);

    /**
     * 指定发货批次出库时实际动用过的批次号，按商品与仓库聚合。
     *
     * <p>批次商品走 FIFO 时，批次是过账阶段才定的，出库单明细上的 batchcode 是空的，
     * 只有流水记录了真正扣的是哪一批。退货要退回原批次，候选就从这里来。</p>
     */
    public List<com.ruoyi.system.domain.vo.SalesReturnBatchOption> selectShippedBatches(
            @Param("shopCode") String shopCode, @Param("shipCodes") List<String> shipCodes,
            @Param("tableSuffix") String tableSuffix);

    /** 全部流水月表后缀，供跨月汇总使用 */
    public List<String> selectLedgerTableSuffixes();
}
