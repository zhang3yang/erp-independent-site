package com.ruoyi.station.console.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.domain.SitePost;
import com.ruoyi.station.console.domain.SiteRate;
import com.ruoyi.station.console.domain.SiteSeo;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;
import com.ruoyi.station.console.mapper.SiteInquiryMapper;
import com.ruoyi.station.console.mapper.SitePostMapper;

/**
 * 前台开放接口。供 {@code /openapi/site/**} 使用，Nuxt 前台从这里取数。
 *
 * <p><b>这是本服务唯一对公网开放的一组数据出口</b>，所以有三条硬规矩：</p>
 * <ol>
 *   <li>只读<b>已上架</b>的商品。草稿和已下架的连标题都不能漏出去；</li>
 *   <li>返回的字段是白名单，一个个手工装进 Map，<b>绝不把数据库实体直接序列化出去</b>——
 *       实体上多一个字段，接口就多漏一份东西；</li>
 *   <li>唯一的写操作是提交询盘，且只能写进询盘表。</li>
 * </ol>
 *
 * <h3>⚠ siteKey 没有了</h3>
 * <p>ERP 那版每个方法的第一个参数都是 {@code siteKey}：前台带着它来取数，
 * ERP 按它查出是哪个站、能不能对外。那是「一个 ERP 服务 N 个站」时代的产物。</p>
 *
 * <p>现在<b>一个部署一个站，前台取的是自己这台机器上的后端</b>，没有范围要限定
 * （docs/独立站与ERP的边界.md §7.2：{@code sitekey} 和 {@code apienabled} 两列都删）。
 * 站点身份由 {@link SiteInfoService} 从本库读，「能不能对外」由
 * {@link SiteInfo#isPublicOpen()} 一个开关决定。</p>
 */
@Service
public class SiteOpenService
{
    private static final Logger log = LoggerFactory.getLogger(SiteOpenService.class);

    /** 每页条数上限。不夹一下的话，一个 size=999999 就能把整库拉走 */
    private static final int MAX_PAGE_SIZE = 60;
    private static final int DEFAULT_PAGE_SIZE = 12;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    @Autowired
    private SiteInquiryMapper inquiryMapper;

    @Autowired
    private CodeGenerator codeGenerator;

    @Autowired
    private InquiryNotifier inquiryNotifier;

    @Autowired
    private RateService rateService;

    @Autowired
    private SitePostMapper postMapper;

    @Autowired
    private SiteSeoService siteSeoService;

    @Autowired
    private SiteCategoryService siteCategoryService;

    @Autowired
    private OrderWizardService orderWizardService;

    /** 站点信息：公司名、导航、Banner、能力介绍、联系方式、商品分类、站长工具配置 */
    public Map<String, Object> siteInfo(String lang)
    {
        return siteInfo(lang, siteInfoService.requirePublic(), null);
    }

    /**
     * 装修预览：拿一份**还没保存**的装修 JSON，算出前台会看到什么。
     *
     * <p>返回的形状和 {@link #siteInfo(String)} 一模一样，前台不用认识第二种结构——
     * 预览页拿到它直接顶掉自己那份就行。</p>
     *
     * <h3>两处和公开接口不同，都是有意的</h3>
     *
     * <ul>
     *   <li><b>用 require() 不是 requirePublic()</b>：站点「建设中」时也要能预览。
     *       装修本来就是**上线之前**做的事，这时候公开接口正回着「站点不可用」。</li>
     *   <li><b>调用方是管理台</b>（带会话、要 site:station:edit），不是匿名前台。
     *       这个方法不写库，只是算一遍。</li>
     * </ul>
     *
     * @param draftContentJson 草稿。传 null 或空串表示「按清空之后的样子看」，
     *                         也就是整份回退到内置默认——和保存空串的效果一致
     */
    public Map<String, Object> siteInfoPreview(String lang, String draftContentJson)
    {
        SiteInfo site = siteInfoService.require();
        return siteInfo(lang, site, draftContentJson == null ? "" : draftContentJson);
    }

    /**
     * @param draft 传 null 表示用库里那份（正常取数）；传字符串表示用这份草稿算
     */
    private Map<String, Object> siteInfo(String lang, SiteInfo site, String draft)
    {
        String language = site.resolveLang(lang);

        // 先铺档案里的基本信息，再让装修 JSON 覆盖：公司对外用的英文名往往和档案里的
        // 站点名称不是一回事，让装修内容说了算。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyName", site.getSiteName());
        result.put("slogan", "");
        result.put("logo", "");
        result.put("seoTitle", site.getSiteName());
        result.put("seoDescription", "");
        result.put("currency", site.getCurrency());
        result.put("lang", language);
        result.put("defaultLang", site.defaultLang());
        // 站点开了哪些语言。前台要渲染语言切换器就得知道这个，否则只能把语言列表写死在页面里
        result.put("langs", site.langList());
        // 前台视觉主题，前台挂到 <html data-theme> 上。空 = esva（site.css 本身那套）
        result.put("theme", site.getTheme());
        // 引导式下单开没开、挂了哪些商品。前台据此在顶栏和商品详情放入口，整份向导另走 /wizard
        result.put("wizard", orderWizardService.summary(language));

        /*
         * 币种切换。三个字段一起下发，前台不需要额外发请求——这个接口本来就是首屏必拉的。
         *
         * ⚠ rates 已经**以站点本币为基准**（见 RateService.ratesFor），前台直接乘即可。
         *   换算放在后端而不是前台：算反了的结果仍然是一个「看起来像价格」的数字，
         *   页面上不报错，只会贵 40 倍或便宜 40 倍，在前端极难被发现。
         *
         * currencies 用 rates 的键而不是 site.currencyList()：没有汇率的币种已经被
         * ratesFor 剔掉了，两者必须一致。给了选项却换算不出来，买家切过去会看到
         * 一个没换算的原始数字配上另一个币种的符号——比不给这个选项糟得多。
         *
         * 汇率一次都没拉到时，rates 里只有本币一个，前台据此不渲染切换器，
         * 降级成没有这个功能时的样子。
         */
        Map<String, BigDecimal> rates =
                rateService.ratesFor(site.baseCurrency(), site.currencyList());
        result.put("currencies", new ArrayList<>(rates.keySet()));
        result.put("rates", rates);
        SiteRate rateRow = rateService.require();
        result.put("rateTime", rateRow == null ? null : rateRow.getFetchTime());
        result.put("nav", defaultNav());
        result.put("hero", defaultHero(site));
        result.put("highlights", new ArrayList<>());
        result.put("capabilities", new ArrayList<>());
        result.put("about", new LinkedHashMap<String, Object>());
        result.put("contact", new LinkedHashMap<String, Object>());
        result.putAll(siteContent(site, language, draft));

        /*
         * 站长工具配置（u_s_seo）。
         *
         * ⚠ 必须放在 putAll(siteContent(...)) **之后**：装修 JSON 是用户可写的内容，
         *   万一里面有一个叫 seo 的顶层键，放在前面就会被它整个盖掉——
         *   而症状是「统计代码明明配了却不生效」，查起来会先怀疑注入那一层。
         *
         * ⚠ 走 publicSeo() 逐列白名单，**不把 SiteSeo 实体直接扔出去**——那是本类
         *   类注释第 2 条。第一版就是直接扔的，于是 updateUser（管理台操作人的登录名）
         *   被发到了这个匿名接口上。
         *
         * ⚠ 那 13 列里包括 sitemapMax 这种看着像后端参数的东西，因为 sitemap.xml 和
         *   robots.txt 是 station-site（Nitro）生成的，不是后端生成的——后端只是
         *   这份配置的存放处。
         *
         * ⚠⚠ 所以 u_s_seo 的业务列基本都是公开的。这是那张表**不许存密钥**的原因，
         *   见它的表注释。将来要存 GSC 的 API key 之类的东西，必须另建一张表：
         *   加在 u_s_seo 上不会报错（publicSeo 不列它，也不会外发），
         *   但那张表的定位从此就含糊了，而下一个人只会照着上面几列的样子把它加进白名单。
         *
         * 取不到配置时 current() 回的是一份默认值（不会抛异常）——统计不注入、
         * robots 按代码里那批基线走。前台**绝不能**因为这一项取不到而 503：
         * 少一个可选功能不该让整站白屏。
         */
        result.put("seo", publicSeo(siteSeoService.current()));

        // 分类是现算的，不放进装修 JSON：商品上下架时它就变了，让人去手工维护一定会不同步
        result.put("categories", goodsMapper.selectPublicCategories());

        /*
         * 分类的各语言显示名。
         *
         * ⚠ 刻意是「另加一个字典」而不是把 categories 改成对象数组：那一改就是破坏性变更，
         * 发版窗口期里老前台配新后端会崩。现在老前台读不到 categoryNames，照旧显示原串。
         *
         * 键是 catkey（分类路径按分隔符逐级拼出来的前缀，见 SiteCategoryService），
         * 值是当前语言的译名。没译名的键不出现——前台据此回退显示原文，不会出空标签。
         * 当前语言就是站点默认语言时返回空字典：默认语言本来就用原文。
         */
        result.put("categoryNames", siteCategoryService.namesFor(language));
        return result;
    }

