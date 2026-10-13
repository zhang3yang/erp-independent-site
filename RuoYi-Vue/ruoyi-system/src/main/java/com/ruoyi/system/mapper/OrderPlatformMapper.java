package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OrderPlatformMapper
{
    List<OrderPlatform> selectPlatformList(OrderPlatform query);
    OrderPlatform selectPlatformByCode(@Param("shopCode") String shopCode,
            @Param("platformCode") String platformCode);
    int insertPlatform(OrderPlatform platform);
    int updatePlatform(OrderPlatform platform);
    int deletePlatform(@Param("shopCode") String shopCode, @Param("id") Long id);
    int countStores(@Param("shopCode") String shopCode, @Param("platformCode") String platformCode);

    List<OrderPlatformStore> selectStoreList(OrderPlatformStore query);
    /** 店铺代码只在平台内唯一，查询必须带上平台代码 */
    OrderPlatformStore selectStoreByCode(@Param("shopCode") String shopCode,
            @Param("platformCode") String platformCode, @Param("storeCode") String storeCode);
    /** 店铺名称同样只在平台内唯一 */
    OrderPlatformStore selectStoreByName(@Param("shopCode") String shopCode,
            @Param("platformCode") String platformCode, @Param("storeName") String storeName);
    int insertStore(OrderPlatformStore store);
    int updateStore(OrderPlatformStore store);
    int deleteStore(@Param("shopCode") String shopCode, @Param("id") Long id);
    int countStoreOrders(@Param("shopCode") String shopCode, @Param("platformCode") String platformCode,
            @Param("storeCode") String storeCode, @Param("tableSuffix") String tableSuffix);
}
