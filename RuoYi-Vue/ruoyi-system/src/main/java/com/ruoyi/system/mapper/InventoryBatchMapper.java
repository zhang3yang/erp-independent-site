package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.enums.StockState;

/**
 * 批次信息Mapper接口
 */
public interface InventoryBatchMapper
{
    /**
     * 查询批次信息
     */
    public InventoryBatch selectBatchByKey(@Param("shopCode") String shopCode,
            @Param("batchCode") String batchCode, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state);

    /**
     * 查询批次列表
     */
    public List<InventoryBatch> selectBatchList(InventoryBatch batch);

    /**
     * 查询可用批次列表（剩余数量>0且状态为active）
     */
    public List<InventoryBatch> selectAvailableBatchList(InventoryBatch batch);

    /**
     * 新增批次信息
     */
    public int insertBatch(InventoryBatch batch);

    /** 原子增加同一批次库存维度的数量和金额。 */
    public int addBatchQty(InventoryBatch batch);

    /**
     * 修改批次信息
     */
    public int updateBatch(InventoryBatch batch);

    /**
     * 只改这一批货的存放货位，别的字段一个都不碰。
     *
     * <p>批次的数量、成本、状态一律由过账驱动，这是唯一的例外：货位是纯管理信息，
     * 不参与任何数量、成本、账务计算，库管员挪了货就该能改。</p>
     */
    public int updateBatchPosition(@Param("shopCode") String shopCode, @Param("batchCode") String batchCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("state") StockState state, @Param("position") String position);

    /**
     * 扣减批次剩余数量
     */
    public int deductBatchQty(@Param("shopCode") String shopCode,
            @Param("batchCode") String batchCode, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state,
            @Param("deductQty") BigDecimal deductQty);

    /**
     * 成本调整：只改批次单价和累计金额，数量一格不动。
     * 只由成本调整单过账调用，见 InventoryDocumentPostingService#executeCostAdjust。
     */
    public int adjustBatchCost(@Param("shopCode") String shopCode,
            @Param("batchCode") String batchCode, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state,
            @Param("costPrice") BigDecimal costPrice, @Param("purchaseAmount") BigDecimal purchaseAmount);

    /**
     * 删除批次信息
     */
    public int deleteBatchByKey(@Param("shopCode") String shopCode,
            @Param("batchCode") String batchCode, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state);

    /**
     * 查询批次列表（根据商品和仓库）
     */
    public List<InventoryBatch> selectBatchByGoodsAndWare(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode);

    /** 查询指定商品、仓库、状态下的可用批次 */
    public List<InventoryBatch> selectAvailableBatchByContext(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("state") StockState state);

    /**
     * 按 FIFO 顺序查询并锁定指定库存维度下的可用批次。
     *
     * @param orderDetailCode 这批货要发给哪条销售订单明细。传了就能用它自己的专属批次
     *        （并且排在最前），别人的专属批次一律排除；传 null 表示不是订单出库，
     *        只能动没有专属归属的批次。
     */
    public List<InventoryBatch> selectAvailableBatchForUpdate(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("state") StockState state, @Param("orderDetailCode") String orderDetailCode);

    /** 把一批货绑给某条销售订单明细。已经绑给别人时更新 0 行。 */
    public int bindBatchOwner(@Param("shopCode") String shopCode, @Param("batchCode") String batchCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("state") StockState state, @Param("ownerOrderCode") String ownerOrderCode,
            @Param("ownerDetailCode") String ownerDetailCode);

    /** 松开某条订单明细占住的全部批次，返回释放的批次数。 */
    public int releaseBatchOwnerByDetail(@Param("shopCode") String shopCode,
            @Param("ownerDetailCode") String ownerDetailCode);

    /** 这条订单明细名下的专属批次。 */
    public List<InventoryBatch> selectBatchesByOwnerDetail(@Param("shopCode") String shopCode,
            @Param("ownerDetailCode") String ownerDetailCode);

    /** 这个商品在这一仓有几个被独占的可用批次。 */
    public int countOwnedBatches(@Param("shopCode") String shopCode, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state);
}
