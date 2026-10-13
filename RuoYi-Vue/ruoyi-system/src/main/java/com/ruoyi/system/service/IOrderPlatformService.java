package com.ruoyi.system.service;

import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import java.util.List;

public interface IOrderPlatformService
{
    List<OrderPlatform> selectPlatformList(OrderPlatform query);
    List<OrderPlatformStore> selectStoreList(OrderPlatformStore query);
    int insertPlatform(OrderPlatform platform);
    int updatePlatform(OrderPlatform platform);
    int deletePlatform(String shopCode, Long id);
    int insertStore(OrderPlatformStore store);
    int updateStore(OrderPlatformStore store);
    int deleteStore(String shopCode, Long id);
}
