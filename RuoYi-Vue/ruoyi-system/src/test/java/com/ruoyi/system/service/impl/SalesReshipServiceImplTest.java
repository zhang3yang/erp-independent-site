package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.mapper.SalesReshipItemMapper;
import com.ruoyi.system.mapper.SalesReshipMapper;
import com.ruoyi.system.service.ISalesOrderService;

/**
 * 销售补发的选单查询测试。
 *
 * <p>重点盯住查询次数：候选订单再多，也只该发「已补发订单号」「全月已发货明细」
 * 「单头」三次查询。原来的实现把整月订单拉回来再逐张回表查明细，
 * 当月 500 单就是 501 次往返，而这只是给一个下拉框用的。</p>
 */
@ExtendWith(MockitoExtension.class)
class SalesReshipServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202608";

    @Mock private SalesReshipMapper reshipMapper;
    @Mock private SalesReshipItemMapper itemMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private ISalesOrderService orderService;

    @InjectMocks private SalesReshipServiceImpl service;

    /** 发出过货的行才是补发候选，明细要按订单挂回各自的单上。 */
    @Test
    void eligibleOrdersGroupShippedItemsByOrderInThreeQueries()
    {
        when(reshipMapper.selectOriginalOrderCodes(SHOP)).thenReturn(new ArrayList<String>());
        when(orderItemMapper.selectShippedItemsByMonth(SHOP, SUFFIX)).thenReturn(Arrays.asList(
                shippedItem("SO001", "D1", "3"),
                shippedItem("SO001", "D2", "1"),
                shippedItem("SO002", "D3", "5")));
        when(orderMapper.selectByCodes(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(Arrays.asList(order("SO001"), order("SO002")));

        List<SalesOrder> result = service.selectEligibleOrders(SHOP, SUFFIX);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getItems().size());
        assertEquals(1, result.get(1).getItems().size());
        assertEquals(SUFFIX, result.get(0).getTableSuffix());

        verify(orderItemMapper).selectShippedItemsByMonth(SHOP, SUFFIX);
        verify(orderMapper).selectByCodes(eq(SHOP), eq(SUFFIX), anyList());
        // 逐单回表的老路子必须走掉
        verify(orderService, never()).selectOrderDetail(anyString(), anyString(), anyString());
        verify(orderService, never()).selectOrderList(org.mockito.ArgumentMatchers.any(), anyString());
    }

    /** 已经建过补发单的原订单不再进下拉，而且不该出现在单头查询的 in 列表里。 */
    @Test
    void eligibleOrdersExcludeAlreadyReshippedOnes()
    {
        when(reshipMapper.selectOriginalOrderCodes(SHOP))
                .thenReturn(new ArrayList<String>(Collections.singletonList("SO001")));
        when(orderItemMapper.selectShippedItemsByMonth(SHOP, SUFFIX)).thenReturn(Arrays.asList(
                shippedItem("SO001", "D1", "3"),
                shippedItem("SO002", "D3", "5")));
        when(orderMapper.selectByCodes(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(Collections.singletonList(order("SO002")));

        List<SalesOrder> result = service.selectEligibleOrders(SHOP, SUFFIX);

        assertEquals(1, result.size());
        assertEquals("SO002", result.get(0).getOrderCode());

        ArgumentCaptor<List<String>> codes = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).selectByCodes(eq(SHOP), eq(SUFFIX), codes.capture());
        assertEquals(Collections.singletonList("SO002"), codes.getValue());
    }

    /** 全月一行都没发出去过：不必再去查单头。 */
    @Test
    void eligibleOrdersSkipHeaderQueryWhenNothingShipped()
    {
        when(reshipMapper.selectOriginalOrderCodes(SHOP)).thenReturn(new ArrayList<String>());
        when(orderItemMapper.selectShippedItemsByMonth(SHOP, SUFFIX))
                .thenReturn(new ArrayList<SalesOrderItem>());

        assertTrue(service.selectEligibleOrders(SHOP, SUFFIX).isEmpty());

        verify(orderMapper, never()).selectByCodes(anyString(), anyString(), anyList());
    }

    private SalesOrder order(String orderCode)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(orderCode);
        return order;
    }

    private SalesOrderItem shippedItem(String orderCode, String detailCode, String shipped)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setShopCode(SHOP);
        item.setOrderCode(orderCode);
        item.setDetailCode(detailCode);
        item.setGoodsCode("G001");
        item.setQty(new BigDecimal("10"));
        item.setShippedQty(new BigDecimal(shipped));
        return item;
    }
}
