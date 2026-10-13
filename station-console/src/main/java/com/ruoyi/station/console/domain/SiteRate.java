package com.ruoyi.station.console.domain;

import java.util.Date;

/**
 * 汇率。对应表 {@code u_s_rate}，本库只有一行（{@code id} 恒为 1）。
 *
 * <h3>这张表存什么</h3>
 * <p>一份完整的汇率表（{@link #ratesJson}），由 {@code RateFetcher} 每天从
 * {@code open.er-api.com} 拉一次；外加一份运营手工填的覆盖值（{@link #overrideJson}）。
 * 取值和换算都在 {@code RateService}，这个类只负责搬运。</p>
 *
 * <h3>⚠ base_code 不是站点本币</h3>
 * <p>{@link #baseCode} 是<b>汇率源返回值的基准</b>，跟着 API 走（现在是 USD）。
 * 站点本币在 {@code u_s_siteinfo.currency}，两者经常不是一个值。把它们看混会让
 * 换算方向整个反过来——而反过来的结果仍然是一个「看起来像价格」的数字，
 * 页面上不会报错，只会贵 40 倍或便宜 40 倍。</p>
 *
 * <p>任意两个币种之间怎么算见 {@code RateService.rate}：两边都相对 baseCode，
 * 所以站点本币是 CNY 也不用换汇率源。</p>
 */
public class SiteRate
{
    /** 拉取成功 */
    public static final String STATUS_OK = "成功";
    /** 拉取失败。ratesJson 保持上一次的值，不清空 */
    public static final String STATUS_FAIL = "失败";

    /** 汇率超过这么多天没更新，管理台标红。前台不阻断——有旧汇率也比没价格强 */
    public static final int STALE_DAYS = 7;

    private Integer id;

    /** ⚠ 汇率源的基准，不是站点本币。见类注释 */
    private String baseCode;

    /** 自动拉回来的完整汇率表，{@code {"CNY":6.728426,"EUR":0.860364,...}}，相对 baseCode */
    private String ratesJson;

    /**
     * 运营手工覆盖，同样相对 baseCode，{@code {"EUR":0.90}}。
     *
     * <p>填了就压过 {@link #ratesJson}，<b>逐币种生效</b>——覆盖 EUR 不影响 CNY。
     * 两个用途：免费 API 没有 SLA，挂了要有兜底；外贸站常故意把汇率打高留 buffer，
     * 那是业务决定，不该改代码。</p>
     */
    private String overrideJson;

    /** 最后一次<b>成功</b>拉取的时间。拉取失败不更新它，否则「过期」就永远判不出来 */
    private Date fetchTime;

    /** {@link #STATUS_OK} / {@link #STATUS_FAIL} */
    private String fetchStatus;

    /** 失败原因。免费 API 静悄悄挂掉是最可能的故障，而它在页面上没有别的症状 */
    private String fetchError;

    private String updateUser;
    private Date updateTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getBaseCode() { return baseCode; }
    public void setBaseCode(String baseCode) { this.baseCode = baseCode; }

    public String getRatesJson() { return ratesJson; }
    public void setRatesJson(String ratesJson) { this.ratesJson = ratesJson; }

    public String getOverrideJson() { return overrideJson; }
    public void setOverrideJson(String overrideJson) { this.overrideJson = overrideJson; }

    public Date getFetchTime() { return fetchTime; }
    public void setFetchTime(Date fetchTime) { this.fetchTime = fetchTime; }

    public String getFetchStatus() { return fetchStatus; }
    public void setFetchStatus(String fetchStatus) { this.fetchStatus = fetchStatus; }

    public String getFetchError() { return fetchError; }
    public void setFetchError(String fetchError) { this.fetchError = fetchError; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
