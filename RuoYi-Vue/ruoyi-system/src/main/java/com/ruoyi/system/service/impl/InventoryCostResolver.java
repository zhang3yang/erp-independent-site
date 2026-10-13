package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.utils.InventoryCostMath;

/**
 * 库存成本解析器。
 *
 * 调拨属于库存维度之间的成本搬移，成本必须来自来源库存或实际批次，
 * 不能使用客户端传入的任意价格。
 */
@Component
public class InventoryCostResolver
{
    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private IInventorySerialService serialService;

    /**
     * 以库存总成本/现存数量为权威值解析移动平均成本。
     * 历史数据只有平均成本可用时，再回退到平均成本字段。
     */
    public BigDecimal resolveStockAverageCost(InventoryStock stock)
    {
        if (stock == null)
        {
            return BigDecimal.ZERO;
        }
        BigDecimal physicalQty = valueOrZero(stock.getPhysicalQty());
        BigDecimal totalCostAmount = valueOrZero(stock.getTotalCostAmount());
        if (physicalQty.compareTo(BigDecimal.ZERO) > 0
                && totalCostAmount.compareTo(BigDecimal.ZERO) > 0)
        {
            return totalCostAmount.divide(physicalQty, 6, RoundingMode.HALF_UP);
        }
        BigDecimal avgCostPrice = valueOrZero(stock.getAvgCostPrice());
        return avgCostPrice.compareTo(BigDecimal.ZERO) > 0 ? avgCostPrice : BigDecimal.ZERO;
    }

    /**
     * 解析出库/调拨成本。成本算法只由 costCalculation 决定，batchStrategy
     * 仅负责批次或序列号实物追踪。
     */
    public BigDecimal resolveOutboundCostPrice(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, String batchCode, List<String> serialNumbers,
            BigDecimal qty, InventoryStock sourceStock)
    {
        validateQty(qty, "出库");
        String costCalculation = costCalculation(goods);
        if ("moving_avg".equals(costCalculation) || "fifo".equals(costCalculation))
        {
            // FIFO 创建单据时仅显示移动平均估算，审核时再按实际批次重算。
            return resolveStockAverageCost(sourceStock);
        }
        if (!"individual".equals(costCalculation))
        {
            throw invalidCostCalculation(goods, costCalculation);
        }
        return resolveTrackedCostPrice(shopCode, goods, wareCode, state, batchCode,
                serialNumbers, qty);
    }

    public BigDecimal resolveTransferCostPrice(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, String batchCode, List<String> serialNumbers,
            BigDecimal qty, InventoryStock sourceStock)
    {
        validateQty(qty, "调拨");
        String costCalculation = costCalculation(goods);
        if ("moving_avg".equals(costCalculation))
        {
            return resolveStockAverageCost(sourceStock);
        }
        if (!"fifo".equals(costCalculation) && !"individual".equals(costCalculation))
        {
            throw invalidCostCalculation(goods, costCalculation);
        }
        // FIFO/个别计价调拨不重新消耗批次，只搬移用户实际选择的批次或序列号成本。
        return resolveTrackedCostPrice(shopCode, goods, wareCode, state, batchCode,
                serialNumbers, qty);
    }

