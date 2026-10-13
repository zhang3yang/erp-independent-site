package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.CurrencyConvertLog;

/**
 * 结算币种换算流水。对应表 {@code u_s_currency_convert_log}。
 *
 * <p>只写不改：一次换算落一行，之后没有任何东西会去改它。所以这里没有 update，
 * 也没有 delete——删掉一行就是把那次换算用的汇率弄丢了，而那是唯一的还原依据
 * （见 {@link CurrencyConvertLog} 的类注释）。</p>
 */
public interface CurrencyConvertLogMapper
{
    int insert(CurrencyConvertLog log);

    /** 最近几次换算，倒序。管理台「站点概览」里展示，也是运营找还原依据的入口 */
    List<CurrencyConvertLog> selectRecent(@Param("limit") int limit);
}
