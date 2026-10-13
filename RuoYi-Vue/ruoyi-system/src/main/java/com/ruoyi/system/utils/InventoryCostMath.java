package com.ruoyi.system.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.ruoyi.system.domain.InventoryBatch;

/**
 * 库存成本金额的统一计算规则。
 *
 * 成本单价保留 6 位用于计算，成本金额按 2 位记账。所有库存业务都应通过
 * 这里计算，避免不同入口使用不同的舍入口径。
 */
public final class InventoryCostMath
{
    public static final int COST_PRICE_SCALE = 6;
    public static final int COST_AMOUNT_SCALE = 2;
    public static final int SALE_PRICE_SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private InventoryCostMath()
    {
    }

    public static BigDecimal zeroIfNull(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal costPrice(BigDecimal value)
    {
        return zeroIfNull(value).setScale(COST_PRICE_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal amount(BigDecimal price, BigDecimal qty)
    {
        return costPrice(price).multiply(zeroIfNull(qty)).setScale(COST_AMOUNT_SCALE, ROUNDING_MODE);
    }

    /**
     * 销售单价按 saleprice 列的 4 位精度取整。
     *
     * <p>销售出库的售价默认取入库成本，而成本单价保留 6 位，直接拿去当售价存库会被列
     * 精度截断，读回来就不再等于成本。审核时要靠“售价是否仍等于开单成本”判断业务员
     * 有没有改过价，所以带出和写入都必须先经过这里规约。</p>
     */
    public static BigDecimal salePrice(BigDecimal value)
    {
        return zeroIfNull(value).setScale(SALE_PRICE_SCALE, ROUNDING_MODE);
    }

    /** 销售金额 = 销售单价 × 数量，与成本金额同样按 2 位记账。 */
    public static BigDecimal saleAmount(BigDecimal price, BigDecimal qty)
    {
        return salePrice(price).multiply(zeroIfNull(qty)).setScale(COST_AMOUNT_SCALE, ROUNDING_MODE);
    }

    /**
     * 判断销售单价是否被业务员改过。
     *
     * <p>售价默认带出成本，仍等于成本就说明没人动过，可以跟随成本重算；不等则是业务员
     * 的定价，任何环节都不能覆盖。业务员恰好把价格改成与成本相同的值时会被当作未改价，
     * 这是不额外加改价标记列的代价，两种处理的结果此刻本来也相同。</p>
     */
    public static boolean isSalePriceOverridden(BigDecimal salePrice, BigDecimal costPrice)
    {
        return salePrice != null && salePrice.compareTo(salePrice(costPrice)) != 0;
    }

    public static BigDecimal average(BigDecimal amount, BigDecimal qty)
    {
        BigDecimal safeQty = zeroIfNull(qty);
        if (safeQty.compareTo(BigDecimal.ZERO) <= 0)
        {
            return BigDecimal.ZERO.setScale(COST_PRICE_SCALE, ROUNDING_MODE);
        }
        return zeroIfNull(amount).divide(safeQty, COST_PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * 来源库存数量归零时，本次业务吸收历史分次结转的分币尾差。
     */
    public static BigDecimal amountWithClosingRemainder(BigDecimal calculatedAmount,
            BigDecimal afterQty, BigDecimal currentTotalCostAmount)
    {
        BigDecimal amount = zeroIfNull(calculatedAmount).max(BigDecimal.ZERO);
        if (zeroIfNull(afterQty).compareTo(BigDecimal.ZERO) == 0)
        {
            amount = zeroIfNull(currentTotalCostAmount).max(BigDecimal.ZERO);
        }
        return amount.setScale(COST_AMOUNT_SCALE, ROUNDING_MODE);
    }

    /**
     * 批次当前单价是批次剩余库存的成本口径。同批次追加入库时由数据库按
     * “原剩余数量 × 原批次单价 + 本次入库金额”重新加权。历史数据只有累计
     * 金额可用时，再回退到累计金额/累计入库数量。
     */
    public static BigDecimal batchCostPrice(InventoryBatch batch)
    {
        if (batch == null)
        {
            return BigDecimal.ZERO.setScale(COST_PRICE_SCALE, ROUNDING_MODE);
        }
        BigDecimal initQty = zeroIfNull(batch.getInitQty());
        BigDecimal remainQty = zeroIfNull(batch.getRemainQty());
        BigDecimal purchaseAmount = zeroIfNull(batch.getPurchaseAmount());
        // 尚未发生出库的历史批次可以安全地用累计金额修复被“最新入库价”覆盖的单价。
        if (initQty.compareTo(BigDecimal.ZERO) > 0 && remainQty.compareTo(initQty) == 0
                && purchaseAmount.compareTo(BigDecimal.ZERO) != 0)
        {
            return average(purchaseAmount, initQty);
        }
        BigDecimal storedCostPrice = zeroIfNull(batch.getCostPrice());
        if (storedCostPrice.compareTo(BigDecimal.ZERO) != 0)
        {
            return costPrice(storedCostPrice);
        }
        return initQty.compareTo(BigDecimal.ZERO) > 0
                ? average(purchaseAmount, initQty) : costPrice(BigDecimal.ZERO);
    }
}
