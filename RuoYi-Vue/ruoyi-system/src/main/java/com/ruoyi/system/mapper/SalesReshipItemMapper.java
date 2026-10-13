package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SalesReshipItem;

public interface SalesReshipItemMapper
{
    List<SalesReshipItem> selectByReshipCode(@Param("shopCode") String shopCode,
            @Param("reshipCode") String reshipCode);

    int batchInsert(@Param("items") List<SalesReshipItem> items);

    int deleteByReshipCode(@Param("shopCode") String shopCode, @Param("reshipCode") String reshipCode);
}
