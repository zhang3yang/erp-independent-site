package com.ruoyi.station.console.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.CustomPlan;

/**
 * 买家 3D 定制方案（{@code u_s_custom_plan}）。只插不改——方案保存后不可修改。
 */
public interface CustomPlanMapper
{
    int insertPlan(CustomPlan plan);

    CustomPlan selectById(@Param("planId") String planId);

    /** 管理台列表。goodsCode / 日期区间都可空；带出商品名（goodsname，不分语言） */
    List<CustomPlan> selectList(@Param("goodsCode") String goodsCode, @Param("from") Date from,
            @Param("to") Date to, @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("goodsCode") String goodsCode, @Param("from") Date from, @Param("to") Date to);
}
