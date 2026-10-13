package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionOrderDetail;

/** 生产工单产出明细 Mapper。 */
public interface ProductionOrderDetailMapper
{
    /** 有几张工单要产出这个商品。商品档案删除前查它。 */
    int countByProductCode(@Param("shopCode") String shopCode, @Param("productCode") String productCode);

    List<ProductionOrderDetail> selectByProductionCode(@Param("shopCode") String shopCode,
            @Param("productionCode") String productionCode);
    int batchInsert(@Param("items") List<ProductionOrderDetail> items);
    int deleteByProductionCode(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode);
    int updateExecution(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("actualQty") java.math.BigDecimal actualQty,
            @Param("materialCost") java.math.BigDecimal materialCost,
            @Param("unitCost") java.math.BigDecimal unitCost,
            @Param("totalCost") java.math.BigDecimal totalCost);
}
