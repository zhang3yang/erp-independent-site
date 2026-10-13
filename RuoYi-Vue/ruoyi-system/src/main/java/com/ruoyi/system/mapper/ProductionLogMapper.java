package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionLog;

/** 生产执行日志月表 Mapper。 */
public interface ProductionLogMapper
{
    int insertLog(@Param("log") ProductionLog log, @Param("tableSuffix") String tableSuffix);
    List<ProductionLog> selectLogList(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode,
            @Param("operation") String operation, @Param("tableSuffix") String tableSuffix);

    /**
     * 已建出来的生产日志月表后缀，供跨月查询使用。
     *
     * <p>工单跨月是常态——上月领料、这月完工，日志会落在两张月表里。不列出来逐张查，
     * 详情页就只看得到当月那几条。</p>
     */
    List<String> selectLogTableSuffixes();
}
