package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventorySerialService;

@ExtendWith(MockitoExtension.class)
class InventoryCostResolverTest
{
    @Mock
    private IInventoryBatchService batchService;

    @Mock
    private IInventorySerialService serialService;

    @InjectMocks
    private InventoryCostResolver resolver;

    @Test
    void movingAverageIgnoresTrackedBatchPrice()
    {
        InventoryGoods goods = goods("batch", "moving_avg");
        InventoryStock stock = stock("10", "125.00");

        BigDecimal result = resolver.resolveOutboundCostPrice("S1", goods, "W1", StockState.available(),
                "B1", Collections.emptyList(), new BigDecimal("2"), stock);

        assertEquals(new BigDecimal("12.500000"), result);
        verifyNoInteractions(batchService, serialService);
    }

    @Test
    void fifoUsesStockEstimateBeforeAuditAndActualBatchCostDuringAudit()
    {
        InventoryGoods goods = goods("batch", "fifo");
        InventoryStock stock = stock("10", "120.00");
        InventoryBatch batch = batch("B1", "5", "5", "20.000000", "100.00");
        when(batchService.selectBatchByKey("S1", "B1", "G1", "W1", StockState.available())).thenReturn(batch);

        assertEquals(new BigDecimal("12.000000"), resolver.resolveOutboundCostPrice(
                "S1", goods, "W1", StockState.available(), "B1", Collections.emptyList(),
                BigDecimal.ONE, stock));
        assertEquals(new BigDecimal("20.000000"), resolver.resolveBatchCostPrice(
                "S1", goods, "W1", StockState.available(), "B1"));
    }

    @Test
    void individualSerialUsesItsOwnCost()
    {
        InventoryGoods goods = goods("serial", "individual");
        InventorySerial serial = serial("SN1", "B1", "17.250000");
        when(serialService.selectSerialByContext("S1", "SN1", "G1", "W1", StockState.available()))
                .thenReturn(serial);

        BigDecimal result = resolver.resolveOutboundCostPrice("S1", goods, "W1", StockState.available(),
                null, Collections.singletonList("SN1"), BigDecimal.ONE, stock("1", "17.25"));

        assertEquals(new BigDecimal("17.250000"), result);
    }

    @Test
    void zeroCostSerialDoesNotFallBackToBatchCost()
    {
        InventoryGoods goods = goods("serial", "individual");
        InventorySerial serial = serial("SN0", "B1", "0.000000");
        when(serialService.selectSerialByContext("S1", "SN0", "G1", "W1", StockState.available()))
                .thenReturn(serial);

        BigDecimal result = resolver.resolveTransferCostPrice("S1", goods, "W1", StockState.available(),
                null, Collections.singletonList("SN0"), BigDecimal.ONE, stock("1", "0.00"));

        assertEquals(new BigDecimal("0.000000"), result);
        verifyNoInteractions(batchService);
    }

    @Test
    void serialsAcrossBatchesUseTheirActualWeightedCost()
    {
        InventoryGoods goods = goods("serial", "individual");
        InventorySerial first = serial("SN1", "B1", "10.000000");
        InventorySerial legacy = serial("SN2", "B2", null);
        InventoryBatch secondBatch = batch("B2", "1", "1", "20.000000", "20.00");
        when(serialService.selectSerialByContext("S1", "SN1", "G1", "W1", StockState.available()))
                .thenReturn(first);
        when(serialService.selectSerialByContext("S1", "SN2", "G1", "W1", StockState.available()))
                .thenReturn(legacy);
        when(batchService.selectBatchByKey("S1", "B2", "G1", "W1", StockState.available()))
                .thenReturn(secondBatch);

        BigDecimal result = resolver.resolveTransferCostPrice("S1", goods, "W1", StockState.available(),
                null, Arrays.asList("SN1", "SN2"), new BigDecimal("2"), stock("2", "30.00"));

        assertEquals(new BigDecimal("15.000000"), result);
    }

    private InventoryGoods goods(String trackingStrategy, String calculation)
    {
        InventoryGoods goods = new InventoryGoods();
        goods.setGoodsCode("G1");
        goods.setGoodsName("Test goods");
        goods.setBatchStrategy(trackingStrategy);
        goods.setCostCalculation(calculation);
        return goods;
    }

    private InventoryStock stock(String qty, String totalCost)
    {
        InventoryStock stock = new InventoryStock();
        stock.setPhysicalQty(new BigDecimal(qty));
        stock.setTotalCostAmount(new BigDecimal(totalCost));
        return stock;
    }

    private InventorySerial serial(String serialNo, String batchCode, String costPrice)
    {
        InventorySerial serial = new InventorySerial();
        serial.setSerialNo(serialNo);
        serial.setBatchCode(batchCode);
        serial.setCostPrice(costPrice == null ? null : new BigDecimal(costPrice));
        serial.setStatus("instock");
        return serial;
    }

    private InventoryBatch batch(String batchCode, String initialQty, String remainingQty,
            String costPrice, String purchaseAmount)
    {
        InventoryBatch batch = new InventoryBatch();
        batch.setBatchCode(batchCode);
        batch.setInitQty(new BigDecimal(initialQty));
        batch.setRemainQty(new BigDecimal(remainingQty));
        batch.setCostPrice(new BigDecimal(costPrice));
        batch.setPurchaseAmount(new BigDecimal(purchaseAmount));
        batch.setStatus("active");
        return batch;
    }
}
