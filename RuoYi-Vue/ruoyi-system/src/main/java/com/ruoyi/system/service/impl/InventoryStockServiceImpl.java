package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.service.IInventoryStockService;

/**
 * 库存Service业务层处理
 */
@Service
public class InventoryStockServiceImpl implements IInventoryStockService
{
    private static final Logger log = LoggerFactory.getLogger(InventoryStockServiceImpl.class);

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryStockRedisLockService stockRedisLockService;

    /**
     * 查询库存记录
     */
    @Override
    public InventoryStock selectStockByKey(String shopCode, String goodsCode, String wareCode, StockState state)
    {
        return stockMapper.selectStockByKey(shopCode, goodsCode, wareCode, state);
    }

    /**
     * 查询库存列表
     */
    @Override
    public List<InventoryStock> selectStockList(InventoryStock stock)
    {
        return stockMapper.selectStockList(stock);
    }

    /**
     * 新增库存记录
     */
    @Override
    public int insertStock(InventoryStock stock)
    {
        // 校验是否已存在
        InventoryStock existing = stockMapper.selectStockByKey(
                stock.getShopCode(), stock.getGoodsCode(), stock.getWareCode(), stock.getStockState());
        if (existing != null)
        {
            throw new ServiceException("该库存记录已存在");
        }

        // 设置默认值
        if (stock.getPhysicalQty() == null)
        {
            stock.setPhysicalQty(BigDecimal.ZERO);
        }
        if (stock.getLockedQty() == null)
        {
            stock.setLockedQty(BigDecimal.ZERO);
        }
        if (stock.getTotalCostAmount() == null)
        {
            stock.setTotalCostAmount(BigDecimal.ZERO);
        }
        if (stock.getAvgCostPrice() == null)
        {
            stock.setAvgCostPrice(BigDecimal.ZERO);
        }

        return stockMapper.insertStock(stock);
    }

    /**
     * 修改库存记录
     */
    @Override
    public int updateStock(InventoryStock stock)
    {
        // 校验是否存在
        InventoryStock existing = stockMapper.selectStockByKey(
                stock.getShopCode(), stock.getGoodsCode(), stock.getWareCode(), stock.getStockState());
        if (existing == null)
        {
            throw new ServiceException("库存记录不存在");
        }

        return stockMapper.updateStock(stock);
    }

    /**
     * 批量删除库存记录
     */
    @Override
    public int deleteStockByGoodsCodes(String shopCode, String[] goodsCodes)
    {
        return stockMapper.deleteStockByGoodsCodes(shopCode, goodsCodes);
    }

    /**
     * 调整库存数量（带乐观锁）
     */
    @Override
    @Transactional
    public int adjustStockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal adjustQty, BigDecimal costAmount)
    {
        InventoryStock stock = stockMapper.selectStockByKey(shopCode, goodsCode, wareCode, state);
        if (stock == null)
        {
            throw new ServiceException("库存记录不存在");
        }
        stockRedisLockService.lockForTransaction(shopCode, java.util.Collections.singletonList(
                new InventoryStockRedisLockService.StockLockKey(shopCode, goodsCode, wareCode, state)));

        // 校验出库时库存是否充足
        if (adjustQty.compareTo(BigDecimal.ZERO) < 0)
        {
            BigDecimal afterQty = stock.getPhysicalQty().add(adjustQty);
            if (afterQty.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("库存不足，当前库存：" + stock.getPhysicalQty());
            }
        }

        // 校验调整后物理库存不能低于「待审核出库单锁定量」。这里必须和
        // InventoryStockMapper.xml 里 adjustStockQtyNoVersion 的 SQL 守卫同口径：
        // 前置校验放过、再被 SQL 挡下更新 0 行的话，用户拿到的是一句与真实原因无关的
        // 「库存数据已被其他人修改，请刷新后重试」。
        BigDecimal lockedQty = stock.getLockedQty() == null ? BigDecimal.ZERO : stock.getLockedQty();
        BigDecimal afterPhysicalQty = stock.getPhysicalQty().add(adjustQty);
        if (afterPhysicalQty.compareTo(lockedQty) < 0)
        {
            throw new ServiceException("调整后库存数量（" + afterPhysicalQty + "）不能低于待审核出库单的锁定数量（"
                    + lockedQty + "），请先处理那些出库单或减少调整量");
        }

        int rows = stockMapper.adjustStockQtyNoVersion(shopCode, goodsCode, wareCode, state,
                adjustQty, costAmount);
        if (rows == 0)
        {
            throw new ServiceException("库存数据已被其他人修改，请刷新后重试");
        }
        return rows;
    }

    /**
     * 锁定库存数量（出库单创建时调用）
     */
    @Override
    @Transactional
    public int lockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal lockQty)
    {
        if (lockQty == null || lockQty.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("锁定数量必须大于0");
        }

        InventoryStock stock = stockMapper.selectStockByKey(shopCode, goodsCode, wareCode, state);
        if (stock == null)
        {
            throw new ServiceException("库存记录不存在");
        }
        stockRedisLockService.lockForTransaction(shopCode, java.util.Collections.singletonList(
                new InventoryStockRedisLockService.StockLockKey(shopCode, goodsCode, wareCode, state)));

        int rows = stockMapper.adjustStockLockedQtyNoVersion(shopCode, goodsCode, wareCode, state,
                lockQty);
        if (rows == 0)
        {
            throw new ServiceException("可用库存不足或库存数据已被其他人修改，请刷新后重试");
        }
        return rows;
    }

    /**
     * 释放锁定数量（出库单删除时调用）
     */
    @Override
    @Transactional
    public int unlockQty(String shopCode, String goodsCode, String wareCode,
            StockState state, BigDecimal unlockQty)
    {
        if (unlockQty == null || unlockQty.compareTo(BigDecimal.ZERO) <= 0)
        {
            return 0;
        }

        InventoryStock stock = stockMapper.selectStockByKey(shopCode, goodsCode, wareCode, state);
        if (stock == null)
        {
            // 库存记录不存在，无需释放
            return 0;
        }
        stockRedisLockService.lockForTransaction(shopCode, java.util.Collections.singletonList(
                new InventoryStockRedisLockService.StockLockKey(shopCode, goodsCode, wareCode, state)));

        int rows = stockMapper.adjustStockLockedQtyNoVersion(shopCode, goodsCode, wareCode, state,
                unlockQty.negate());
        if (rows == 0)
        {
            // 版本冲突，记录日志但不抛异常（释放操作不应阻塞删除）
            log.warn("释放锁定数量时版本冲突，shopCode={}, goodsCode={}, wareCode={}", shopCode, goodsCode, wareCode);
        }
        return rows;
    }
}