    private BigDecimal resolveTrackedCostPrice(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, String batchCode, List<String> serialNumbers,
            BigDecimal qty)
    {
        String trackingStrategy = goods == null || goods.getBatchStrategy() == null
                ? "none" : goods.getBatchStrategy().trim().toLowerCase();

        if ("batch".equals(trackingStrategy))
        {
            if (batchCode == null || batchCode.trim().isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'必须填写调拨批次");
            }
            return resolveBatchCostPrice(shopCode, goods, wareCode, state, batchCode.trim());
        }
        if ("serial".equals(trackingStrategy))
        {
            if (serialNumbers == null || serialNumbers.isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'必须选择调拨序列号");
            }
            int expectedCount;
            try
            {
                expectedCount = qty.intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new ServiceException("序列号商品'" + goods.getGoodsName() + "'的调拨数量必须是整数");
            }
            if (serialNumbers.size() != expectedCount)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'的调拨数量与序列号数量不一致");
            }

            Map<String, BigDecimal> batchCostCache = new HashMap<>();
            BigDecimal totalCostAmount = BigDecimal.ZERO;
            for (String serialNo : serialNumbers)
            {
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo,
                        goods.getGoodsCode(), wareCode, state);
                if (serial == null || !"instock".equals(serial.getStatus()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'已不属于调出商品、仓库或库存状态");
                }
                if (serial.getCostPrice() != null)
                {
                    totalCostAmount = totalCostAmount.add(validateSerialCost(serialNo, serial.getCostPrice()));
                    continue;
                }
                if (serial.getBatchCode() == null || serial.getBatchCode().isEmpty())
                {
                    throw new ServiceException("序列号'" + serialNo + "'未关联批次，不能计算调拨成本");
                }
                BigDecimal batchCost = batchCostCache.get(serial.getBatchCode());
                if (batchCost == null)
                {
                    batchCost = resolveBatchCostPrice(shopCode, goods, wareCode, state,
                            serial.getBatchCode());
                    batchCostCache.put(serial.getBatchCode(), batchCost);
                }
                totalCostAmount = totalCostAmount.add(batchCost);
            }
            return totalCostAmount.divide(qty, 6, RoundingMode.HALF_UP);
        }
        throw new ServiceException("商品'" + goods.getGoodsName() + "'使用个别计价时必须启用批次或序列号管理");
    }

    /**
     * 解析单个序列号的个别成本。新数据以序列号成本为准，只有历史空值才回退批次成本。
     */
    public BigDecimal resolveSerialCostPrice(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, InventorySerial serial)
    {
        if (serial == null)
        {
            throw new ServiceException("序列号不存在，不能计算成本");
        }
        if (serial.getCostPrice() != null)
        {
            return validateSerialCost(serial.getSerialNo(), serial.getCostPrice());
        }
        if (serial.getBatchCode() == null || serial.getBatchCode().trim().isEmpty())
        {
            throw new ServiceException("序列号'" + serial.getSerialNo() + "'未关联批次，不能计算成本");
        }
        return resolveBatchCostPrice(shopCode, goods, wareCode, state,
                serial.getBatchCode().trim());
    }

    private BigDecimal validateSerialCost(String serialNo, BigDecimal costPrice)
    {
        if (costPrice.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("序列号'" + serialNo + "'的成本单价不能小于0");
        }
        return InventoryCostMath.costPrice(costPrice);
    }

    private void validateQty(BigDecimal qty, String businessName)
    {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException(businessName + "数量必须大于0");
        }
    }

    private String costCalculation(InventoryGoods goods)
    {
        return goods == null || goods.getCostCalculation() == null
                ? "moving_avg" : goods.getCostCalculation().trim().toLowerCase();
    }

    private ServiceException invalidCostCalculation(InventoryGoods goods, String costCalculation)
    {
        String goodsName = goods == null ? "未知商品" : goods.getGoodsName();
        return new ServiceException("商品'" + goodsName + "'的成本计算方法无效：" + costCalculation);
    }

    public BigDecimal resolveBatchCostPrice(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, String batchCode)
    {
        InventoryBatch batch = selectSourceBatch(shopCode, goods, wareCode, state, batchCode);
        return resolveBatchCost(batch);
    }

    private InventoryBatch selectSourceBatch(String shopCode, InventoryGoods goods,
            String wareCode, StockState state, String batchCode)
    {
        InventoryBatch batch = batchService.selectBatchByKey(shopCode, batchCode,
                goods.getGoodsCode(), wareCode, state);
        if (batch == null || !"active".equals(batch.getStatus()))
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的来源批次'"
                    + batchCode + "'不存在或不可用");
        }
        return batch;
    }

    private BigDecimal resolveBatchCost(InventoryBatch batch)
    {
        return InventoryCostMath.batchCostPrice(batch);
    }

    private BigDecimal valueOrZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }
}
