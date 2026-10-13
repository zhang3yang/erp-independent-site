package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionMaterial;

/** 生产工单物料 Mapper。 */
public interface ProductionMaterialMapper
{
    /** 有几张工单的物料清单用到这个商品。商品档案删除前查它。 */
    int countByGoodsCode(@Param("shopCode") String shopCode, @Param("goodsCode") String goodsCode);

    List<ProductionMaterial> selectByProductionCode(@Param("shopCode") String shopCode,
            @Param("productionCode") String productionCode);
    int batchInsert(@Param("items") List<ProductionMaterial> items);
    int deleteByProductionCode(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode);
    int updateActualQty(@Param("shopCode") String shopCode, @Param("materialCode") String materialCode,
            @Param("actualQty") java.math.BigDecimal actualQty);

    /** 已退数量原子累加，退超时更新 0 行。 */
    int increaseReturnedQty(@Param("shopCode") String shopCode, @Param("materialCode") String materialCode,
            @Param("returnQty") java.math.BigDecimal returnQty);

    /** 已领数量原子累加，领超计划量时更新 0 行。支持分批领料。 */
    int increasePickedQty(@Param("shopCode") String shopCode, @Param("materialCode") String materialCode,
            @Param("pickQty") java.math.BigDecimal pickQty);
}
