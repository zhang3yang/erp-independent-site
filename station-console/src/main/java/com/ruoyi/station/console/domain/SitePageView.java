package com.ruoyi.station.console.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次前台浏览。对应 {@code u_s_pageview} 的一行。
 *
 * <p>表结构和全部设计决定在 {@code sql/station/migration/2026-09-08-pageview.sql}
 * 的文件头里，改这个类之前先读它。</p>
 *
 * <h3>⚠ 这个对象不是从请求体反序列化出来的</h3>
 * <p>前台上报的字段只有 {@code path / type / goodsCode / lang / referrer} 五个，
 * 收在 {@code OpenApiController.PageViewReport} 那个 record 里。这个类由
 * {@link com.ruoyi.station.console.service.PageViewService} 组装——
 * {@code visitorId} 来自 Cookie、{@code clientIp} 来自转发头、{@code isBot} 是判定结果、
 * {@code siteCode} 来自站点档案，<b>一个都不接受前端传</b>。</p>
 *
 * <p>照的是 {@code SiteOrderSubmit} 那条同样的规矩：公网接口不要直接收 domain 对象，
 * 否则往这个类上加字段的人不会想起「它同时是一个公网入参」。</p>
 *
 * <h3>⚠ 没有 shopCode</h3>
 * <p>老 domain 都有。新表没建这一列——见基线脚本末尾那段：
 * 「新代码不得读它、不得按它过滤」。</p>
 */
public class SitePageView
{
    private Long id;

    /** 站点编码。由服务端从站点档案取，不接受前端传 */
    private String siteCode;

    /**
     * 访客标识，{@code SITE_VISITOR} Cookie 里那个随机串。
     *
     * <p>⚠ UV 的唯一依据。清了 Cookie 或换个浏览器就是一个新访客——
     * 这是固有误差，别试图用 IP+UA 去「修正」它（那反而是在做指纹追踪）。</p>
     */
    private String visitorId;

    /** 会话标识，{@code SITE_SESSION} Cookie。30 分钟不活动就换一个 */
    private String sessionId;

    /** 页面类型。⚠ 服务端按白名单校验过，不认识的一律是 {@code other} */
    private String pageType;

    /**
     * 看的哪个商品。
     *
     * <p>⚠ 非详情页是<b>空串不是 null</b>：它进 {@code u_s_pv_daily} 的主键，
     * 而 NULL 进不了主键。两张表用同一个约定。</p>
     */
    private String goodsCode;

    /** 页面路径，带语言前缀。不含 query string */
    private String pagePath;

    /** 访客在看哪个语种的页面 */
    private String lang;

    /** 来源页。区分「搜索进来的」和「直接输网址的」，判断 SEO 有没有起作用就靠它 */
    private String referrer;

    /**
     * ⚠ <b>截断过的</b> IP，不是完整 IP。
     *
     * <p>IPv4 去掉最后一段、IPv6 只留前 48 位。和 {@code SiteChat.clientIp} 不一样，
     * 理由见 migration 脚本文件头那段——那两张表存的是「主动联系过我们的人」的业务记录，
     * 这张表存的是匿名浏览行为。</p>
     */
    private String clientIp;

    /** 国家二字码。⚠ 现在恒为 null，没接 IP 归属库。留着是为了将来接了不用改表 */
    private String country;

    /** 浏览器标识。留着是为了事后能重新判定爬虫和设备——判定规则会改，原始 UA 不会 */
    private String userAgent;

    /**
     * 设备类型：{@code mobile} / {@code tablet} / {@code desktop}。
     *
     * <p>由 {@link com.ruoyi.station.console.service.UserAgentParser} 从 {@link #userAgent}
     * 解析，<b>不采集新数据</b>。⚠ 这一列不进 {@code u_s_pv_daily}，
     * 所以设备分布只能查保留期以内。</p>
     */
    private String device;

    /** 浏览器。⚠ 判定顺序敏感，见 {@code UserAgentParser} 的类注释 */
    private String browser;

    /** 1=判定为爬虫。⚠ 照样入库，只在统计时排除 */
    private Integer isBot;

    private LocalDateTime createTime;

    /**
     * 这一条算哪一天，按 {@code Asia/Shanghai} 切。
     *
     * <p>⚠ <b>不是 {@code u_s_pageview} 的列</b>，库里那张表只有 {@code createtime}。
     * 它是给 {@code u_s_pv_daily} 的 upsert 用的：日期在 Java 里算一次、
     * 两处写入用同一个值，而不是让 SQL 各自 {@code DATE(createtime)} ——
     * 后者用的是数据库连接的时区，和这里的东八区不一定是同一个东西。</p>
     */
    private LocalDate statDate;

    /**
     * 完整的客户端 IP，<b>只用来查国家，绝不落库</b>。
     *
     * <p>⚠ <b>这不是 {@code u_s_pageview} 的列</b>，和上面的 {@link #statDate} 一样。
     * 库里那一列是 {@link #clientIp}，存的是<b>截断过的</b>值。</p>
     *
     * <p>为什么要多带一个字段：国家查询要查库，所以只能在写线程做
     * （请求线程一次都不该碰库）；而到了写线程，{@code clientIp} 已经是截断后的了。
     * 所以把完整值单独带过去，用完就随对象一起丢掉。</p>
     *
     * <p>⚠ <b>别把它加进 mapper 的 insert 列表</b>。加进去等于把完整 IP 落了库，
     * 而「这张表只存截断 IP」是整套设计的隐私前提
     * （见 migration 脚本里 clientip 那一段）。</p>
     */
    private String lookupIp;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public Integer getIsBot() { return isBot; }
    public void setIsBot(Integer isBot) { this.isBot = isBot; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public String getLookupIp() { return lookupIp; }
    public void setLookupIp(String lookupIp) { this.lookupIp = lookupIp; }
}
