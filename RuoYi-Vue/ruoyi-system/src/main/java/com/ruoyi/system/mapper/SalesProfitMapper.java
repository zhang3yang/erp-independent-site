package com.ruoyi.system.mapper;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.SalesProfitSource;

/**
 * 销售利润报表的原始数据查询。只读，按月表逐张查，汇总在 Service 里做。
 * 所有 ${suffix} 都必须先过 MonthTableSuffix 校验，只能是 6 位数字。
 */
public interface SalesProfitMapper
{
    /** 一张单据月表里，日期区间内已审核的销售发货出库、补发出库、销售退货明细 */
    List<SalesProfitSource> selectDocumentLines(@Param("shopCode") String shopCode, @Param("suffix") String suffix,
            @Param("begin") Date begin, @Param("endExclusive") Date endExclusive);

    List<SalesProfitSource> selectShipBatches(@Param("shopCode") String shopCode, @Param("suffix") String suffix,
            @Param("shipCodes") Collection<String> shipCodes);

    List<SalesProfitSource> selectReships(@Param("shopCode") String shopCode,
            @Param("reshipCodes") Collection<String> reshipCodes);

    List<SalesProfitSource> selectOrders(@Param("shopCode") String shopCode, @Param("suffix") String suffix,
            @Param("orderCodes") Collection<String> orderCodes);

    List<SalesProfitSource> selectOrderLines(@Param("shopCode") String shopCode, @Param("suffix") String suffix,
            @Param("orderCodes") Collection<String> orderCodes);
}