    /**
     * 商品列表。
     *
     * @param keyword  关键字，匹配标题、卖点、商品编码
     * @param category 站点分类
     * @param page     页码，从 1 开始
     * @param size     每页条数，服务端会夹到 1~60，防止有人用 size=999999 把库拖垮
     */
    public Map<String, Object> products(String lang, String keyword, String category, int page, int size)
    {
        SiteInfo site = siteInfoService.requirePublic();
        String language = site.resolveLang(lang);
        int limit = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int current = Math.max(page, 1);
        String kw = trimToNull(keyword);
        String cat = trimToNull(category);

        int total = goodsMapper.countPublicList(language, site.defaultLang(), kw, cat);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (total > 0)
        {
            List<SiteGoods> list = goodsMapper.selectPublicList(language, site.defaultLang(),
                    kw, cat, (current - 1) * limit, limit);
            for (SiteGoods item : list)
            {
                rows.add(toCard(item, site));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", current);
        result.put("size", limit);
        result.put("currency", site.getCurrency());
        result.put("rows", rows);
        return result;
    }

    /** 商品详情。查不到（不存在或没上架）返回 null，由 Controller 转成 404 文案 */
    public Map<String, Object> productDetail(String lang, String goodsCode)
    {
        SiteInfo site = siteInfoService.requirePublic();
        String code = trimToNull(goodsCode);
        if (code == null)
        {
            return null;
        }
        SiteGoods item = goodsMapper.selectPublicOne(code, site.resolveLang(lang), site.defaultLang());
        if (item == null)
        {
            return null;
        }

        Map<String, Object> result = toCard(item, site);
        result.put("spec", item.getSpec());
        result.put("keywords", item.getKeywords());
        // 详情富文本会被页面当 HTML 渲染。过滤在写入侧做
        // （SiteGoodsService.saveGoods → RichTextCleaner），这里直接吐库里的值。
        //
        // 库里可能还有过滤上线之前存进来的老数据，用
        // sql/migration/20260831_station_goods_description_scan.sql 扫一遍，
        // 命中的重新保存一次即可。
        result.put("description", item.getDescription());
        // 规格按语言下发：以默认语言那份（主表 specsjson）为行清单和顺序，
        // 逐行取请求语言的译键/译值（u_s_sitegoods_lang.specsjson，按默认键当身份），
        // 缺译回退默认。返回普通 {键:值}，前台不用改。
        result.put("specs", buildSpecs(item.getSpecsJson(), item.getLangSpecsJson(), code));
        // 3D 模型地址。只在详情下发，列表卡片（toCard）不带——卡片用不到，别让每张多一个字段。
        // 没配就是 null，前台据此决定渲不渲染展示台（老商品在规格里填的 model3d 键前台仍认）
        result.put("model3d", item.getModel3d());
        // 3D 标注（logo 贴花 / 屏幕 / 开机键），下发对象不是字符串；没有或坏了就是 null
        result.put("model3dMarks", Model3dMarks.toObject(item.getModel3dMarks()));
        // 3D 模型状态（多个 glb 切换）：只给主模型 + 有模型的状态、名字已按语言换好；不足 2 个就是 null
        result.put("model3dStates", Model3dStates.toPublic(item.getModel3dStates(),
                site.resolveLang(lang), site.defaultLang()));
        // 主图视频：图库轮播的第一格。和 videos（详情底部那一排）是两块不同的位置，
        // 一个商品可以两边都配。没配就是 null，前台图库第一格仍是图。
        // 封面不下发——前台直接拿 images[0] 当 poster。
        result.put("mainVideo", item.getMainVideo());
        // 组合（详情页那排带缩略图的选项卡）。没配过就是空数组，前台据此决定渲不渲染这一块。
        // 配了规格维度（二维：尺码 × 款式）时多给一份 options，每张组合卡带上 opt1/opt2，
        // 前台据此画成两排选择器；没配就不给 options，前台照旧一排
        String language = site.resolveLang(lang);
        GoodsOptions options = readOptions(item.getOptionsJson(), code);
        List<Map<String, Object>> combos = comboCards(code, language, site.defaultLang(), site, options);
        result.put("combos", combos);
        if (options != null && !combos.isEmpty())
        {
            result.put("options", optionCards(options, combos, language, site.defaultLang()));
        }
        return result;
    }

    /**
     * 库里的规格维度。读不出来按平铺处理并记一笔，<b>不让详情页 500</b>：
     * 维度坏了，组合还在、还能按名字一排显示，客户照样能选能询价。
     */
    /** 定制页用：和详情页同一套组合卡片（名称按语言、二维模式现拼） */
    List<Map<String, Object>> publicComboCards(SiteGoods item, String lang, SiteInfo site)
    {
        String language = site.resolveLang(lang);
        GoodsOptions options = readOptions(item.getOptionsJson(), item.getGoodsCode());
        return comboCards(item.getGoodsCode(), language, site.defaultLang(), site, options);
    }

    private GoodsOptions readOptions(String json, String goodsCode)
    {
        try
        {
            return GoodsOptions.parse(json, JSON);
        }
        catch (IllegalArgumentException e)
        {
            log.warn("商品 {} 的规格维度解析失败，按平铺显示：{}", goodsCode, e.getMessage());
            return null;
        }
    }

    /**
     * 详情页那两排选择器：[{code, name, values:[{code, name, image}]}]。
     *
     * <p><b>一个可选组合都没用到的取值不下发。</b>比如「灰色」三个尺码都停用了，
     * 那一格点下去什么都选不中——与其画一个永远灰着的按钮，不如不画。
     * 「某个尺码下没有灰色」这种局部缺货不在这里处理，那是前台按当前选中项置灰的事。</p>
     */
    private List<Map<String, Object>> optionCards(GoodsOptions options, List<Map<String, Object>> combos,
            String lang, String defaultLang)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < options.size(); i++)
        {
            String key = i == 0 ? "opt1" : "opt2";
            java.util.Set<Object> used = new java.util.HashSet<>();
            for (Map<String, Object> combo : combos)
            {
                used.add(combo.get(key));
            }
            GoodsOptions.Dim dim = options.dims().get(i);
            List<Map<String, Object>> values = new ArrayList<>();
            for (GoodsOptions.Value value : dim.values)
            {
                if (!used.contains(value.code))
                {
                    continue;
                }
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("code", value.code);
                v.put("name", options.valueName(i, value.code, lang, defaultLang));
                v.put("image", value.image);
                values.add(v);
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("code", dim.code);
            d.put("name", options.dimName(i, lang, defaultLang));
            d.put("values", values);
            out.add(d);
        }
        return out;
    }

    /**
     * 提交询盘。
     *
     * <p>蜜罐字段有值时<b>静默丢弃并返回成功</b>：让机器人以为提交成功了，
     * 它就不会换个花样再来。返回 false 表示这是一条被丢掉的垃圾询盘。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean submitInquiry(SiteInquiry inquiry, String clientIp, String userAgent)
    {
        SiteInfo site = siteInfoService.requirePublic();
        if (inquiry == null)
        {
            throw new ServiceException("询盘内容不能为空");
        }

        // 蜜罐中招：装作成功，不给机器人任何反馈，它就不会换个花样再来
        if (trimToNull(inquiry.getWebsite()) != null)
        {
            log.info("拦下一条机器人询盘，IP {}", clientIp);
            return false;
        }

        inquiry.setName(require(inquiry.getName(), "姓名", 100));
        inquiry.setEmail(require(inquiry.getEmail(), "邮箱", 150));
        inquiry.setMessage(require(inquiry.getMessage(), "留言", 2000));
        if (!inquiry.getEmail().matches("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$"))
        {
            throw new ServiceException("邮箱格式不正确");
        }
        inquiry.setCompany(cut(inquiry.getCompany(), 200));
        inquiry.setPhone(cut(inquiry.getPhone(), 50));
        inquiry.setGoodsCode(cut(inquiry.getGoodsCode(), 50));
        inquiry.setProductTitle(cut(inquiry.getProductTitle(), 300));
        inquiry.setPageUrl(cut(inquiry.getPageUrl(), 500));
        // 语言先归一：下面查型号名要用它，而客户端传来的可能是站点没开的语言
        inquiry.setLang(site.resolveLang(inquiry.getLang()));
        // 型号快照。必须在语言归一之后
        resolveInquiryCombo(inquiry, site);

        inquiry.setShopCode(site.getShopCode());
        inquiry.setSiteCode(site.getSiteCode());
        inquiry.setInquiryCode(codeGenerator.nextInquiryCode());
        inquiry.setClientIp(cut(clientIp, 64));
        inquiry.setUserAgent(cut(userAgent, 300));
        inquiry.setStatus(SiteInquiry.STATUS_NEW);
        inquiryMapper.insert(inquiry);

        // 只是登记，真正发信在事务提交之后、另一个线程上。这一行不会抛异常，
        // 也不会拖慢访客那个请求——理由见 InquiryNotifier 的类注释
        inquiryNotifier.notifyAfterCommit(site, inquiry);
        return true;
    }

    // ==================== 内部 ====================

    /**
     * 详情页的组合选项卡。
     *
     * <p><b>名称为空的型号直接丢掉</b>，不拿 comboCode 兜底：那是给机器看的码（CB01），
     * 显示在选项卡上等于让客户去猜。宁可少一个选项，也不要一个看不懂的选项。
     * 商家在管理台会看到那一格是空的——那正是要让他看见的信息。</p>
     *
     * <p>价格照 {@code toCard} 的约定：0 表示面议，前台显示 Inquiry 而不是「0 元」。
     * 币种给站点本币，换算由前台的 useDisplayCurrency 统一做——和商品价走同一条路，
     * 不能只换算商品价、不换算型号价。</p>
     */
    private List<Map<String, Object>> comboCards(String goodsCode, String lang,
            String defaultLang, SiteInfo site, GoodsOptions options)
    {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (SiteGoodsCombo combo : comboMapper.selectPublicByGoods(goodsCode, lang, defaultLang))
        {
            String title = trimToNull(combo.getTitle());
            if (options != null)
            {
                // 二维模式：名称按当前维度现拼，不读库里那份。
                // 库里那份是保存时拼好给单据快照用的；站点后来新开了一种语言、还没重新保存过商品时，
                // 那种语言的库里没有名称，现拼能直接用上取值的译名。
                // 对不上任何取值的组合（取值被删了、数据是切模式前留下的）不显示
                title = options.comboTitle(combo.getOpt1(), combo.getOpt2(), lang, defaultLang);
            }
            if (title == null)
            {
                continue;
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("code", combo.getComboCode());
            card.put("title", title);
            if (options != null)
            {
                card.put("opt1", combo.getOpt1());
                card.put("opt2", combo.getOpt2());
            }
            card.put("image", combo.getImage());
            // 规格自己的 3D 模型（2026-10-09）：只有配了模型的才带；标注 null = logo 跟随主模型
            if (combo.getModel3d() != null)
            {
                card.put("model3d", combo.getModel3d());
                card.put("model3dMarks", Model3dMarks.toObject(combo.getModel3dMarks()));
                card.put("model3dStates", Model3dStates.toPublic(combo.getModel3dStates(), lang, defaultLang));
            }
            card.put("price", combo.getPrice() == null ? BigDecimal.ZERO : combo.getPrice());
            card.put("currency", site.getCurrency());
            cards.add(card);
        }
        return cards;
    }

    /**
     * 把询盘上的型号快照补齐，顺便验一遍。
     *
     * <p><b>只信 comboCode，名称一律以库里的为准。</b>客户端传来的 comboName 直接丢掉：
     * 那个名字是要发给销售、要拿去开报价单的，让浏览器随便填等于让人往询盘台账里写字。</p>
     *
     * <p>编码对不上（写错了、那条型号被停用了、或者根本不属于这个商品）时
     * <b>把编码也清掉</b>，而不是抛异常。理由：客户已经把表单填完了，为了一个他看不懂的
     * 字段把提交打回去，丢的是一条真询盘；而留一个指向不存在型号的编码，
     * 会让销售以为客户问的是某个具体型号。两害相权，留下询盘、丢掉这一格。</p>
     */
    private void resolveInquiryCombo(SiteInquiry inquiry, SiteInfo site)
    {
        inquiry.setComboName(null);
        String comboCode = trimToNull(inquiry.getComboCode());
        String goodsCode = trimToNull(inquiry.getGoodsCode());
        if (comboCode == null || goodsCode == null)
        {
            inquiry.setComboCode(null);
            return;
        }
        String lang = site.resolveLang(inquiry.getLang());
        SiteGoodsCombo combo = comboMapper.selectPublicOne(goodsCode, comboCode, lang, site.defaultLang());
        if (combo == null || trimToNull(combo.getTitle()) == null)
        {
            log.info("询盘里的型号编码对不上，已忽略：商品 {}，型号 {}", goodsCode, comboCode);
            inquiry.setComboCode(null);
            return;
        }
        inquiry.setComboCode(cut(combo.getComboCode(), 50));
        inquiry.setComboName(cut(combo.getTitle(), 200));
    }

    /** 列表和详情共用的商品字段。白名单在这里，别的地方一律不许直接吐实体 */
    private Map<String, Object> toCard(SiteGoods item, SiteInfo site)
    {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("code", item.getGoodsCode());
        card.put("title", item.getTitle());
        card.put("summary", item.getSubtitle());
        card.put("category", item.getSiteCategory());
        card.put("price", item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());
        card.put("currency", site.getCurrency());
        card.put("moq", item.getMoq());
        card.put("unit", item.getUnit());
        card.put("featured", Integer.valueOf(1).equals(item.getFeatured()));
        card.put("images", images(item));
        // 视频没有回退来源，没挑就是空数组，前台据此决定渲不渲染视频区块
        card.put("videos", splitUrls(item.getSiteVideos()));
        return card;
    }

    /**
     * 商品图。站点专用图优先，整组覆盖；没挑过才回退档案图。
     *
     * <p>覆盖而不是追加：两组混在一起的话，运营在管理台挑完图，站上还是会显示
     * 那张内部用的图，等于没挑。</p>
     *
     * <p>ERP 那版这两组图存在不同的桶里（站点图在商家的对象存储、档案图在平台的阿里云）。
     * 现在都在这台机器的磁盘上，区别只剩「是不是特意为站点挑的」。</p>
     */
    private List<String> images(SiteGoods item)
    {
        List<String> picked = splitUrls(item.getSiteImgs());
        if (!picked.isEmpty())
        {
            return picked;
        }
        List<String> result = new ArrayList<>();
        String main = trimToNull(item.getImgUrl());
        if (main != null)
        {
            result.add(main);
        }
        result.addAll(splitUrls(item.getMainImgs()));
        return result.stream().distinct().collect(Collectors.toList());
    }

    /** 逗号分隔的 URL 串拆成列表，去空去重，顺序保持不变（第一张是主图） */
    private List<String> splitUrls(String text)
    {
        List<String> result = new ArrayList<>();
        String value = trimToNull(text);
        if (value == null)
        {
            return result;
        }
        for (String url : value.split(","))
        {
            String item = trimToNull(url);
            if (item != null && !result.contains(item))
            {
                result.add(item);
            }
        }
        return result;
    }

    // ==================== 博客（对外） ====================
    //
    // ⚠⚠ 这一组和商品那一组在「缺译文」上**行为相反**，别照着商品那边改：
    //
    //   商品：left join + 「当前语言 → 默认语言」回退。商品必须能被买到，
    //         宁可标题是英文，也不能让日语站少一个可下单的商品。
    //
    //   文章：inner join + 要求标题非空。**某语言没译文就等于那个语言版本不存在**
    //         （列表里没有、详情 404、sitemap 不列、hreflang 不列）。
    //         一篇英文长文摆在日语站上对读者没价值，而且会被 Google 判成
    //         「hreflang 说是日语、内容是英文」——比少一篇文章更伤。
    //
    // 这条规则在四处一起生效，缺一处就漏。前三处在 SitePostMapper.xml 的
    // selectPublic* / selectSitemapRows 里，第四处是下面 postCard 里那个 langs。

    /**
     * 博客文章列表。
     *
     * @param lang     当前语言。⚠ 没有默认语言回退，见上面那段
     * @param keyword  关键字，匹配标题和摘要
     * @param category 博客分类
     * @param page     页码，从 1 开始
     * @param size     每页条数，夹到 1~60（同商品，不夹一下一个 size=999999 就能把库拉走）
     */
    public Map<String, Object> posts(String lang, String keyword, String category, int page, int size)
    {
        SiteInfo site = siteInfoService.requirePublic();
        String language = site.resolveLang(lang);
        int limit = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int current = Math.max(page, 1);
        String kw = trimToNull(keyword);
        String cat = trimToNull(category);

        int total = postMapper.countPublicList(language, kw, cat);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (total > 0)
        {
            for (SitePost post : postMapper.selectPublicList(language, kw, cat,
                    (current - 1) * limit, limit))
            {
                rows.add(postCard(post, site, false));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", current);
        result.put("size", limit);
        result.put("rows", rows);
        // 分类现算，不落库：文章上下线时它就变了，让人手工维护一定会不同步（同商品分类）
        result.put("categories", postMapper.selectPublicCategories());
        return result;
    }

    /**
     * 博客文章详情，按 <b>slug</b> 查。查不到返回 null，由 Controller 转成 404。
     *
     * <p>⚠ 「文章不存在」和「这个语言没有译文」对外<b>都是 404</b>，不区分。
     * 区分了等于告诉外面「这篇文章存在但你看不到」，而那个信息对访客没用。</p>
     */
    public Map<String, Object> postDetail(String lang, String slug)
    {
        SiteInfo site = siteInfoService.requirePublic();
        String key = trimToNull(slug);
        if (key == null) return null;

        SitePost post = postMapper.selectPublicOne(key, site.resolveLang(lang));
        if (post == null) return null;

        return postCard(post, site, true);
    }

    /**
     * 给 sitemap 用：每一篇已发布文章 + 它有译文的语言。
     *
     * <p>⚠ <b>不按当前语言过滤</b>——这是它和 {@link #posts} 的全部区别，
     * 也是它必须单独存在的理由。用列表那一条生成 sitemap 会漏掉
     * 「只翻了日语、没有默认语言版本」的文章，而那种漏的表现是
     * 「文章在站上打得开、Google 却一直发现不了」，最难注意到的一类问题。</p>
     *
     * @param limit 条数上限，前台从 {@code u_s_seo.sitemapmax} 传进来
     */
    public Map<String, Object> postSitemap(int limit)
    {
        SiteInfo site = siteInfoService.requirePublic();
        int max = limit <= 0 ? 5000 : Math.min(limit, 6000);
        Set<String> openLangs = new LinkedHashSet<>(site.langList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SitePost post : postMapper.selectSitemapRows(max))
        {
            // group_concat 出来的逗号串拆进 langs，见 SitePost#splitLangCodes
            post.splitLangCodes();
            List<String> langs = post.getLangs().stream()
                    .filter(openLangs::contains)
                    .collect(Collectors.toList());
            // 译文只剩站点已经关掉的语言时，这篇文章对外不存在，不该进 sitemap
            if (langs.isEmpty()) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slug", post.getSlug());
            row.put("langs", langs);
            row.put("publishTime", post.getPublishTime());
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("rows", rows);
        return result;
    }

    /**
     * 一篇文章对外的字段白名单。
     *
     * <p>⚠ 手工装 Map，不把实体直接序列化——本类类注释第 2 条。实体上多一个字段
     * （{@code remark} 是只给管理台看的备注、{@code createUser} 是操作人登录名），
     * 接口就多漏一份东西。</p>
     *
     * @param withContent 详情才带正文。列表不带——它可以很大，而列表页用不上
     */
    private Map<String, Object> postCard(SitePost post, SiteInfo site, boolean withContent)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("slug", post.getSlug());
        row.put("title", post.getTitle());
        row.put("summary", post.getSummary());
        row.put("category", post.getCategory());
        row.put("coverImg", post.getCoverImg());
        row.put("author", post.getAuthor());
        row.put("publishTime", post.getPublishTime());

        /*
         * 这篇文章有译文的语言，给页面拼 hreflang 用。
         *
         * ⚠ 必须再按「站点当前开的语言」交一次集：库里可能留着站点已经关掉的语言的
         *   旧译文，把它们放进 hreflang 等于向 Google 承诺一批打不开的地址
         *   （那些语言前缀在前台会 404，见 useSite.ts 里那段语言校验）。
         *
         * ⚠ 而 hreflang 里有一个坏链接，Google 会忽略**整组**声明——
         *   也就是这个站多语言 SEO 的全部价值。所以这个交集不是保守，是必须。
         */
        Set<String> openLangs = new LinkedHashSet<>(site.langList());
        row.put("langs", postMapper.selectPublicLangs(post.getPostCode()).stream()
                .filter(openLangs::contains)
                .collect(Collectors.toList()));

        if (withContent)
        {
            // 正文在写入时已经过了 RichTextCleaner 白名单（SitePostService.savePost），
            // 所以前台可以 v-html 渲染——**前提是那一层过滤还在**。
            row.put("content", post.getContent());
        }
        return row;
    }

    /**
     * 站长工具配置里<b>能对外发的那一部分</b>。
     *
     * <h3>为什么不直接把 {@link SiteSeo} 扔出去</h3>
     * <p>一开始就是那么写的，然后发现两个问题：</p>
     *
     * <ul>
     *   <li><b>{@code updateUser} 是管理台操作人的登录名。</b>这个接口是<b>匿名</b>的，
     *       等于把「谁在管这个站」发给任何人。不是大事，但没有任何理由发；</li>
     *   <li>{@code id} / {@code remark} / {@code createTime} / {@code updateTime} 是
     *       内部列，前台一个都不读，发出去只是让这个接口的返回体更难看懂。</li>
     * </ul>
     *
     * <p>⚠ 所以这里<b>逐个列</b>，不用反射也不用 {@code @JsonView}：这张表整体是公开的，
     * 而「哪些列真的公开」这件事应该是<b>一段能被人读一遍的代码</b>。将来往
     * {@code u_s_seo} 加列时，不加到这个方法里就不会外发——这是安全的那一边。</p>
     *
     * <p>下面这 13 项是前台真的要用的：{@code noindexAll} 给页面上的 noindex
     * （{@code useSeo.ts}），其余都给服务端的 robots / sitemap / head 注入
     * （{@code server/utils/seoConfig.ts}）。</p>
     */
    private Map<String, Object> publicSeo(SiteSeo seo)
    {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("noindexAll", seo.getNoindexAll());
        out.put("disallowPaths", seo.getDisallowPaths());
        out.put("robotsExtra", seo.getRobotsExtra());
        out.put("sitemapOn", seo.getSitemapOn());
        out.put("sitemapMax", seo.getSitemapMax());
        out.put("homeFreq", seo.getHomeFreq());
        out.put("goodsFreq", seo.getGoodsFreq());
        out.put("googleVerify", seo.getGoogleVerify());
        out.put("bingVerify", seo.getBingVerify());
        out.put("baiduVerify", seo.getBaiduVerify());
        out.put("ga4Id", seo.getGa4Id());
        out.put("gtmId", seo.getGtmId());
        out.put("baiduHmId", seo.getBaiduHmId());
        return out;
    }

    /**
     * 解析一段 JSON 文本成 Map。
     *
     * <p>解析不了就当空的，绝不往上抛：装修内容或规格 JSON 被人改坏，
     * 顶多是首页少一块、详情少张规格表，不该让整个站打不开。</p>
     */
    /**
     * 按语言合并规格。默认 {@code specsjson}(主表)定行清单和顺序,逐键用请求语言的译键/译值覆盖,
     * 缺译回退默认。返回普通 {@code {键:值}}(键也本地化,因为键值都翻),前台按 entries 直接渲染。
     *
     * @param defaultJson 默认语言规格 {@code {"Material":"..."}}
     * @param langJson    请求语言译文 {@code {"Material":{"k":"译键","v":"译值"}}},按默认键当身份
     */
    private Map<String, Object> buildSpecs(String defaultJson, String langJson, String owner)
    {
        Map<String, Object> base = parseObject(defaultJson, owner);
        String lt = trimToNull(langJson);
        if (lt == null || base.isEmpty())
        {
            return base;
        }
        JsonNode overrides;
        try
        {
            overrides = JSON.readTree(lt);
        }
        catch (Exception e)
        {
            log.warn("{} 的规格译文解析失败,按无译处理:{}", owner, e.getMessage());
            return base;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : base.entrySet())
        {
            String defaultKey = entry.getKey();
            String key = defaultKey;
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            JsonNode override = overrides.get(defaultKey);
            if (override != null)
            {
                String tk = trimToNull(override.path("k").asString(null));
                String tv = trimToNull(override.path("v").asString(null));
                if (tk != null)
                {
                    key = tk;
                }
                if (tv != null)
                {
                    value = tv;
                }
            }
            out.put(key, value);
        }
        return out;
    }

    private Map<String, Object> parseObject(String json, String owner)
    {
        String text = trimToNull(json);
        if (text == null)
        {
            return new LinkedHashMap<>();
        }
        try
        {
            Map<String, Object> map = JSON.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {});
            return map == null ? new LinkedHashMap<>() : map;
        }
        catch (Exception e)
        {
            log.warn("{} 的 JSON 内容解析失败，按空处理：{}", owner, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 取这个语言下的装修内容。
     *
     * <p>装修 JSON 的分工只有一句话：<b>页面长什么样只存一份，页面上写着什么按语言各存一份。</b>
     * 区块的结构、顺序、版式、图片、电话号码、地图点位在<b>共享层</b>（{@code i18n} 之外的顶层键），
     * 所有语言共用；文案和 SEO 标题描述在<b>语言层</b>（{@code i18n.<语言>}），每种语言各一份。</p>
     *
     * <p>商家因此是「搭一次页面，之后只翻译文字」：换语言不会改变版式，
     * 而每种语言又都有自己的 H1 和 meta——hreflang 才成立，Google 也不会判成重复内容。</p>
     *
     * <pre>
     * {
     *   "v": 4,
     *   "logo": "https://…",                          ← 共享层：图片只存一份
     *   "hero":  { "slides": [ {"id":"s1", "image":"…"} ] },
     *   "nav":   [ {"id":"n1", "url":"/products"} ],   ← 有几条、指向哪、什么顺序：共享
     *   "highlights": [ {"id":"h1", "icon":"★"} ],
     *   "order": [...], "off": [...], "blocks": {...},
     *   "i18n": {
     *     "en": { "seoTitle":"…",
     *             "navText":       { "n1": {"title":"Products"} },   ← 语言层：只有文字
     *             "highlightText": { "h1": {"title":"…","desc":"…"} },
     *             "slideText":     { "s1": {"title":"…"} },
     *             "hero": { "title":"…" } },
     *     "ja": { … 同样那几个 id，另一套文字 … }
     *   }
     * }
     * </pre>
     *
     * <h3>三层，从下往上铺</h3>
     *
     * <ol>
     *   <li><b>共享层</b>——{@code i18n} 之外的顶层键，各语言通用；</li>
     *   <li><b>默认语言层</b>——站点默认语言那一份，当译文的兜底；</li>
     *   <li><b>当前语言层</b>——访客要的那个语言，盖在最上面。</li>
     * </ol>
     *
     * <p>所以某个语言只译了一半时，<b>缺的那几项按项退回默认语言</b>，不是整块空掉。
     * 这和商品多语言的行为一致（缺某语言文案回退默认语言），
     * 2026-09-10 定的，见 {@code docs/设计稿/2026-09-10-首页装修重做记录.md} §4.C。</p>
     *
     * <p>⚠ 改之前是<b>整份</b>论的：当前语言这一份存在就整份用它，只有整份都没有才退回默认语言。
     * 于是「日语配了 Banner、没配关于我们」的站，日语页上关于我们那一块是空的——
     * 而管理台上一直写着「没填的项会退回默认语言」。那句话当时是错的，现在才成立。</p>
     *
     * <h3>同名键怎么合</h3>
     *
     * <p>两边都是对象时<b>合一层</b>（里面的小项各归各位），其余情况整个覆盖。见
     * {@link #overlay}。这是「共享粒度到字段」的实现：共享层放
     * {@code contact:{phone,email,whatsapp}}、语言层放 {@code contact:{address}}，
     * 合出来四项都在。</p>
     *
     * <p>⚠ 只合一层，所以<b>共享层和语言层在同一个父键底下必须各用各的键名</b>：
     * 共享层写 {@code bento.statValue}、语言层写 {@code bento.statLabel} 是对的；
     * 两边都写 {@code bento.stat.xxx} 的话，语言层那个 {@code stat} 会把共享层那个整个换掉，
     * 数字就没了。列表也是这个道理，所以文案袋子叫 {@code navText} 而不是 {@code nav}。</p>
     *
     * <p>⚠ 另一个代价：语言层<b>没法删掉</b>共享层的某个子键了，只能覆盖成别的值。
     * 目前没有这个需求（要「这个语言不显示某块」得另想表达方式，不能靠留空）。</p>
     *
     * <h3>合完还有一步：{@link #compose}</h3>
     *
     * <p>共享的条目（{@code nav}、{@code highlights}…）和语言层的文案袋子
     * （{@code navText}、{@code highlightText}…）靠 id 对应，合并之后还是两半，
     * 而前台认的是拼好的那一种（{@code nav:[{title,url}]}）。拼接放在后端，
     * 前台一个字都不用改，管理台的实时预览也自动是对的——它推的就是这里算出来的数据。</p>
     *
     * <h3>老数据兼容：v1 / v2 / v3 全都照常显示</h3>
     *
     * <ul>
     *   <li><b>v1</b>——没有 {@code i18n} 键，整份就是一种语言的内容，原样返回。
     *       迁移脚本 {@code sql/migration/20260831_station_content_i18n.sql} 会把它们包进
     *       {@code i18n.<站点默认语言>}，跑过之后这条路只是安全网；</li>
     *   <li><b>v2</b>——除 logo 外什么都在语言层。逐键合并让它照常显示；</li>
     *   <li><b>v3</b>——图片、电话、地图点位已经在共享层，但 {@code nav} / {@code highlights}
     *       这些整条都还在语言层（条目里自带 {@code title}，没有 id）。
     *       {@link #compose} 对这种条目<b>原样放行</b>：没有 id 就没有文案袋子要拼。</li>
     * </ul>
     *
     * <p>所以 v3 → v4 <b>不需要迁移脚本</b>：商家在管理台点一次保存就自动归位，
     * 在那之前页面显示一模一样。</p>
     */
    private Map<String, Object> siteContent(SiteInfo site, String language, String draft)
    {
        // draft 为 null = 用库里那份；非 null = 预览一份还没保存的（见 siteInfoPreview）
        String text = draft == null ? site.getContentJson() : draft;
        Map<String, Object> root = parseObject(text, site.getSiteCode());

        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet())
        {
            // v 和 i18n 是结构字段，不该出现在接口返回里
            if (!"i18n".equals(entry.getKey()) && !"v".equals(entry.getKey()))
            {
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        Object i18n = root.get("i18n");
        if (i18n instanceof Map)
        {
            Map<?, ?> byLang = (Map<?, ?>) i18n;

            /*
             * 先铺默认语言、再盖当前语言。
             *
             * 两者相同时只铺一次——不是为了省这点时间，是为了别让 overlay 把同一个对象
             * 自己合一遍（结果一样，但读代码的人会停下来想为什么要合两次）。
             */
            String fallback = site.defaultLang();
            if (fallback != null && !fallback.equalsIgnoreCase(language))
            {
                overlay(merged, byLang.get(fallback));
            }
            overlay(merged, byLang.get(language));
        }

        /*
         * ⚠ v1（没有 i18n 键）也要走一遍 compose：那份数据整份就是一种语言的内容，
         *   拼不出什么来，但走同一条路才不会有第二种「前台拿到的形状」。
         */
        compose(merged);
        return merged;
    }

    // ======== 以下到「拼接结束」为止，与 scripts/verify/MergeCheck.java 里那份逐字一致 ========

    /**
     * 把「共享的条目」和「语言层的文案」拼回前台认识的那一种。
     *
     * <p>装修里每一种可重复的东西（导航、特色、能力、幻灯片、数字亮点）都是<b>两半</b>存的：</p>
     *
     * <pre>
     * 共享层   "nav": [ {"id": "n1", "url": "/products"} ]      ← 有几条、指向哪、什么顺序
     * 语言层   "navText": { "n1": {"title": "Products"} }       ← 只有那几个字
     * </pre>
     *
     * <p>这么存是为了让商家<b>搭一次页面、之后只翻译文字</b>：加一条导航是所有语言一起加的，
     * 换一张图是所有语言一起换的，而每种语言的字各填各的。整条都存在语言层的老做法下，
     * 七种语言能排出七种不同的导航，同一个网址商家得填七遍。</p>
     *
     * <p>拼接放在后端，所以前台拿到的仍然是 {@code nav:[{title,url}]} 这种老形状，
     * 一个字都不用改；管理台的实时预览推的也是这里算出来的数据，不会和线上不一致。</p>
     *
     * <h3>三条规矩</h3>
     *
     * <ol>
     *   <li><b>靠 id 对应，不靠下标</b>。往中间插一条，下标就全错位了，
     *       而错位的表现是「日语页面上每条文案都串了一格」；</li>
     *   <li><b>文案袋子一律放在语言层的顶层</b>，哪怕它配的那个列表在某个键底下
     *       （{@code hero.slides} 的袋子叫 {@code slideText}，不叫 {@code hero.slideText}）。
     *       因为 {@link #overlay} <b>只合一层</b>：顶层的袋子能和默认语言那份合一次，
     *       于是「这一条没译」能按条退回默认语言；藏在 {@code hero} 底下的话，
     *       整个袋子会被当前语言那份整个换掉——译了三张里的一张，另外两张的字就没了；</li>
     *   <li><b>没有 id 的条目原样放行</b>。v3 及更早的数据整条都在语言层
     *       （条目里自带 title），那时候既没有 id 也没有文案袋子，放行即可——
     *       这正是 v4 不需要迁移脚本的原因。</li>
     * </ol>
     *
     * <p>⚠ 文案袋子拼完就<b>从结果里删掉</b>：它是存储格式的内部细节，不是前台契约。
     * 留着的话前台会多出一份和 {@code nav} 说着同一件事的数据，下一个人迟早会去读它，
     * 而它没有经过这里的拼接。</p>
     */
    private void compose(Map<String, Object> merged)
    {
        joinRows(merged, merged, "nav", "navText");
        joinRows(merged, merged, "highlights", "highlightText");
        joinRows(merged, merged, "capabilities", "capText");
        joinNested(merged, "hero", "slides", "slideText");
        joinNested(merged, "about", "stats", "aboutStatText");
        joinNested(merged, "reach", "stats", "reachStatText");
        // 地图上的地名（2026-09-22 起分语言）：目的地按 id 走文案袋子，总部名是语言层 reach.hqName。
        // 老数据两处都只有共享层那一份英文名，袋子里没有 = 保留共享层的，前台不会空一格。
        joinNested(merged, "reach", "destinations", "reachDestText");
        liftScalar(merged, "reach", "hqName", "hq", "name");
        joinScalar(merged, "bento", "stat", "statValue", "value", "statLabel", "label");
        // 2026-09-21 新增的四块。都走 joinNested：列表在自己那个键底下的 items 里，
        // 文案袋子仍然放语言层顶层（见本方法第 2 条规矩）
        joinNested(merged, "trust", "items", "trustText");
        joinNested(merged, "reviews", "items", "reviewText");
        joinNested(merged, "partners", "items", "partnerText");
        // 合作方下面那排竖屏短视频（2026-09-30）：视频和封面在共享层，说明（caption）在语言层袋子里
        joinNested(merged, "partners", "videos", "partnerVideoText");
        joinNested(merged, "faq", "items", "faqText");
    }

    /**
     * 把 {@code merged[textKey]} 里按 id 存的文案，拼到 {@code owner[listKey]} 的每一条上。
     *
     * <p>就地改的是这两个 Map（换掉那两个键），<b>条目本身是拷一份再改</b>——
     * 底下那份可能是共享层的对象，同一次请求里连着取两种语言的话，直接改会把它写脏。</p>
     *
     * @param merged 合并结果。文案袋子一律从这里取（见 {@link #compose} 第 2 条）
     * @param owner  列表所在的那个 Map。顶层的列表就是 merged 自己
     */
    private void joinRows(Map<String, Object> merged, Map<String, Object> owner,
            String listKey, String textKey)
    {
        // 先删：列表不存在时，文案袋子也不该漏到接口返回里去
        Object textValue = merged.remove(textKey);
        Object listValue = owner.get(listKey);
        if (!(listValue instanceof List))
        {
            return;
        }
        Map<?, ?> text = textValue instanceof Map ? (Map<?, ?>) textValue : null;

        List<Object> out = new ArrayList<>();
        for (Object row : (List<?>) listValue)
        {
            if (!(row instanceof Map))
            {
                // 脏数据（字符串、数字）原样留着，交给前台那边的判断去处理
                out.add(row);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            ((Map<?, ?>) row).forEach((k, v) -> item.put(String.valueOf(k), v));
            Object id = item.get("id");
            Object bag = text == null || id == null ? null : text.get(String.valueOf(id));
            if (bag instanceof Map)
            {
                ((Map<?, ?>) bag).forEach((k, v) -> item.put(String.valueOf(k), v));
            }
            out.add(item);
        }
        owner.put(listKey, out);
    }

    /** 同 {@link #joinRows}，只是列表在 {@code parentKey} 底下（hero.slides、about.stats…） */
    private void joinNested(Map<String, Object> merged, String parentKey, String listKey, String textKey)
    {
        Object parent = merged.get(parentKey);
        if (!(parent instanceof Map))
        {
            // 这一块整个没配。袋子还是要删掉，不然它会单独出现在返回里
            merged.remove(textKey);
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) parent).forEach((k, v) -> copy.put(String.valueOf(k), v));
        joinRows(merged, copy, listKey, textKey);
        merged.put(parentKey, copy);
    }

    /**
     * 不是列表、只有一格的那种：三栏特色里那张粉色数字卡。
     *
     * <p>数字（{@code bento.statValue}）不分语言，说明（{@code bento.statLabel}）分语言，
     * 拼成前台早就认识的 {@code bento.stat:{value,label}}。</p>
     *
     * <p>⚠ 两边不能都叫 {@code stat}：{@link #overlay} 只合一层，语言层那个 {@code stat}
     * 会把共享层那个整个换掉，数字就没了。老数据里整格都在语言层
     * （{@code stat:{value,label}}），那时候这两个键都不存在，于是这里什么都不做，原样放行。</p>
     */
    private void joinScalar(Map<String, Object> merged, String parentKey, String targetKey,
            String sharedKey, String sharedField, String textKey, String textField)
    {
        Object parent = merged.get(parentKey);
        if (!(parent instanceof Map))
        {
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) parent).forEach((k, v) -> copy.put(String.valueOf(k), v));
        Object sharedValue = copy.remove(sharedKey);
        Object textValue = copy.remove(textKey);
        if (sharedValue != null || textValue != null)
        {
            Map<String, Object> target = new LinkedHashMap<>();
            Object old = copy.get(targetKey);
            if (old instanceof Map)
            {
                ((Map<?, ?>) old).forEach((k, v) -> target.put(String.valueOf(k), v));
            }
            if (sharedValue != null)
            {
                target.put(sharedField, sharedValue);
            }
            if (textValue != null)
            {
                target.put(textField, textValue);
            }
            copy.put(targetKey, target);
        }
        merged.put(parentKey, copy);
    }

    /**
     * 把 {@code merged[parentKey][fromKey]}（语言层的一个标量）盖到 {@code merged[parentKey][targetKey][field]} 上。
     * 2026-09-22 起地图总部名分语言用的就是它：共享层 {@code reach.hq.name} 是老数据和没译语言的兜底，
     * 语言层 {@code reach.hqName} 有值就用语言层的。
     *
     * <p>和 {@link #joinScalar} 的区别：那个是把两个平铺的键<b>拼成</b>一个对象；
     * 这个是往<b>已有</b>对象里盖一个字段。提完把 fromKey 删掉——它是存储格式的内部细节，
     * 不是前台契约（同 compose 上那条注释）。</p>
     */
    private void liftScalar(Map<String, Object> merged, String parentKey, String fromKey,
            String targetKey, String field)
    {
        Object parent = merged.get(parentKey);
        if (!(parent instanceof Map))
        {
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) parent).forEach((k, v) -> copy.put(String.valueOf(k), v));
        Object value = copy.remove(fromKey);
        String text = value == null ? null : String.valueOf(value).trim();
        Object old = copy.get(targetKey);
        // 总部只有名字没有坐标时 hq 本来就不存在，这个名字也没有地方可挂——原样丢掉
        if (text != null && !text.isEmpty() && old instanceof Map)
        {
            Map<String, Object> target = new LinkedHashMap<>();
            ((Map<?, ?>) old).forEach((k, v) -> target.put(String.valueOf(k), v));
            target.put(field, text);
            copy.put(targetKey, target);
        }
        merged.put(parentKey, copy);
    }

    // ======== 拼接结束 ========

    /**
     * 把一层内容盖到结果上：同名键两边都是对象就合一层，否则整个换掉。
     *
     * <p>只合一层，不递归。装修内容的嵌套本来就只有两层
     * （{@code contact.address}、{@code hero.image}），
     * 而递归合并会让 {@code about.stats} 这种<b>数组</b>的行为变得难以预料——
     * 数组在这里的语义是「整份替换」：语言层给了三条数字亮点，就是这三条，
     * 不该和共享层的按下标拼起来。</p>
     *
     * @param merged 铺到现在的结果，就地改
     * @param layer  要盖上去的那一层，不是 Map 就什么都不做（没配这个语言）
     */
    private void overlay(Map<String, Object> merged, Object layer)
    {
        if (!(layer instanceof Map))
        {
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) layer).entrySet())
        {
            if (entry.getKey() == null)
            {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object incoming = entry.getValue();
            Object current = merged.get(key);
            if (current instanceof Map && incoming instanceof Map)
            {
                // 拷一份再改：底下那份可能是共享层的对象，直接改会污染它
                Map<String, Object> combined = new LinkedHashMap<>();
                ((Map<?, ?>) current).forEach((k, v) -> combined.put(String.valueOf(k), v));
                ((Map<?, ?>) incoming).forEach((k, v) -> combined.put(String.valueOf(k), v));
                merged.put(key, combined);
            }
            else
            {
                merged.put(key, incoming);
            }
        }
    }

    /**
     * 一条导航都没配时的兜底。
     *
     * <p><b>地址必须保持中立。</b>早先写的是 {@code index.html} / {@code products.html}，
     * 那等于替前台定死了两件事：它是多页静态站、而且 URL 里不带语言前缀。
     * 所以改成根相对路径，语言前缀（前台若走 {@code /ja/products} 那种）由前台自己拼。</p>
     *
     * <p>标题是英文的：这只是「什么都没配」时的兜底。要分语言的导航请在装修里按语言配，
     * 见 {@link #siteContent}。</p>
     */
    private List<Map<String, String>> defaultNav()
    {
        List<Map<String, String>> nav = new ArrayList<>();
        nav.add(navItem("Home", "/"));
        nav.add(navItem("Products", "/products"));
        nav.add(navItem("About Us", "/#about"));
        nav.add(navItem("Contact", "/#inquiry"));
        return nav;
    }

    private Map<String, String> navItem(String title, String url)
    {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("url", url);
        return item;
    }

    private Map<String, Object> defaultHero(SiteInfo site)
    {
        Map<String, Object> hero = new LinkedHashMap<>();
        hero.put("title", site.getSiteName());
        hero.put("subtitle", "");
        hero.put("image", "");
        return hero;
    }

    private String require(String value, String label, int max)
    {
        String text = trimToNull(value);
        if (text == null)
        {
            throw new ServiceException(label + "不能为空");
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String cut(String value, int max)
    {
        String text = trimToNull(value);
        if (text == null)
        {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
