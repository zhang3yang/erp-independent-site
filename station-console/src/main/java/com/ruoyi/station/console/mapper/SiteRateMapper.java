package com.ruoyi.station.console.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteRate;

/**
 * 汇率。对应表 {@code u_s_rate}，只有一行。
 *
 * <p>写操作故意拆成两个：{@link #updateFetched} 是定时任务写的，
 * {@link #updateOverride} 是运营在管理台写的。合成一个 update 的话，
 * 凌晨的定时任务会把运营白天填的覆盖值一起冲掉——而那种覆盖，
 * 常常正是因为自动汇率不对才填的。</p>
 *
 * <p>SQL 里不写库限定名，理由同 {@link SiteInfoMapper}。</p>
 */
public interface SiteRateMapper
{
    /** 取唯一那一行。建库脚本已经 INSERT IGNORE 了一行，正常查得到 */
    SiteRate selectRate();

    /**
     * 定时任务拉取成功后写回。
     *
     * <p>只动 {@code ratesjson} / {@code base_code} / {@code fetchtime} /
     * {@code fetchstatus}，并清空 {@code fetcherror}。<b>不碰 overridejson。</b></p>
     */
    int updateFetched(@Param("baseCode") String baseCode,
                      @Param("ratesJson") String ratesJson);

    /**
     * 定时任务拉取失败后写回。
     *
     * <p>只写状态和原因，<b>ratesjson 和 fetchtime 都保持原样</b>——上一次的汇率
     * 还能用，而 fetchtime 一旦被失败刷新，「多久没更新了」就再也判不出来了。</p>
     */
    int updateFetchFailed(@Param("error") String error);

    /** 运营在管理台改手工覆盖值。不碰自动拉取的那几列 */
    int updateOverride(@Param("overrideJson") String overrideJson,
                       @Param("updateUser") String updateUser);
}
