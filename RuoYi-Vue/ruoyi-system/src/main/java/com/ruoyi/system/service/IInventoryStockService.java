package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryStock;

/**
 * 库存Service接口
 */
public interface IInventoryStockService
{
    /**
     * 查询库存记录
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @return 库存记录
     */
    public InventoryStock selectStockByKey(String shopCode, String goodsCode, String wareCode, StockState state);

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
     * 批量删除库存记录
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码数组
     * @return 结果
     */
    public int deleteStockByGoodsCodes(String shopCode, String[] goodsCodes);

    /**
     * 调整库存数量
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param adjustQty 调整数量
     * @param costAmount 调整金额
     * @return 结果
     */
    public int adjustStockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal adjustQty, BigDecimal costAmount);

    /**
     * 锁定库存数量（出库单创建时调用）
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param lockQty 要锁定的数量
     * @return 结果
     */
    public int lockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal lockQty);

    /**
     * 释放锁定数量（出库单删除时调用）
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param wareCode 仓库代码
     * @param state 三维库存状态
     * @param unlockQty 要释放的数量
     * @return 结果
     */
    public int unlockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal unlockQty);
}
