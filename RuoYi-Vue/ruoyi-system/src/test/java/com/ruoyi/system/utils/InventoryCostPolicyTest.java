package com.ruoyi.system.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryGoods;

class InventoryCostPolicyTest
{
    @Test
    void appliesDefaults()
    {
        InventoryGoods goods = new InventoryGoods();
        InventoryCostPolicy.normalizeAndValidate(goods);
        assertEquals("none", goods.getBatchStrategy());
        assertEquals("moving_avg", goods.getCostCalculation());
    }

    @Test
    void derivesCostCalculationFromBatchStrategy()
    {
        assertEquals("moving_avg", normalized("none"));
        assertEquals("fifo", normalized("batch"));
        assertEquals("individual", normalized("serial"));
    }

    @Test
    void overridesMismatchedCostCalculation()
    {
        assertEquals("fifo", normalized("batch", "individual"));
        assertEquals("individual", normalized("serial", "fifo"));
        assertEquals("moving_avg", normalized("none", "individual"));
    }

    @Test
    void normalizesCaseAndPadding()
    {
        InventoryGoods goods = goods("  BATCH ", null);
        InventoryCostPolicy.normalizeAndValidate(goods);
        assertEquals("batch", goods.getBatchStrategy());
        assertEquals("fifo", goods.getCostCalculation());
    }

    @Test
    void rejectsUnknownBatchStrategy()
    {
        InventoryGoods goods = goods("lot", "fifo");
        assertThrows(ServiceException.class, () -> InventoryCostPolicy.normalizeAndValidate(goods));
    }

    @Test
    void exposesMappingDirectly()
    {
        assertEquals("moving_avg", InventoryCostPolicy.costCalculationFor(null));
        assertEquals("fifo", InventoryCostPolicy.costCalculationFor("batch"));
        assertEquals("individual", InventoryCostPolicy.costCalculationFor("SERIAL"));
        assertEquals("moving_avg", InventoryCostPolicy.costCalculationFor("lot"));
    }

    private String normalized(String tracking)
    {
        return normalized(tracking, null);
    }

    private String normalized(String tracking, String calculation)
    {
        InventoryGoods goods = goods(tracking, calculation);
        InventoryCostPolicy.normalizeAndValidate(goods);
        return goods.getCostCalculation();
    }

    private InventoryGoods goods(String tracking, String calculation)
    {
        InventoryGoods goods = new InventoryGoods();
        goods.setBatchStrategy(tracking);
        goods.setCostCalculation(calculation);
        return goods;
    }
}
