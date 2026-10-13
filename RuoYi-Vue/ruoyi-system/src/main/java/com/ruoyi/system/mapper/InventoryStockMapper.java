package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.enums.StockState;

/**
 * 库存Mapper接口
 *
 * <p>库存行的主键是「商家 + 商品 + 仓库 + 三维状态」，三维状态统一用
 * {@link StockState} 传递，避免每个方法都拖着三个散参数。</p>
 */
public interface InventoryStockMapper
{
    /**
     * 查询库存记录（按复合主键）
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @return 库存记录
     */
    public InventoryStock selectStockByKey(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state);

    /** 审核库存业务时锁定库存汇总行。 */
    public InventoryStock selectStockByKeyForUpdate(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state);

    /**
     * 查询库存列表
     *
     * @param stock 查询条件
     * @return 库存集合
     */
    public List<InventoryStock> selectStockList(InventoryStock stock);

    /**
     * 新增库存记录
     *
     * @param stock 库存信息
     * @return 结果
     */
    public int insertStock(InventoryStock stock);

    /**
     * 修改库存记录
     *
     * @param stock 库存信息
     * @return 结果
     */
    public int updateStock(InventoryStock stock);

    /**
     * 删除库存记录
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @return 结果
     */
    public int deleteStockByKey(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state);

    /**
     * 批量删除库存记录
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码数组
     * @return 结果
     */
    public int deleteStockByGoodsCodes(@Param("shopCode") String shopCode,
            @Param("goodsCodes") String[] goodsCodes);

    /**
     * 调整库存数量（带乐观锁）
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param adjustQty 调整数量（正数入库，负数出库）
     * @param costAmount 调整金额
     * @param version 当前版本号
     * @return 结果（0表示版本冲突）
     */
    public int adjustStockQty(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustQty") BigDecimal adjustQty,
            @Param("costAmount") BigDecimal costAmount,
            @Param("version") Integer version);

    /**
     * 调整锁定数量（带乐观锁）
     * 用于出库单创建时锁定库存、删除出库单时释放锁定
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param adjustLockedQty 锁定数量调整值（正数锁定，负数释放）
     * @param version 当前版本号
     * @return 结果（0表示版本冲突或可用库存不足）
     */
    public int adjustStockLockedQty(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustLockedQty") BigDecimal adjustLockedQty,
            @Param("version") Integer version);

    /** Redis 分布式锁保护下调整锁定数量，不再依赖 version。 */
    public int adjustStockLockedQtyNoVersion(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustLockedQty") BigDecimal adjustLockedQty);

    /** Redis 分布式锁保护下同步扣减物理库存并释放对应锁定数量。 */
    public int adjustStockQtyWithUnlockNoVersion(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustQty") BigDecimal adjustQty,
            @Param("adjustLockedQty") BigDecimal adjustLockedQty,
            @Param("costAmount") BigDecimal costAmount);

    /** Redis 分布式锁保护下调整物理库存，不再依赖 version。 */
    public int adjustStockQtyNoVersion(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustQty") BigDecimal adjustQty,
            @Param("costAmount") BigDecimal costAmount);

    /**
     * 出库审核时同步减少物理库存和锁定数量（带乐观锁）
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param adjustQty 物理库存调整值（负数）
     * @param adjustLockedQty 锁定数量调整值（负数，释放锁定）
     * @param costAmount 调整金额
     * @param version 当前版本号
     * @return 结果（0表示版本冲突）
     */
    public int adjustStockQtyWithUnlock(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode,
            @Param("state") StockState state,
            @Param("adjustQty") BigDecimal adjustQty,
            @Param("adjustLockedQty") BigDecimal adjustLockedQty,
            @Param("costAmount") BigDecimal costAmount,
            @Param("version") Integer version);
}
