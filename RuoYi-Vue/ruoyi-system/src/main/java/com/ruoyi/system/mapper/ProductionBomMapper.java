package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionBom;

/** 产品配方（BOM）主表 Mapper。 */
public interface ProductionBomMapper
{
    List<ProductionBom> selectBomList(ProductionBom query);
    ProductionBom selectBomByCode(@Param("shopCode") String shopCode, @Param("bomCode") String bomCode);
    /** 建工单时按产出商品取配方；停用的配方不参与自动带出，由调用方判断 status */
    ProductionBom selectBomByProduct(@Param("shopCode") String shopCode, @Param("productCode") String productCode);

    /**
     * 已经有配方的成品编码，不分页。
     *
     * <p>新建配方时选成品要把它们锁住——一个成品只能有一套配方。列表页是分页的，
     * 拿那一页去排除只能排掉当前页。这里只取一列编码，全量返回也不重。</p>
     */
    List<String> selectProductCodes(@Param("shopCode") String shopCode);
    int insertBom(ProductionBom bom);
    int updateBom(ProductionBom bom);
    int deleteBom(@Param("shopCode") String shopCode, @Param("bomCode") String bomCode);
}
