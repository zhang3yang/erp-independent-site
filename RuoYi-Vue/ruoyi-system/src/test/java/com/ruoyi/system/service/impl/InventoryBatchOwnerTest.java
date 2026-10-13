package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryBatchAllocation;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryBatchMapper;

/**
 * 定制件专属批次：绑定、挑批、释放。
 *
 * <p>这是「定制货不能被别的订单发走」在库存侧唯一的 enforcement 点。订单已经不再预占库存，
 * 普通件谁先发货谁拿到；定制件靠这里的归属关系兜住。</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryBatchOwnerTest
{
    private static final String SHOP = "S001";
    private static final String GOODS = "P001";
    private static final String WARE = "W1";
    private static final String BATCH = "PC20260824001";
    private static final String MINE = "XSMX20260824001";
    private static final String OTHERS = "XSMX20260824002";
    private static final StockState AVAILABLE = StockState.available();

    @Mock private InventoryBatchMapper batchMapper;
    @InjectMocks private InventoryBatchServiceImpl service;

    // ---------------------------------------------------------------- 归属判定

    /** 没绑过的批次谁都能发，包括不带订单上下文的调拨、盘点。 */
    @Test
    void unownedBatchIsAvailableToEveryone()
    {
        InventoryBatch batch = batch(BATCH, "10", null);
        assertFalse(batch.isOwned());
        assertTrue(batch.isAvailableFor(MINE));
        assertTrue(batch.isAvailableFor(null));
    }

    /** 绑过的批次只认自己那条明细：别人来发、或者没带订单上下文，都不给。 */
    @Test
    void ownedBatchOnlyServesItsOwner()
    {
        InventoryBatch batch = batch(BATCH, "10", MINE);
        assertTrue(batch.isOwned());
        assertTrue(batch.isAvailableFor(MINE));
        assertFalse(batch.isAvailableFor(OTHERS));
        // 非订单出库（调拨、盘亏、生产领料）一律不能动定制货
        assertFalse(batch.isAvailableFor(null));
    }

    // ---------------------------------------------------------------- 绑定

    @Test
    void bindOwnerWritesOwnership()
    {
        when(batchMapper.bindBatchOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", MINE)).thenReturn(1);

        service.bindOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", MINE);

        verify(batchMapper).bindBatchOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", MINE);
    }

    /**
     * 已经绑给别人的批次要抛，不能静默跳过。
     *
     * <p>静默跳过意味着这张单的定制货被前一张单发走了，客户拿不到自己那批——
     * 宁可让整笔完工回滚，让人换个批次号重来。</p>
     */
    @Test
    void bindOwnerRejectsBatchAlreadyOwnedByAnother()
    {
        when(batchMapper.bindBatchOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", MINE)).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.bindOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", MINE));
        assertTrue(error.getMessage().contains("其他销售订单的定制货"), error.getMessage());
    }

    @Test
    void bindOwnerRequiresBatchAndDetail()
    {
        assertThrows(ServiceException.class,
                () -> service.bindOwner(SHOP, "  ", GOODS, WARE, AVAILABLE, "XS001", MINE));
        assertThrows(ServiceException.class,
                () -> service.bindOwner(SHOP, BATCH, GOODS, WARE, AVAILABLE, "XS001", "  "));
        verify(batchMapper, never()).bindBatchOwner(anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- 挑批

    /**
     * 挑批时把订单明细代码透传给 SQL：过滤和排序都在那一层做，
     * 这里只验它确实传下去了——传丢了会让定制货被当成通用库存发走。
     */
    @Test
    void deductPassesOrderContextToQuery()
    {
        when(batchMapper.selectAvailableBatchForUpdate(SHOP, GOODS, WARE, AVAILABLE, MINE))
                .thenReturn(Collections.singletonList(batch(BATCH, "10", MINE)));
        when(batchMapper.deductBatchQty(SHOP, BATCH, GOODS, WARE, AVAILABLE, new BigDecimal("6"))).thenReturn(1);

        List<InventoryBatchAllocation> allocations = service.deductFifoBatches(
                SHOP, GOODS, WARE, AVAILABLE, new BigDecimal("6"), MINE);

        verify(batchMapper).selectAvailableBatchForUpdate(SHOP, GOODS, WARE, AVAILABLE, MINE);
        assertEquals(1, allocations.size());
        assertEquals(BATCH, allocations.get(0).getBatchCode());
        assertEquals(0, allocations.get(0).getQty().compareTo(new BigDecimal("6")));
    }

    /** 多个批次按查询返回的顺序依次扣，扣满即止。 */
    @Test
    void deductConsumesBatchesInQueryOrder()
    {
        when(batchMapper.selectAvailableBatchForUpdate(SHOP, GOODS, WARE, AVAILABLE, MINE))
                .thenReturn(Arrays.asList(batch("PC001", "4", MINE), batch("PC002", "10", null)));
        when(batchMapper.deductBatchQty(eq(SHOP), anyString(), eq(GOODS), eq(WARE), eq(AVAILABLE), any()))
                .thenReturn(1);

        List<InventoryBatchAllocation> allocations = service.deductFifoBatches(
                SHOP, GOODS, WARE, AVAILABLE, new BigDecimal("6"), MINE);

        assertEquals(2, allocations.size());
        assertEquals(0, allocations.get(0).getQty().compareTo(new BigDecimal("4")));
        assertEquals(0, allocations.get(1).getQty().compareTo(new BigDecimal("2")));
    }

    /**
     * 挑不到足够的批次时，错误里要点出「别人的定制货不算可用量」。
     *
     * <p>不说这一句的话，现场看到的是「仓库明明有 100 个却说不够」，会被当成数据错乱去查库。</p>
     */
    @Test
    void deductExplainsThatOthersCustomBatchesDoNotCount()
    {
        when(batchMapper.selectAvailableBatchForUpdate(SHOP, GOODS, WARE, AVAILABLE, MINE))
                .thenReturn(Collections.singletonList(batch(BATCH, "2", null)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.deductFifoBatches(
                SHOP, GOODS, WARE, AVAILABLE, new BigDecimal("6"), MINE));
        assertTrue(error.getMessage().contains("定制批次"), error.getMessage());
    }

    // ---------------------------------------------------------------- 释放

    /** 订单终止时松开：那批货虽然是为它做的，但留着没人能发比给别人用更糟。 */
    @Test
    void releaseOwnerFreesBatchesOfDetail()
    {
        when(batchMapper.releaseBatchOwnerByDetail(SHOP, MINE)).thenReturn(2);
        assertEquals(2, service.releaseOwner(SHOP, MINE));
    }

    /** 明细代码为空时直接返回，别把一句没有 where 条件的 update 发下去。 */
    @Test
    void releaseOwnerIgnoresBlankDetail()
    {
        assertEquals(0, service.releaseOwner(SHOP, "  "));
        assertEquals(0, service.releaseOwner(SHOP, null));
        verify(batchMapper, never()).releaseBatchOwnerByDetail(anyString(), anyString());
    }

    private InventoryBatch batch(String batchCode, String remain, String ownerDetailCode)
    {
        InventoryBatch batch = new InventoryBatch();
        batch.setShopCode(SHOP);
        batch.setBatchCode(batchCode);
        batch.setGoodsCode(GOODS);
        batch.setWareCode(WARE);
        batch.setRemainQty(new BigDecimal(remain));
        batch.setCostPrice(new BigDecimal("5"));
        batch.setOwnerDetailCode(ownerDetailCode);
        if (ownerDetailCode != null) batch.setOwnerOrderCode("XS001");
        return batch;
    }
}
