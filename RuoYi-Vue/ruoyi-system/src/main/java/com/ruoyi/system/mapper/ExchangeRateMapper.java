package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ExchangeRate;

/**
 * 汇率Mapper接口
 */
public interface ExchangeRateMapper
{
    public List<ExchangeRate> selectRateList(ExchangeRate query);

    public ExchangeRate selectRateById(@Param("shopCode") String shopCode, @Param("id") Long id);

    public int insertRate(ExchangeRate rate);

    public int updateRate(ExchangeRate rate);

    public int deleteRateById(@Param("shopCode") String shopCode, @Param("id") Long id);
}
