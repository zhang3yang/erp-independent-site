package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 站长工具配置。对应表 {@code u_s_seo}，只有一行。
 *
 * <h3>这个模块补的是什么</h3>
 * <p>前台的 SEO 基建本来就不薄：{@code useSeo.ts} 有 canonical + hreflang + og，
 * {@code sitemap.xml.ts} 把每个商品每种语言都列出来，{@code robots.txt.ts} 还按域
 * 区分了买家中心。所以这里<b>不是从零做 SEO</b>，是把那些写死在前台代码里的规则
 * 搬到库里让站长能改，再加上验证码和统计代码的注入位。</p>
 *
 * <h3>⚠⚠ 这个对象整体是公开的</h3>
 * <p>每一个字段最终都会出现在匿名接口 {@code /openapi/site/info} 的返回里
 * （{@code SiteOpenService#siteInfo} 里那个 {@code seo} 子对象）。必须如此：
 * sitemap 和 robots 是 <b>station-site（Nitro）生成的</b>，不是后端生成的，
 * 所以连 {@link #sitemapMax} 这种看着像后端参数的东西也得发给前台。</p>
 *
 * <p>于是这条约束是硬的：<b>将来要存 GSC 的 API key、第三方 token 之类的东西，
 * 必须另建一张表。</b>加在这里不报错，只是把密钥公开在一个谁都能 curl 的接口上，
 * 而且没有任何现象提示。</p>
 *
 * <h3>为什么统计只存 ID，不存代码</h3>
 * <p>几乎所有建站产品都给一个「自由粘贴统计代码」的文本框。那是一个真的存储型 XSS
 * 入口：任何能进管理台的人都能在<b>每一个公开页面</b>上执行任意 JavaScript，
 * 包括偷买家 Cookie 和改结账页。所以这里只存 ID，{@code <script>} 标签由
 * {@code station-site/server/plugins/seo-head.ts} 自己拼。</p>
 *
 * <p>代价是接一个新平台（Clarity、Hotjar、Meta Pixel）要改代码。这个代价我们认。</p>
 */
public class SiteSeo implements Serializable
{
    private static final long serialVersionUID = 1L;

    // ==================== 格式校验 ====================
    //
    // ⚠ 这几个正则在**两处**存在：这里（保存时校验）和 seo-head.ts（输出时校验）。
    //   改一处必须改另一处。
    //
    //   输出时那一次不是多余的防御：库里的值可能是别人用 SQL 直接改的（部署时手工
    //   补配置是常态），而输出端正是把这个值拼进 <script> 的地方。「只存 ID」这个
    //   安全前提，靠的就是输出端那一次校验——不是靠保存时这一次。

    /** GA4 测量 ID：G- 加大写字母数字。官方现在是 10 位，放宽到 4~20 位免得以后改规则 */
    public static final Pattern GA4_ID = Pattern.compile("^G-[A-Z0-9]{4,20}$");

    /** GTM 容器 ID：GTM- 加大写字母数字 */
    public static final Pattern GTM_ID = Pattern.compile("^GTM-[A-Z0-9]{4,12}$");

    /** 百度统计 hm.js 的 id，32 位小写十六进制 */
    public static final Pattern BAIDU_HM_ID = Pattern.compile("^[0-9a-f]{32}$");

    /**
     * 站点验证码。
     *
     * <p>三家的格式不统一（Google 是 43 位 base64url，Bing 是 32 位十六进制，
     * 百度是十来位字母数字），所以这里不按厂商分别写正则，只卡一个<b>安全字符集</b>：
     * 字母数字加 {@code - _ = .}。</p>
     *
     * <p>这个字符集足以排掉所有能逃出 HTML 属性的字符（引号、尖括号、空格），
     * 而那正是这道校验唯一要防的事——它防的不是「填了个无效的验证码」
     * （那种情况的后果只是验证不通过，看得见、改得回来），
     * 而是「往 {@code <meta>} 标签里注入属性」。</p>
     */
    public static final Pattern VERIFY_TOKEN = Pattern.compile("^[A-Za-z0-9\\-_=.]{8,100}$");

    /** changefreq 的合法取值，sitemap 协议定死的那几个 */
    private static final Set<String> FREQ_VALUES = Set.of(
            "always", "hourly", "daily", "weekly", "monthly", "yearly", "never");

    /**
     * {@link #sitemapMax} 的上限。
     *
     * <p>sitemap 单文件规范上限是 5 万条 <b>URL</b>，而这一列是<b>商品条数</b>——
     * 一个商品乘以语言数（最多 8）才是 URL 数。所以商品数封在 6000：
     * 8 种语言时约 4.8 万条，刚好在规范之内。</p>
     */
    public static final int SITEMAP_MAX_CEILING = 6000;

    private Integer id;

    // ==================== 收录 ====================

    /**
     * 整站禁止收录。
     *
     * <p>⚠ 和 {@code u_s_siteinfo.status} 是<b>两个独立的决定</b>，不是同一个值的副本。
     * {@code status=建设中/已停用} 会让前台整个不对外（{@link SiteInfo#isOpen()} 只认
     * 「运行中」），那时候谈收录没有意义。这一列管的是另一件事：<b>站开着、对访客正常，
     * 但先别收录</b>——内容还没铺完就要给客户看的软上线阶段。</p>
     *
     * <p>⚠ 打开之后前台在三处生效，缺一处都不算数：{@code robots.txt} 输出
     * {@code Disallow: /}、每个页面加 {@code noindex} meta、{@code sitemap.xml} 返回 404。
     * 前两处的分工见 {@code robots.txt.ts} 里那段注释：{@code Disallow} 管「别来爬」，
     * {@code noindex} 管「爬到了别收」，被外链指到的地址只有后者拦得住。</p>
     */
    private Integer noindexAll;

    /**
     * 额外的 Disallow 路径，逗号分隔。
     *
     * <p>⚠ 这是<b>追加</b>，不是替换。{@code robots.txt.ts} 里那批硬编码
     * （{@code cart} / {@code checkout} / {@code orders}，各带一份语言前缀版）
     * <b>故意留在代码里</b>：结账页不该被收录是这个站的结构性事实，不是运营偏好。
     * 搬进库的唯一效果，是某天有人把它们删掉，然后结账页开始出现在搜索结果里。</p>
     */
    private String disallowPaths;

    /** robots.txt 末尾原样追加的内容（Crawl-delay、针对某个 UA 的段落等） */
    private String robotsExtra;

    // ==================== sitemap ====================

    private Integer sitemapOn;

    /**
     * 进 sitemap 的商品条数上限。
     *
     * <p>⚠ 不是 URL 条数：每个商品会乘以语言数。8 种语言、5000 个商品 = 4 万条 URL。</p>
     *
     * <p>现状是硬编码封在 540 条（{@code size=60} × 最多 9 页），而那个文件的注释自己
     * 写着「真超过 500 个商品时要改成分片 sitemap」。这一轮<b>不做分片</b>：单文件规范
     * 上限 5 万条 URL，这个站离得很远，为一个还不存在的问题引入一层索引文件不值得。</p>
     */
    private Integer sitemapMax;

    private String homeFreq;
    private String goodsFreq;

    // ==================== 站点验证 ====================
    //
    // ⚠ 只存**验证码那个值**，不存整个 <meta> 标签。前台自己拼标签。
    //   存整个标签就等于开了一个「往 <head> 里塞任意 HTML」的口子。

    private String googleVerify;
    private String bingVerify;
    private String baiduVerify;

    // ==================== 统计 ====================

    private String ga4Id;

    /** ⚠ 和 {@link #ga4Id} 同时填大概率重复计数——GTM 容器里通常也挂着一个 GA4 代码 */
    private String gtmId;

    /** ⚠ {@code hm.baidu.com} 对海外访客慢甚至连不上，而这个站的访客在境外。默认留空 */
    private String baiduHmId;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 一份全默认值的配置。
     *
     * <p><b>这个方法是「老库没跑迁移」和「跑了但没配」表现一致的原因。</b>
     * 查不到那一行时返回它，而它的每一个值都等于<b>这张表不存在时前台的原有行为</b>：
     * 允许收录、生成 sitemap、首页 daily、商品 weekly、什么都不注入。</p>
     *
     * <p>所以缺那一行不是故障，不该抛异常——抛了只会让一个还没跑迁移的老库
     * 整个管理台打不开，而它本来只是少一个可选功能。</p>
     */
    public static SiteSeo defaults()
    {
        SiteSeo seo = new SiteSeo();
        seo.id = 1;
        seo.noindexAll = 0;
        seo.sitemapOn = 1;
        seo.sitemapMax = 5000;
        seo.homeFreq = "daily";
        seo.goodsFreq = "weekly";
        return seo;
    }

    /**
     * 把可能为 null 或越界的值补成合法值。查出来之后、发出去之前都要过一遍。
     *
     * <p>⚠ 为什么需要它：那几列在表上是 {@code NOT NULL DEFAULT}，但老库可能是用别的
     * 方式建的，而且 {@code Integer} 字段在 MyBatis 映射不到列时也是 null。少了这一步，
     * {@code sitemapMax} 为 null 会在前台变成 {@code undefined}，然后翻页圈数算成
     * {@code NaN}——sitemap 里一个商品都没有，<b>而且不报错</b>。</p>
     */
    public SiteSeo normalized()
    {
        if (noindexAll == null) noindexAll = 0;
        if (sitemapOn == null) sitemapOn = 1;
        if (sitemapMax == null || sitemapMax <= 0) sitemapMax = 5000;
        if (sitemapMax > SITEMAP_MAX_CEILING) sitemapMax = SITEMAP_MAX_CEILING;
        if (homeFreq == null || !FREQ_VALUES.contains(homeFreq)) homeFreq = "daily";
        if (goodsFreq == null || !FREQ_VALUES.contains(goodsFreq)) goodsFreq = "weekly";
        return this;
    }

    /** changefreq 的取值合法吗。保存时用 */
    public static boolean validFreq(String freq)
    {
        return freq != null && FREQ_VALUES.contains(freq);
    }

    /**
     * {@link #disallowPaths} 拆成一条条路径。
     *
     * <p>去空、去重、保持填写顺序（{@code LinkedHashSet}）。不以 {@code /} 开头的
     * 自动补上——运营填 {@code promo} 而不是 {@code /promo} 是最常见的写法，
     * 而 robots.txt 里不带斜杠的那一条<b>不报错、只是不生效</b>。</p>
     */
    public List<String> disallowList()
    {
        List<String> out = new ArrayList<>();
        if (disallowPaths == null || disallowPaths.isBlank()) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : disallowPaths.split(","))
        {
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (!p.startsWith("/")) p = "/" + p;
            seen.add(p);
        }
        out.addAll(seen);
        return out;
    }

    /**
     * 整站禁收录开着吗。
     *
     * <h3>⚠ 方法名刻意不叫 {@code isNoindexAll}</h3>
     * <p>那个名字会被 Jackson 当成 bean getter，于是 {@code noindexAll} 这个属性有
     * <b>两个访问器</b>（{@code getNoindexAll()} 回 Integer、{@code isNoindexAll()}
     * 回 boolean），序列化出来的类型取决于 Jackson 挑中哪一个——而这个对象会原样
     * 出现在公开接口里，前台判的是 {@code === 1}。哪天它变成 {@code true}，
     * 前台那个判断会静默失效：禁收录开着，但页面上没有 noindex。</p>
     *
     * <p>不用 {@code @JsonIgnore} 解决：那个注解加在同名属性的一个访问器上，
     * Jackson 会把<b>整个属性</b>屏蔽掉（经典的坑），前台就一个字段都拿不到了。
     * 换个不是 getter 形状的名字最省事，也不需要注解。</p>
     */
    public boolean noindexAllOn() { return noindexAll != null && noindexAll == 1; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getNoindexAll() { return noindexAll; }
    public void setNoindexAll(Integer noindexAll) { this.noindexAll = noindexAll; }

    public String getDisallowPaths() { return disallowPaths; }
    public void setDisallowPaths(String disallowPaths) { this.disallowPaths = disallowPaths; }

    public String getRobotsExtra() { return robotsExtra; }
    public void setRobotsExtra(String robotsExtra) { this.robotsExtra = robotsExtra; }

    public Integer getSitemapOn() { return sitemapOn; }
    public void setSitemapOn(Integer sitemapOn) { this.sitemapOn = sitemapOn; }

    public Integer getSitemapMax() { return sitemapMax; }
    public void setSitemapMax(Integer sitemapMax) { this.sitemapMax = sitemapMax; }

    public String getHomeFreq() { return homeFreq; }
    public void setHomeFreq(String homeFreq) { this.homeFreq = homeFreq; }

    public String getGoodsFreq() { return goodsFreq; }
    public void setGoodsFreq(String goodsFreq) { this.goodsFreq = goodsFreq; }

    public String getGoogleVerify() { return googleVerify; }
    public void setGoogleVerify(String googleVerify) { this.googleVerify = googleVerify; }

    public String getBingVerify() { return bingVerify; }
    public void setBingVerify(String bingVerify) { this.bingVerify = bingVerify; }

    public String getBaiduVerify() { return baiduVerify; }
    public void setBaiduVerify(String baiduVerify) { this.baiduVerify = baiduVerify; }

    public String getGa4Id() { return ga4Id; }
    public void setGa4Id(String ga4Id) { this.ga4Id = ga4Id; }

    public String getGtmId() { return gtmId; }
    public void setGtmId(String gtmId) { this.gtmId = gtmId; }

    public String getBaiduHmId() { return baiduHmId; }
    public void setBaiduHmId(String baiduHmId) { this.baiduHmId = baiduHmId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
