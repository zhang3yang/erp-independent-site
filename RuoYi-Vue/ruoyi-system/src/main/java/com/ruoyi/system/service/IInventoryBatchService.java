package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryBatchAllocation;

/**
 * 批次信息Service接口
 */
public interface IInventoryBatchService
{
    /**
     * 查询批次信息
     */
    public InventoryBatch selectBatchByKey(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state);

    /**
     * 查询批次列表
     */
    public List<InventoryBatch> selectBatchList(InventoryBatch batch);

    /**
     * 查询可用批次列表
     */
    public List<InventoryBatch> selectAvailableBatchList(InventoryBatch batch);

    /**
     * 新增批次信息
     */
    public int insertBatch(InventoryBatch batch);

    /**
     * 修改批次信息
     */
    public int updateBatch(InventoryBatch batch);

    /**
     * 改这一批货的存放货位，别的字段一个都不碰。
     *
     * <p>批次的数量、成本、状态一律由过账驱动，这是唯一允许手工改的字段：
     * 货位是纯管理信息，不参与任何数量、成本、账务计算，库管员挪了货就该能改。
     * 传空表示清掉货位——货挪走了还没定新位置，留空比留个错的强。</p>
     */
    public int updateBatchPosition(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state, String position);

    /**
     * 扣减批次剩余数量
     */
    public int deductBatchQty(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state, BigDecimal deductQty);

    /**
     * 删除批次信息
     */
    public int deleteBatchByKey(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state);

    /**
     * 生成批次号
     */
    public String generateBatchCode(String shopCode);

    /** 查询指定商品、仓库、状态下的可用批次 */
    public List<InventoryBatch> selectAvailableBatchByContext(String shopCode, String goodsCode, String wareCode,
            StockState state);

    /**
     * 按采购日期、创建时间、批次号顺序锁定并扣减 FIFO 批次。
     */
    /**
     * 按 FIFO 扣减批次。
     *
     * @param orderDetailCode 这批货发给哪条销售订单明细：定制件的专属批次只认这一条，
     *        并且会被优先扣掉；传 null 表示非订单出库，只能动没有专属归属的批次。
     */
    public List<InventoryBatchAllocation> deductFifoBatches(String shopCode, String goodsCode,
            String wareCode, StockState state, BigDecimal deductQty, String orderDetailCode);

    /**
     * 把一批货绑给某条销售订单明细，标成「专门为这张单做的」。
     *
     * <p>绑过之后别的订单出库挑不到它，只有这条明细能发。已经绑给别人时抛异常——
     * 那意味着两张工单的产出撞进了同一个批次号，静默跳过会让后一张的货被前一张发走。</p>
     */
    public void bindOwner(String shopCode, String batchCode, String goodsCode, String wareCode,
            StockState state, String ownerOrderCode, String ownerDetailCode);

    /** 松开某条订单明细占住的全部专属批次，退回通用库存。返回释放的批次数。 */
    public int releaseOwner(String shopCode, String ownerDetailCode);

    /** 这条订单明细名下的专属批次。 */
    public List<InventoryBatch> selectBatchesByOwner(String shopCode, String ownerDetailCode);

    /**
     * 这个商品在这一仓有没有被谁独占的可用批次。
     *
     * <p>出库审核用它决定要不要强制走批次扣减：有专属批次就说明有人为某张订单专门做过货，
     * 不按批次发就拦不住别人把它拿走。</p>
     */
    public boolean hasOwnedBatch(String shopCode, String goodsCode, String wareCode, StockState state);
}
