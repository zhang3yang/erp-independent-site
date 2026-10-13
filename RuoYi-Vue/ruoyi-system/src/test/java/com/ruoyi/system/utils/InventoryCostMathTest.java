package com.ruoyi.system.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.ruoyi.system.domain.InventoryBatch;

class InventoryCostMathTest
{
    @Test
    void calculatesMoneyWithOneSharedRoundingRule()
    {
        assertEquals(new BigDecimal("33.33"),
                InventoryCostMath.amount(new BigDecimal("3.333333"), new BigDecimal("10")));
        assertEquals(new BigDecimal("3.333000"),
                InventoryCostMath.average(new BigDecimal("33.33"), new BigDecimal("10")));
    }

    @Test
    void repairsUndeductedLegacyBatchFromAccumulatedAmount()
    {
        InventoryBatch batch = new InventoryBatch();
        batch.setInitQty(new BigDecimal("20"));
        batch.setRemainQty(new BigDecimal("20"));
        batch.setPurchaseAmount(new BigDecimal("300.00"));
        batch.setCostPrice(new BigDecimal("20.000000"));

        assertEquals(new BigDecimal("15.000000"), InventoryCostMath.batchCostPrice(batch));
    }

    @Test
    void keepsCurrentBatchPriceAfterAnyDeduction()
    {
        InventoryBatch batch = new InventoryBatch();
        batch.setInitQty(new BigDecimal("20"));
        batch.setRemainQty(new BigDecimal("8"));
        batch.setPurchaseAmount(new BigDecimal("300.00"));
        batch.setCostPrice(new BigDecimal("16.250000"));

        assertEquals(new BigDecimal("16.250000"), InventoryCostMath.batchCostPrice(batch));
    }

    @Test
    void closingMovementAbsorbsTheRemainingCentDifference()
    {
        assertEquals(new BigDecimal("6.67"), InventoryCostMath.amountWithClosingRemainder(
                new BigDecimal("6.66"), BigDecimal.ZERO, new BigDecimal("6.67")));
        assertEquals(new BigDecimal("6.66"), InventoryCostMath.amountWithClosingRemainder(
                new BigDecimal("6.66"), BigDecimal.ONE, new BigDecimal("6.67")));
    }
}
