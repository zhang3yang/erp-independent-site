package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.domain.ShipBatchItem;

/**
 * 订单侧整批加锁测试。
 *
 * <p>每个用例都盯死同一件事：一个事务只能有<b>一个</b>加锁点。
 * {@code verify(stockRedisLockService).lockForTransaction(...)} 的默认 times(1)
 * 就是这条断言——多一次调用就是多一个加锁点，排序保证随之失效。</p>
 *
 * <p>原先这里还测「确认订单」和「生产完工回填」两个加锁点，它们是给订单预占准备的。
 * 订单预占取消之后那两条路径一行库存都不碰，只剩发货这一个。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderStockLockCoordinatorTest
{
    private static final String SHOP = "S1";

    @Mock
    private InventoryStockRedisLockService stockRedisLockService;

    @InjectMocks
    private OrderStockLockCoordinator coordinator;

    /** 发货：一张批次里的多个商品一次锁完，同商品重复出现只锁一把。 */
    @Test
    void shipLocksEveryDistinctGoodsRowInOneCall()
    {
        coordinator.lockForShip(SHOP, "W1",
                Arrays.asList(shipItem("G2"), shipItem("G1"), shipItem("G2")));

        assertEquals(Arrays.asList("S1:G2:W1:1-1", "S1:G1:W1:1-1"), capturedKeys());
    }

    /**
     * 没给仓库就没有库存行要动，一把锁都不加。
     *
     * <p>不能顺手锁个「默认仓」：锁一把用不上的锁只会凭空增加争用。</p>
     */
    @Test
    void shipWithoutWarehouseTakesNoLock()
    {
        coordinator.lockForShip(SHOP, "  ", Collections.singletonList(shipItem("G1")));

        verify(stockRedisLockService, never()).lockForTransaction(anyString(), any());
    }

    /** 明细为空同理：没有要锁的行。 */
    @Test
    void shipWithoutItemsTakesNoLock()
    {
        coordinator.lockForShip(SHOP, "W1", null);

        verify(stockRedisLockService, never()).lockForTransaction(anyString(), any());
    }

    /** 锁的是「良品·在库」那一格——销售出库单就从这一格扣。 */
    @Test
    void shipLocksTheAvailableStateOnly()
    {
        coordinator.lockForShip(SHOP, "W1", Collections.singletonList(shipItem("G1")));

        assertEquals(Collections.singletonList("S1:G1:W1:1-1"), capturedKeys());
    }

    private List<String> capturedKeys()
    {
        ArgumentCaptor<List<InventoryStockRedisLockService.StockLockKey>> captor = listCaptor();
        verify(stockRedisLockService).lockForTransaction(eq(SHOP), captor.capture());
        return captor.getValue().stream()
                .map(InventoryStockRedisLockService.StockLockKey::asText)
                .collect(Collectors.toList());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<List<InventoryStockRedisLockService.StockLockKey>> listCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private ShipBatchItem shipItem(String goodsCode)
    {
        ShipBatchItem item = new ShipBatchItem();
        item.setShopCode(SHOP);
        item.setGoodsCode(goodsCode);
        return item;
    }
}
