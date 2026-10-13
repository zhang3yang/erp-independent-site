package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.ShipBatchItem;
import com.ruoyi.system.enums.StockState;

/**
 * 订单侧库存锁协调器：把一笔订单操作要动的<b>全部</b>库存行收齐后一次锁定。
 *
 * <p>做法和 {@link InventoryDocumentLockCoordinator} 一样，理由也一样：
 * {@link InventoryStockRedisLockService#lockForTransaction} 的排序只在<b>单次调用内</b>
 * 生效，锁却一直攒在事务级的 acquired 里到 afterCompletion 才释放。所以「一行一锁」
 * 地循环调用，等于给一个事务开了 N 个加锁点，两个事务只要明细顺序不同就会交叉持锁：</p>
 *
 * <pre>
 *   订单A 明细 [G2, G1]  ┐ 同仓并发发货
 *   订单B 明细 [G1, G2]  ┘
 *   A 拿到 G2、B 拿到 G1 → 互等 → 5 秒后双双报「库存正被其他业务处理」
 * </pre>
 *
 * <p>更麻烦的是 Redis 锁之后紧跟着 {@code selectStockByKeyForUpdate} 的行锁，顺序一样，
 * 那个是 InnoDB 真死锁，会被检测出来回滚掉一方。</p>
 *
 * <p><b>只剩发货一个加锁点了。</b>原先还有「确认订单」和「生产完工回填」两个，它们是给
 * 订单预占准备的——预占要逐条 reserve，每条各自加锁，正是上面那个交叉持锁的场景。
 * 订单预占取消之后这两条路径一行库存都不碰，锁也就没有对象了。</p>
 */
@Component
public class OrderStockLockCoordinator
{
    /** 发货扣的是「良品·在库」那一格，与销售出库单的来源状态同口径。 */
    private static final StockState SHIP_STATE = StockState.available();

    @Autowired
    private InventoryStockRedisLockService stockRedisLockService;

    /**
     * 发货：把本次销售出库单要动的库存行一次锁完。
     *
     * <p>动的是批次仓的「良品·在库」，一张批次里可能有多个商品，必须在<b>同一次调用</b>里
     * 交出去排序加锁，不能边发边锁。</p>
     */
    public void lockForShip(String shopCode, String wareCode, List<ShipBatchItem> items)
    {
        if (isBlank(wareCode) || items == null)
        {
            return;
        }
        Set<InventoryStockRedisLockService.StockLockKey> keys = new LinkedHashSet<>();
        for (ShipBatchItem item : items)
        {
            addKey(keys, shopCode, item == null ? null : item.getGoodsCode(), wareCode, SHIP_STATE);
        }
        lock(shopCode, keys);
    }

    private void addKey(Set<InventoryStockRedisLockService.StockLockKey> keys, String shopCode,
            String goodsCode, String wareCode, StockState state)
    {
        if (isBlank(goodsCode) || isBlank(wareCode) || state == null)
        {
            return;
        }
        // StockLockKey 的 equals/hashCode 都走 asText()，重复键在这里自然去掉
        keys.add(new InventoryStockRedisLockService.StockLockKey(shopCode, goodsCode, wareCode, state));
    }

    private void lock(String shopCode, Set<InventoryStockRedisLockService.StockLockKey> keys)
    {
        if (keys.isEmpty())
        {
            return;
        }
        stockRedisLockService.lockForTransaction(shopCode, new ArrayList<>(keys));
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
