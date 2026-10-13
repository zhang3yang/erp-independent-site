package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionBomItem;

/** 产品配方（BOM）物料明细 Mapper。 */
public interface ProductionBomItemMapper
{
    List<ProductionBomItem> selectByBomCode(@Param("shopCode") String shopCode, @Param("bomCode") String bomCode);
    int batchInsert(@Param("items") List<ProductionBomItem> items);
    int deleteByBomCode(@Param("shopCode") String shopCode, @Param("bomCode") String bomCode);

    /** 有几套配方用到这个物料。商品档案删除前查它，免得配方指向一个查不到的商品。 */
    int countByGoodsCode(@Param("shopCode") String shopCode, @Param("goodsCode") String goodsCode);
}
