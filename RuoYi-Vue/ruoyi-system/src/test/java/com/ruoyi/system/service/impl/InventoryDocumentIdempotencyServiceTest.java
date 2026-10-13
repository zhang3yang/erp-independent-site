package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.mapper.DocumentMainMapper;

@ExtendWith(MockitoExtension.class)
class InventoryDocumentIdempotencyServiceTest
{
    private static final String REDIS_KEY = "inventory:document:idempotency:S1:request-001";

    @Mock
    private DocumentMainMapper documentMainMapper;

    @Mock
    private RedisService redisService;

    @Mock
    private InventoryDocumentTableRouter tableRouter;

    @InjectMocks
    private InventoryDocumentIdempotencyService service;

    @AfterEach
    void clearTransactionSynchronization()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resolvesAndValidatesDocumentKeyWithoutHttpRequest()
    {
        DocumentMain document = document();
        document.setIdempotencyKey("  request-001  ");

        assertEquals("request-001", service.resolveKey(document));

        document.setIdempotencyKey("bad key");
        assertThrows(ServiceException.class, () -> service.resolveKey(document));
    }

    @Test
    void replaysCompletedRedisEntryWhenDocumentStillExists()
    {
        DocumentMain document = document();
        DocumentMain existing = new DocumentMain();
        existing.setDocCode("D001");
        when(redisService.getValue(REDIS_KEY)).thenReturn(Map.of("value", "DONE|202608|D001"));
        when(tableRouter.validate("202608")).thenReturn("202608");
        when(documentMainMapper.selectDocumentByCode("S1", "D001", "202608")).thenReturn(existing);

        assertEquals("D001", service.replay(document));
        assertEquals("D001", document.getDocCode());
        assertEquals("202608", document.getTableSuffix());
    }

    @Test
    void processingEntryRecoversCommittedDatabaseDocument()
    {
        DocumentMain document = document();
        Date businessDate = new Date(1785513600000L);
        document.setDocDate(businessDate);
        DocumentMain existing = new DocumentMain();
        existing.setDocCode("D002");
        when(redisService.getValue(REDIS_KEY)).thenReturn(Map.of("value", "PROCESSING"));
        when(tableRouter.forBusinessDate(businessDate)).thenReturn("202608");
        when(documentMainMapper.selectDocumentByIdempotencyKey("S1", "request-001", "202608"))
                .thenReturn(existing);

        assertEquals("D002", service.replay(document));
        verify(redisService).setValueCover(REDIS_KEY, "DONE|202608|D002");
    }

    @Test
    void processingEntryRejectsConcurrentRequestWhenDatabaseHasNoResult()
    {
        DocumentMain document = document();
        Date businessDate = document.getDocDate();
        when(redisService.getValue(REDIS_KEY)).thenReturn(Map.of("value", "PROCESSING"));
        when(tableRouter.forBusinessDate(businessDate)).thenReturn("202608");

        assertThrows(ServiceException.class, () -> service.replay(document));
    }

    @Test
    void completionChangesRedisStateOnlyAfterCommit()
    {
        DocumentMain document = document();
        document.setDocCode("D003");
        document.setTableSuffix("202608");
        TransactionSynchronizationManager.initSynchronization();

        service.registerCompletion(document);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.get(0).afterCommit();
        verify(redisService).setValueCover(REDIS_KEY, "DONE|202608|D003");
    }

    @Test
    void rollbackCleanupDeletesProcessingKey()
    {
        DocumentMain document = document();
        TransactionSynchronizationManager.initSynchronization();
        service.registerRollbackCleanup(document);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(redisService).delValue(REDIS_KEY);
    }

    private DocumentMain document()
    {
        DocumentMain document = new DocumentMain();
        document.setShopCode("S1");
        document.setIdempotencyKey("request-001");
        document.setDocDate(new Date(1785513600000L));
        return document;
    }
}
