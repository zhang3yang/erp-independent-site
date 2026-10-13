package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SalesPackage;
import com.ruoyi.system.domain.SalesPackageItem;

/** 销售套餐主数据。套餐不分月表——它是「当前有效配置」，不是单据。 */
public interface SalesPackageMapper
{
    List<SalesPackage> selectPackageList(SalesPackage query);

    SalesPackage selectPackageByCode(@Param("shopCode") String shopCode,
            @Param("packageCode") String packageCode);

    int insertPackage(SalesPackage salesPackage);

    int updatePackage(SalesPackage salesPackage);

    int deletePackage(@Param("shopCode") String shopCode, @Param("packageCode") String packageCode);

    /** 明细按套餐整体重建，所以只有「全量查」「批量插」「按套餐删」三个动作 */
    List<SalesPackageItem> selectItemsByPackage(@Param("shopCode") String shopCode,
            @Param("packageCode") String packageCode);

    /** 一次取多个套餐的明细，供下单时的套餐选择列表一次性带出，避免 N+1 */
    List<SalesPackageItem> selectItemsByPackages(@Param("shopCode") String shopCode,
            @Param("packageCodes") List<String> packageCodes);

    int batchInsertItems(@Param("items") List<SalesPackageItem> items);

    int deleteItemsByPackage(@Param("shopCode") String shopCode,
            @Param("packageCode") String packageCode);
}
