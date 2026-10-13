package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 本站档案。对应表 {@code u_s_siteinfo}，<b>这张表只有一行</b>。
 *
 * <p>它是 ERP 那张 {@code u_s_site} 瘦身之后剩下的「站里的业务数据」那一半
 * （docs/独立站与ERP的边界.md §7.2）：站点编码、名称、语言、币种、前台开关、装修 JSON。
 * ERP 那边留下的是登记信息——域名、服务器 IP、跳转密钥、负责人。</p>
 *
 * <h3>为什么站点必须自己存一份 sitecode / sitename</h3>
 * <p>跳转令牌里的 {@code site} 声明要和 {@link #siteCode} 对上才放行（§4.3）。
 * 靠问 ERP 拿自己的身份，就又是一条互通了——而且是在验签之前就要发起的一条，
 * 等于把「ERP 必须在线」重新引回来。</p>
 *
 * <h3>status 为什么两边各留一份</h3>
 * <p>它们是<b>两个开关，不是一个值的副本</b>：ERP 那份管「这个站还在不在服务、还发不发
 * 跳转令牌」，这一份管「前台还开不开」。语言和币种则相反，只有这一份——两边都留就是
 * 两份会各自被改的配置，而且没有任何机制能发现它们不一致了。</p>
 */
public class SiteInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 站点状态：建设中。前台不对外 */
    public static final String STATUS_BUILDING = "建设中";
    /** 站点状态：运行中。唯一对外的状态 */
    public static final String STATUS_RUNNING = "运行中";
    /** 站点状态：已停用。前台不对外 */
    public static final String STATUS_DISABLED = "已停用";

    /** 一种语言都没配时按英语处理，外贸站的默认受众 */
    public static final String FALLBACK_LANG = "en";

    private Integer id;
    private String siteCode;
    private String siteName;
    /** 【遗留列】建站时那个商户码。新代码不得读它、不得按它过滤 */
    private String shopCode;
    /**
     * 主域名，存<b>主机名</b>（{@code tongling.vclinker.com}），不带协议头。
     *
     * <h3>⚠ 存主域，不是 buyer. 子域</h3>
     * <p>这一列有一阵子存的是 {@code buyer.kangchen.vclinker.com}——那时候前台整站
     * 都挂在 {@code buyer.} 上，主域连 nginx server 块都没有。<b>现在不是了</b>：
     * 主域是对外那个地址，{@code buyer.} 只留登录/注册/找回密码/重置密码/邮箱验证/
     * 账户这几页（{@code deploy/nginx/tongling.conf}）。</p>
     *
     * <p>两个消费方都要主域：管理台顶栏那个「访问前台」当然是主域；
     * 而买家验证信、找回密码信里的链接（{@code BuyerAuthService#siteBase}）
     * 指向 {@code https://<主域>/verify-email?token=…}，虽然那两页在 {@code buyer.} 上，
     * nginx 有一条 302 把它转过去、query 一起带过去。<b>所以后端不需要知道
     * {@code buyer.} 这个域名的存在</b>，这一列也就不用记两个值。</p>
     *
     * <p>和 ERP 的 {@code sdsshop.u_s_site.domain} 现在是<b>同一个语义</b>了（都是主域名）。
     * 那两列曾经一个存主域、一个存前台地址，照抄会出错——这个坑没了。</p>
     *
     * <p>⚠ 这里原来还写着「前台拼绝对地址（canonical / sitemap）用」，<b>不对</b>：
     * 前台的 canonical 和 hreflang 走 station-site 的 {@code NUXT_PUBLIC_SITE_URL}
     * （{@code useSeo.ts}），跟这一列没关系。照那句话理解，会以为它只是个 SEO 字段、
     * 空着无非少点收录。</p>
     *
     * <p>实际上全项目只有两个消费方，其中一个是<b>会坏的</b>：
     * {@code BuyerAuthService#siteBase} 用它拼买家验证邮件和找回密码邮件里的链接，
     * 空着直接抛 500——前台注册看起来一切正常，只是那封信永远不到。
     * 另一个是管理台的「访问前台」链接。</p>
     */
    private String domain;
    /** 开放的语言，逗号分隔，如 {@code en,ru,es}。第一个是站点默认语言 */
    private String langs;
    /**
     * <b>管理台</b>界面的默认语言（{@code zh} / {@code en} / {@code fr}）。空 = {@code zh}。
     *
     * <h3>⚠ 和 {@link #langs} 是两件事</h3>
     * <p>{@code langs} 是前台对<b>买家</b>开放哪几种语言，商品文案要按那些语言填；
     * 这一列是<b>管理台自己的界面</b>用什么语言画。一个只卖法国的站完全可以
     * {@code langs=fr} 而 {@code consoleLang=zh}（前台法语，后台我们自己运营），
     * 反过来也成立。看混的代价是：把它当成「又一个前台语言」去填，
     * 结果是管理台整屏变了语言而前台一点没变。</p>
     *
     * <h3>它是默认值，不是唯一值</h3>
     * <p>这正是这个设置存在的理由：法国站的站长看不懂中文，而我们的业务员要看中文，
     * <b>两个人用的是同一个管理台</b>。所以只有「浏览器里还没有自己的选择」的人
     * 按这一列走；每个人都能在顶栏切，切完存在他自己浏览器的 {@code localStorage}
     * 里，<b>不回写这一列</b>——回写的话，站长切一次法语，所有业务员的管理台
     * 跟着变法语。</p>
     *
     * <p>越界值一律当 {@code zh}，见 {@link #CONSOLE_LANGS}。</p>
     */
    private String consoleLang;
    /**
     * 前台视觉主题：{@code classic}（紫色 + Fraunces，康辰）或空 / {@code esva}（黑 + 电光蓝，法国站）。
     * 只决定 station-site 的 {@code <html data-theme>}，见 station-site/app/assets/theme-classic.css。
     * 管理台不改它，要换只能改库。
     */
    private String theme;
    /**
     * 结算币种。<b>这是基准</b>：商品价格 {@code u_s_sitegoods.price} 就是用它存的，
     * 订单也一律落它。
     */
    private String currency;
    /**
     * 【请求专用，<b>不是库里的列</b>】改 {@link #currency} 时要不要按汇率换算已有金额。
     *
     * <p>{@code true}（默认）：商品价、型号价、订单金额一起乘汇率，
     * 见 {@code CurrencyConvertService}。这是正确的默认值——改币种而不换算，
     * 等于把 £100 的商品静默变成 $100。</p>
     *
     * <p>{@code false}：只改这个三字母码，数字一个不动。<b>留这条路是因为有一种
     * 真实场景</b>：建站时币种就填错了（数字一直是按 USD 定的，码却写成 GBP），
     * 这时候要的是「改正标签」而不是「按汇率调价」——强制换算反而会把对的数字改错，
     * 而且不可逆。管理台的确认框把两条路摆在一起让运营选，默认落在换算上。</p>
     *
     * <p>⚠ 它没有对应的数据库列，{@code SiteInfoMapper.update} 的 SQL 里也没有它。
     * 加列的话会变成一个「上次保存时选了什么」的持久状态，而它描述的是<b>这一次操作</b>
     * 的意图，不是站点的属性。</p>
     */
    private Boolean convertAmounts;
    /**
     * 前台允许买家切换的<b>显示</b>币种，逗号分隔。
     *
     * <p>⚠ 和上面的 {@link #currency} 只差一个字母，但不是一回事，看错的代价很实在：
     * 那个是「价格用什么存」，这个是「买家能切到哪些」。换算率在 {@code u_s_rate}。</p>
     *
     * <p>留空 = 前台不显示切换器，退回没有这个功能时的样子。基准币种由
     * {@link #currencyList()} 自动并入，运营不填它也不会把买家锁在外面。</p>
     */
    private String currencies;
    private String status;
    /**
     * 服务到期日。{@code null} = 不提醒。
     *
     * <p>⚠ ERP 的 {@code u_s_site} 里也有一个 {@code expiredate}，<b>两份不同步</b>——
     * 和域名一样，两个库之间没有通道。ERP 那份是我们自己的收费台账（独立站列表按剩余
     * 天数排序，快到期的顶上来），这一份是给站长在自己管理台上看的。改一边不会动另一边。</p>
     *
     * <p>这一列是 2026-09-07 补的。在此之前管理台首页那个「服务到期」统计块引用的是一个
     * 后端从来没下发过的字段，从拆分上线起就一直显示「未设置」——那段代码是从
     * 「ERP 内置管理台」那一版搬来的，那时候整个 site 对象来自 ERP、自带到期日，
     * 搬到独立部署之后数据源没跟过来，而页面上看着只像是「这个站没填到期日」。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expireDate;
    /**
     * <b>已发布</b>的首页装修内容 JSON（v4：结构在共享层、文案分语言）。
     * 为空时接口返回内置默认。未发布的改动在 {@link #draftJson}。
     */
    private String contentJson;
    /**
     * 装修草稿。结构和 {@link #contentJson} 完全一样，{@code null} = 没有未发布的改动。
     *
     * <p>管理台「保存草稿」只写这一列，前台仍然渲染 {@code contentJson}；「发布」才把
     * 这一份拷过去并把本列置空。所以商家可以在**线上站**上慢慢改，改到一半也不会见客。</p>
     *
     * <p>⚠ 公开接口一律不下发这一列，见 {@code SiteInfoService#requirePublic}。
     * 草稿里可能有还没定稿的价格、还没上线的活动文案——那些不该被访客从
     * {@code /openapi/site/info} 里翻出来。</p>
     */
    private String draftJson;
    /** 草稿最后一次保存的人。和 {@link #updateUser} 分开，那个是整份档案的更新人 */
    private String draftUser;
    /** 草稿最后一次保存的时间。管理台保存条上显示「草稿保存于 …」 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date draftTime;
    /** 询盘通知邮箱，多个用逗号分隔。留空则收到询盘只入库不发信 */
    private String notifyEmail;
    /**
     * 从 ERP 跳过来的令牌验签密钥。
     *
     * <p><b>{@code @JsonIgnore} 不能去掉。</b>这个对象会被 {@code /api/site} 直接序列化给
     * 管理台前端——密钥跟着下发到浏览器，等于任何一个能进管理台的人都能伪造别人的跳转令牌。</p>
     */
    @JsonIgnore
    private String consoleSecret;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /**
     * 距到期还有多少天。负数是已过期，{@code null} 是没填到期日。
     *
     * <p>没有对应的列，跟着 {@link #expireDate} 一起序列化给管理台，前端据此决定
     * 标黄（≤30 天）还是标红（已过期）。</p>
     *
     * <h3>为什么在 Java 里现算，而不像 ERP 那样用 SQL 的 datediff</h3>
     * <p>不是风格问题。{@code SiteInfoService} 把这个对象<b>缓存在进程里</b>，只在写操作
     * 之后失效，而站点档案几个星期不写一次很正常。SQL 算出来的天数会连同对象一起冻在
     * 加载的那一刻——页面上「还有 30 天到期」挂着不动，真到期了也不变红。
     * 而这块提醒的全部价值就是那个会走的数字。ERP 那边没这个问题，它每次都查库。</p>
     *
     * <p>⚠ 不要写成 {@code expireDate.toInstant()}：MyBatis 把 DATE 列映射成
     * {@code java.sql.Date}，它的 {@code toInstant()} 是<b>直接抛 UnsupportedOperationException</b>
     * 的（JDK 里就那么写的）。字段声明成 {@code java.util.Date} 也拦不住，
     * 运行期进来的还是子类。走 {@code getTime()} 两种都安全。</p>
     */
    public Integer getExpireDays()
    {
        if (expireDate == null)
        {
            return null;
        }
        LocalDate day = Instant.ofEpochMilli(expireDate.getTime())
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), day);
    }

    /** 前台还开不开。只有「运行中」对外 */
    @JsonIgnore
    public boolean isPublicOpen()
    {
        return STATUS_RUNNING.equals(status);
    }

    /**
     * 前台<b>真正支持</b>的语言。{@code langs} 列里超出这个集合的值一律被丢掉。
     *
     * <h3>为什么必须是白名单</h3>
     *
     * <p>{@code langs} 是一列自由文本，以前不校验。填 {@code fr,de,tr} 进去的结果是：
     * 前台语言切换器显示漂亮的「Français」、{@code /fr/products} 这个路由通、
     * hreflang 也如实发给了 Google——但</p>
     * <ul>
     *   <li>整站界面文案退回英文（前台的 {@code useShopCopy} 里没有这个语言的文案表）；</li>
     *   <li>金额和日期按 {@code en-US} 格式化（{@code intlLocale} 里没有这个映射）；</li>
     *   <li>Google 收到「这里有法语版本」的承诺，抓到的却是一个英文页面。</li>
     * </ul>
     *
     * <p>也就是「看起来支持 18 种，实际支持 8 种」。少支持一种语言只是少一块生意；
     * <b>假装支持一种语言是同时给买家和 Google 发错信号</b>，比不支持糟得多。
     * 所以在最靠里的这一层砍掉——{@link #langList()} 是站点信息接口和管理台装修页
     * 共用的那个出口，从这里砍能保证两边看到的是同一份。</p>
     *
     * <h3>⚠ 这份清单和另外两份必须一致</h3>
     * <p>前台的真相是 {@code station-site/app/composables/useShopCopy.ts} 里
     * {@code COPY} 这个 map 的键（文件末尾，紧挨着 {@code useShopCopy}）——
     * 有键才有那个语言的界面文案表。只改这一边的话，前台会拿到一个它没有文案表的语言，
     * 就又回到上面那个状态。</p>
     * <p>管理台的候选列表是第三份：{@code station-console-ui/src/views/Overview.vue}
     * 的 {@code langOptions}。它比这里多出过 de/fr/ar/vi/th，表现是「选得上、提示已保存、
     * 但前台一直没有」——三份任何时候都要一起改。</p>
     *
     * <p><b>RTL 语言（ar / he / fa）不在也不能在这份清单里</b>：前台整站没有任何
     * {@code dir="rtl"} 处理，加进来版式是错的。要支持得先把 dir 那条线做出来。</p>
     */
    private static final List<String> SUPPORTED_LANGS =
            List.of("en", "zh", "ja", "es", "pt", "ru", "it", "ko", "fr");

    /**
     * 站点开了哪些语言。全小写去重，默认语言排第一个
     * （{@code langs} 列本来就是这个顺序）。
     *
     * <p>不在 {@link #SUPPORTED_LANGS} 里的一律丢掉，全被丢掉或者一个都没配时
     * 返回 {@code [en]}——前台至少有一种能正常显示的语言。</p>
     */
    @JsonIgnore
    public List<String> langList()
    {
        List<String> result = new ArrayList<>();
        if (langs != null && !langs.trim().isEmpty())
        {
            for (String item : langs.split(","))
            {
                String lang = item == null ? null : item.trim();
                if (lang == null || lang.isEmpty())
                {
                    continue;
                }
                lang = lang.toLowerCase(Locale.ROOT);
                // 见 SUPPORTED_LANGS 的注释：没翻译的语言不能放出去，
                // 放出去等于给访客一个通向英文页面的语言入口
                if (SUPPORTED_LANGS.contains(lang) && !result.contains(lang))
                {
                    result.add(lang);
                }
            }
        }
        if (result.isEmpty())
        {
            result.add(FALLBACK_LANG);
        }
        return result;
    }

    /**
     * 库里配了、但前台不支持的语言。
     *
     * <p>只给启动自检和管理台提示用（「你配的 fr 前台还不支持，已忽略」）——
     * 静默丢掉的话，运营会一直以为法语站开着，而访客看到的一直是英文。
     * 这是个诊断出口，业务逻辑一律用 {@link #langList()}。</p>
     */
    @JsonIgnore
    public List<String> unsupportedLangList()
    {
        List<String> result = new ArrayList<>();
        if (langs == null || langs.trim().isEmpty())
        {
            return result;
        }
        for (String item : langs.split(","))
        {
            String lang = item == null ? null : item.trim().toLowerCase(Locale.ROOT);
            if (lang != null && !lang.isEmpty()
                    && !SUPPORTED_LANGS.contains(lang) && !result.contains(lang))
            {
                result.add(lang);
            }
        }
        return result;
    }

    /** 管理台没配语言时说中文：它的日常使用者是我们自己的业务员 */
    public static final String CONSOLE_LANG_FALLBACK = "zh";

    /**
     * 管理台<b>有界面文案表</b>的语言。
     *
     * <p>和 {@link #SUPPORTED_LANGS} 是同一个道理、不同一份清单：那一份问的是
     * 「<b>前台</b>有没有这个语言的文案」，这一份问的是「<b>管理台</b>有没有」。
     * 管理台的文案表在 {@code station-console-ui/src/i18n/locales/}，眼下是
     * zh / en / fr 三份；前台那边是九种。两份不该去凑成一份——它们是两个界面
     * 各自翻到哪儿了，硬凑的结果必然是其中一边在撒谎。</p>
     *
     * <p>⚠ 管理台加一种语言要同时改三处：这个常量、{@code src/i18n/index.js} 的
     * {@code LOCALES}、以及新增一份 {@code locales/xx.js}。只改这里的表现是
     * 「站点设置里选得上、提示已保存、进去还是中文」——和 langs 越界时
     * 那个坑一模一样。</p>
     */
    private static final List<String> CONSOLE_LANGS = List.of("zh", "en", "fr");

    /**
     * 管理台界面的默认语言。没配、或配了一个没有文案表的语言码时是 {@code zh}。
     *
     * <p>越界值不抛异常也不留痕：这一列只决定界面画成什么样，兜回中文之后
     * 管理台照常能用，为它把整个站点档案读失败是本末倒置。</p>
     */
    @JsonIgnore
    public String consoleLangOrDefault()
    {
        String lang = consoleLang == null ? "" : consoleLang.trim().toLowerCase(Locale.ROOT);
        return CONSOLE_LANGS.contains(lang) ? lang : CONSOLE_LANG_FALLBACK;
    }

    /** 站点默认语言。文案回退链的最后一环 */
    @JsonIgnore
    public String defaultLang()
    {
        return langList().get(0);
    }

    /** 基准币种。没配时按 USD——外贸站的默认结算币种 */
    @JsonIgnore
    public String baseCurrency()
    {
        return currency == null || currency.trim().isEmpty()
                ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 买家在前台能切到哪些币种。<b>基准币种恒在第一个</b>。
     *
     * <p>全大写去重。基准币种无条件并入——运营在 {@code currencies} 里漏填它是常态
     * （那一栏问的是「还想让买家看到哪些」），漏填就把买家锁在外币里、切不回本币，
     * 是这里最容易出的错。</p>
     *
     * <p>只配了基准币种一种时返回单元素列表，前台据此不渲染切换器，
     * 与语言切换器「只开一种就不显示」的行为一致。</p>
     *
     * <p>⚠ 这里<b>不做白名单校验</b>。和语言不同：语言超出白名单前台是真的坏了
     * （没有文案表，整站退回英文），而币种只要是合法 ISO 4217 码，前台的
     * {@code Intl.NumberFormat} 就渲染得出来。能不能换算取决于 {@code u_s_rate}
     * 里有没有这个币种的汇率，那是 {@code RateService} 的判断，不在这一层。</p>
     */
    @JsonIgnore
    public List<String> currencyList()
    {
        List<String> result = new ArrayList<>();
        result.add(baseCurrency());
        if (currencies != null && !currencies.trim().isEmpty())
        {
            for (String item : currencies.split(","))
            {
                String code = item == null ? null : item.trim().toUpperCase(Locale.ROOT);
                if (code != null && !code.isEmpty() && !result.contains(code))
                {
                    result.add(code);
                }
            }
        }
        return result;
    }

    /**
     * 把请求上的语言归一到本站开着的那几种之一。
     *
     * <p>不在列表里就退回默认语言，别让人用 {@code ?lang=xx} 探测数据。</p>
     */
    @JsonIgnore
    public String resolveLang(String want)
    {
        if (want == null || want.trim().isEmpty())
        {
            return defaultLang();
        }
        String lang = want.trim().toLowerCase(Locale.ROOT);
        return langList().contains(lang) ? lang : defaultLang();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    /**
     * {@link #domain} 规范化成一个能直接拼路径的站点根地址（{@code https://xxx}，不带尾斜杠）。
     * <b>没登记域名返回 {@code null}</b>。
     *
     * <h3>⚠ 方法名故意不叫 {@code getPublicBase}</h3>
     * <p>这个对象会被 {@code ConsoleApiController} 原样序列化给管理台。叫 {@code getXxx}
     * 的话 Jackson 会当成一个属性跟着输出——多一个没人要的字段事小，
     * 但它是<b>算出来的</b>，域名空着时序列化会多一个 {@code "publicBase": null}，
     * 看起来像库里真有这么一列。</p>
     *
     * <h3>⚠ 返回 null 而不是抛异常</h3>
     * <p>两个调用方对「没登记域名」的处理是<b>相反</b>的，所以判断留给它们：</p>
     * <ul>
     *   <li>{@code BuyerAuthService#siteBase} 拼的是验证信/重置信里的链接，
     *       那封信没有链接就等于没发 —— 它拿到 null 要抛 500，让装机的人去填；</li>
     *   <li>{@code BuyerMailer#sendOrderReceiptAfterCommit} 拼的是下单确认信里的
     *       「查看这张单」链接，那只是锦上添花 —— 它拿到 null 就<b>不放链接，照常发信</b>。
     *       为了一个链接把确认信整封吞掉是本末倒置。</li>
     * </ul>
     */
    public String publicBase()
    {
        String value = domain == null ? null : domain.trim();
        if (value == null || value.isEmpty())
        {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://"))
        {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
        return "https://" + value;
    }

    public String getLangs() { return langs; }
    public void setLangs(String langs) { this.langs = langs; }

    public String getConsoleLang() { return consoleLang; }
    public void setConsoleLang(String consoleLang) { this.consoleLang = consoleLang; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean getConvertAmounts() { return convertAmounts; }
    public void setConvertAmounts(Boolean convertAmounts) { this.convertAmounts = convertAmounts; }

    /**
     * 这次保存要不要按汇率换算已有金额。<b>缺省是要</b>。
     *
     * <p>默认值落在「换算」这一边是有意的：请求里没带这个字段的调用方
     * （老版本前端、脚本、手工 curl）拿到的是正确行为，而不是那个把 £100
     * 静默变成 $100 的旧行为。想跳过换算必须<b>显式</b>送 {@code false}。</p>
     */
    public boolean shouldConvertAmounts()
    {
        return convertAmounts == null || convertAmounts;
    }

    public String getCurrencies() { return currencies; }
    public void setCurrencies(String currencies) { this.currencies = currencies; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }

    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }

    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String draftJson) { this.draftJson = draftJson; }

    public String getDraftUser() { return draftUser; }
    public void setDraftUser(String draftUser) { this.draftUser = draftUser; }

    public Date getDraftTime() { return draftTime; }
    public void setDraftTime(Date draftTime) { this.draftTime = draftTime; }

    /** 有没有未发布的改动。管理台靠它在菜单和保存条上标「未发布」 */
    public boolean isHasDraft()
    {
        return draftJson != null && !draftJson.trim().isEmpty();
    }

    public String getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail; }

    public String getConsoleSecret() { return consoleSecret; }
    public void setConsoleSecret(String consoleSecret) { this.consoleSecret = consoleSecret; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
