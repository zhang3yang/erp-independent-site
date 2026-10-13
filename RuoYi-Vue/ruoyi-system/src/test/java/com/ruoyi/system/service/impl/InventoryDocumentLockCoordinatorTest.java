package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;

@ExtendWith(MockitoExtension.class)
class InventoryDocumentLockCoordinatorTest
{
    @Mock
    private DocTypeConfigMapper docTypeConfigMapper;

    @Mock
    private InventoryStockMapper stockMapper;

    @Mock
    private InventoryStockRedisLockService stockRedisLockService;

    @InjectMocks
    private InventoryDocumentLockCoordinator coordinator;

    @Test
    void pendingOutboundAggregatesPositiveQuantitiesByStockRow()
    {
        DocumentMain document = outboundDocument(detail("G1", "2"), detail("G1", "3"),
                detail("G2", "1"), detail("G3", "0"));
        when(docTypeConfigMapper.selectDocTypeByCode("CK")).thenReturn(new DocTypeConfig());
        when(stockMapper.adjustStockLockedQtyNoVersion("S1", "G1", "W1", StockState.available(), new BigDecimal("5")))
                .thenReturn(1);
        when(stockMapper.adjustStockLockedQtyNoVersion("S1", "G2", "W1", StockState.available(), BigDecimal.ONE))
                .thenReturn(1);

        coordinator.lockPendingOutboundOrTransfer(document);

        ArgumentCaptor<List<InventoryStockRedisLockService.StockLockKey>> keyCaptor = listCaptor();
        verify(stockRedisLockService).lockForTransaction(eq("S1"), keyCaptor.capture());
        List<String> keys = keyCaptor.getValue().stream()
                .map(InventoryStockRedisLockService.StockLockKey::asText)
                .collect(Collectors.toList());
        assertEquals(2, keys.size());
        assertTrue(keys.contains("S1:G1:W1:1-1"));
        assertTrue(keys.contains("S1:G2:W1:1-1"));
    }

    @Test
    void pendingOutboundReleaseUsesNegativeAggregatedQuantity()
    {
        DocumentMain document = outboundDocument(detail("G1", "2"), detail("G1", "3"));
        when(docTypeConfigMapper.selectDocTypeByCode("CK")).thenReturn(new DocTypeConfig());
        when(stockMapper.adjustStockLockedQtyNoVersion("S1", "G1", "W1", StockState.available(), new BigDecimal("-5")))
                .thenReturn(1);

        coordinator.unlockPendingOutboundOrTransfer(document, document.getDetails());

        verify(stockMapper).adjustStockLockedQtyNoVersion(
                "S1", "G1", "W1", StockState.available(), new BigDecimal("-5"));
    }

    @Test
    void transferLocksEachDistinctSourceAndTargetStockRowOnce()
    {
        DocumentMain document = outboundDocument(detail("G1", "1"), detail("G1", "2"));
        document.setToWareCode("W2");
        document.setToState(StockState.of(QualityFlag.DEFECTIVE, OnhandFlag.IN));

        coordinator.lockStockOperationResources(document, "N", document.getDetails());

        ArgumentCaptor<List<InventoryStockRedisLockService.StockLockKey>> keyCaptor = listCaptor();
        verify(stockRedisLockService).lockForTransaction(eq("S1"), keyCaptor.capture());
        List<String> keys = keyCaptor.getValue().stream()
                .map(InventoryStockRedisLockService.StockLockKey::asText)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("S1:G1:W1:1-1", "S1:G1:W2:0-1"), keys);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<List<InventoryStockRedisLockService.StockLockKey>> listCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private DocumentMain outboundDocument(DocumentDetail... details)
    {
        DocumentMain document = new DocumentMain();
        document.setShopCode("S1");
        document.setDocType("CK");
        document.setFromWareCode("W1");
        document.setFromState(StockState.available());
        document.setDetails(Arrays.asList(details));
        return document;
    }

    private DocumentDetail detail(String goodsCode, String quantity)
    {
        DocumentDetail detail = new DocumentDetail();
        detail.setGoodsCode(goodsCode);
        detail.setQty(new BigDecimal(quantity));
        return detail;
    }
}
