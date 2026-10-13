package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SalesReship;

public interface SalesReshipMapper
{
    List<SalesReship> selectList(SalesReship query);

    SalesReship selectByCode(@Param("shopCode") String shopCode, @Param("reshipCode") String reshipCode);

    SalesReship selectByCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("reshipCode") String reshipCode);

    SalesReship selectByOriginalOrder(@Param("shopCode") String shopCode,
            @Param("originalOrderCode") String originalOrderCode);

    List<String> selectOriginalOrderCodes(@Param("shopCode") String shopCode);

    int insert(SalesReship reship);

    int updateShipped(@Param("shopCode") String shopCode, @Param("reshipCode") String reshipCode,
            @Param("carrier") String carrier, @Param("logisticsNo") String logisticsNo,
            @Param("outboundDocCode") String outboundDocCode, @Param("shipUser") String shipUser);

    /** 删除待发货的补发单。状态条件在 SQL 里，已发货的删不掉，返回 0 行。 */
    int deletePending(@Param("shopCode") String shopCode, @Param("reshipCode") String reshipCode);
}
