package com.ruoyi.system.service.impl;

import com.ruoyi.system.enums.StockState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.RedisService;

/** 库存行 Redis 分布式锁协调器。锁只覆盖当前事务，事务完成后释放。 */
@Component
public class InventoryStockRedisLockService
{
    private static final Object RESOURCE_KEY = InventoryStockRedisLockService.class.getName();
    private static final long WAIT_TIME_MILLIS = 5000L;
    private static final int LEASE_TIME_MILLIS = 120000;
    private static final String KEY_PREFIX = "inventory:stock:lock:";

    @Autowired
    private RedisService redisService;

    @SuppressWarnings("unchecked")
    public void lockForTransaction(String shopCode, List<StockLockKey> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            throw new ServiceException("库存锁定必须在事务中执行");
        }
        List<StockLockKey> ordered = new ArrayList<>(keys);
        Collections.sort(ordered, (a, b) -> a.asText().compareTo(b.asText()));
        Map<String, String> acquired = (Map<String, String>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        boolean first = acquired == null;
        if (acquired == null)
        {
            acquired = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, acquired);
        }
        try
        {
            for (StockLockKey key : ordered)
            {
                String lockKey = KEY_PREFIX + key.asText();
                if (acquired.containsKey(lockKey))
                {
                    continue;
                }
                Map result = redisService.tryLock(lockKey, WAIT_TIME_MILLIS, LEASE_TIME_MILLIS);
                if (!Boolean.TRUE.equals(result.get("result")))
                {
                    throw new ServiceException("库存正被其他业务处理，请稍后重试");
                }
                acquired.put(lockKey, String.valueOf(result.get("requestId")));
            }
            if (first)
            {
                final Map<String, String> lockResources = acquired;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCompletion(int status)
                {
                    release(lockResources);
                    TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
                }
            });
            }
        }
        catch (RuntimeException e)
        {
            if (first)
            {
                release(acquired);
                TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
            }
            throw e;
        }
    }

    private void release(Map<String, String> acquired)
    {
        List<Map.Entry<String, String>> entries = new ArrayList<>(acquired.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<String, String> entry : entries)
        {
            redisService.releaseLock(entry.getKey(), entry.getValue());
        }
    }

    public static final class StockLockKey
    {
        private final String shopCode;
        private final String goodsCode;
        private final String wareCode;
        private final StockState state;

        public StockLockKey(String shopCode, String goodsCode, String wareCode, StockState state)
        {
            this.shopCode = shopCode;
            this.goodsCode = goodsCode;
            this.wareCode = wareCode;
            this.state = state;
        }

        /** 锁的粒度就是库存行的主键：商家 + 商品 + 仓库 + 二维状态 */
        public String asText()
        {
            return String.valueOf(shopCode) + ":" + String.valueOf(goodsCode) + ":"
                    + String.valueOf(wareCode) + ":"
                    + (state == null ? "null"
                            : state.getQualityFlag() + "-" + state.getOnhandFlag());
        }

        @Override
        public boolean equals(Object other)
        {
            return other instanceof StockLockKey && asText().equals(((StockLockKey) other).asText());
        }

        @Override
        public int hashCode()
        {
            return asText().hashCode();
        }
    }
}
