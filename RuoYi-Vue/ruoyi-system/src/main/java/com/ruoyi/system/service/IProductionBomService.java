package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.ProductionBom;

/** 产品配方（BOM）主数据服务。 */
public interface IProductionBomService
{
    List<ProductionBom> selectBomList(ProductionBom query);

    /** 配方详情，带物料明细 */
    ProductionBom selectBomByCode(String shopCode, String bomCode);

    /**
     * 按产出商品取配方，供建生产工单时自动带出。
     *
     * <p>停用的配方返回 null——停用的意思就是「不要再按这套做了」，带出来反而会误导。</p>
     */
    ProductionBom selectEnabledBomByProduct(String shopCode, String productCode);

    /** 已经有配方的成品编码，供新建配方时把它们从选商品弹窗里锁掉。停用的也算——它占着那个成品。 */
    List<String> selectBomProductCodes(String shopCode);

    int insertBom(ProductionBom bom);
    int updateBom(ProductionBom bom);
    int deleteBom(String shopCode, String bomCode);
}
