package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.config.ConsoleProperties;
import com.ruoyi.station.console.domain.CurrencyConvertLog;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteFolder;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteImage;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.domain.SitePost;
import com.ruoyi.station.console.domain.SiteRate;
import com.ruoyi.station.console.domain.SiteSeo;
import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.domain.SiteUser;
import com.ruoyi.station.console.domain.SiteVideo;
import com.ruoyi.station.console.domain.SiteModel;
import com.ruoyi.station.console.security.RequirePerm;
import com.ruoyi.station.console.service.CurrencyConvertService;
import com.ruoyi.station.console.service.CustomPlanService;
import com.ruoyi.station.console.service.ModelGenService;
import com.ruoyi.station.console.service.OrderWizardService;
import com.ruoyi.station.console.service.SiteFolderService;
import com.ruoyi.station.console.service.PageViewStatService;
import com.ruoyi.station.console.service.RateFetcher;
import com.ruoyi.station.console.service.RateService;
import com.ruoyi.station.console.service.SeoAuditService;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteBuyerService;
import com.ruoyi.station.console.service.SiteCategoryService;
import com.ruoyi.station.console.service.SiteFileService;
import com.ruoyi.station.console.service.SiteFollowupService;
import com.ruoyi.station.console.service.SiteGoodsService;
import com.ruoyi.station.console.service.SiteInfoService;
import com.ruoyi.station.console.service.SiteInquiryService;
import com.ruoyi.station.console.service.SiteOpenService;
import com.ruoyi.station.console.service.SiteOrderService;
import com.ruoyi.station.console.service.SitePostService;
import com.ruoyi.station.console.service.SiteSeoService;
import com.ruoyi.station.console.service.SiteStorageService;
import com.ruoyi.station.console.service.SiteTranslateService;
import com.ruoyi.station.console.service.SiteUserService;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * 管理台的数据接口。
 *
 * <h3>⚠ 这个类整个翻过来了：从「转发」变成「查本地库」</h3>
 * <p>以前每一个方法都是对 ERP 那组 S2S 数据接口的一次转发，
 * 类注释里写着「这个服务不需要看懂业务数据，原样转发 JSON」。</p>
 *
 * <p><b>那条路整个删了</b>（docs/独立站与ERP的边界.md §10.1、§10.3.6）：
 * 那套 S2S 客户端（HTTP 客户端 + 它的配置类和异常类）全部删除，
 * 每个方法改成调本地 Service。</p>
 *
 * <p><b>对外的 URL 路径一个都没变</b>——§10.3.6 明确要求
 * 「station-console-ui 不用跟着改」。下面标了「路径不变」的就是这个意思。</p>
 *
 * <h3>四个端点没了</h3>
 * <ul>
 *   <li>{@code GET /api/catalog/categories}、{@code GET /api/catalog/goods}
 *       —— 那是「从 ERP 商品档案里挑商品」的选择器。ERP 的商品档案够不着了，
 *       商品改成站点自己录（§5.4），选择器随之消失。
 *       <b>这两个是前端要跟着改的地方</b>：「加商品」弹窗从「挑」变成「填表」。</li>
 *   <li>{@code POST /api/inquiry/{id}/convert} —— 「询盘转 ERP 客户」，
 *       往 {@code sdsshop.u_m_buyer} 写数据，是一条互通（§3.2）。</li>
 *   <li>{@code POST /api/goods} 路径还在，但<b>请求体变了</b>：
 *       从 {@code ["code1","code2"]}（挑哪几个）变成一个完整的商品对象（录一个）。</li>
 * </ul>
 *
 * <h3>新增的一组：{@code /api/user/**}</h3>
 * <p>本站自己的用户体系（§6），ERP 那边没有对应物。</p>
 *
 * <h3>@PathVariable / @RequestParam 一律写死名字</h3>
 * <p>不写名字时 Spring 靠反射读参数名，而参数名只有编译带了 {@code -parameters} 才在 class 里。
 * 本工程是 BOM import、没继承 {@code spring-boot-starter-parent}，那个开关得自己在 pom 里打开——
 * 漏了就是运行期一句 {@code Name for argument of type [java.lang.String] not specified}，
 * <b>而且编译和启动都不报，只有真发到那个接口才炸</b>。pom 里已经补上开关，这里再写死一遍。</p>
 *
 * <h3>身份和权限</h3>
 * <p>身份只从服务端会话取，前端传什么都不看。权限由 {@link RequirePerm} + {@code AuthInterceptor} 判——
 * <b>这一层是唯一的授权点</b>，ERP 不再参与，漏挂一个注解那个接口就是无权限的
 * （所以拦截器对没挂注解的方法一律拒绝）。</p>
 */
@RestController
@RequestMapping("/api")
public class ConsoleApiController
{
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CurrencyConvertService currencyConvertService;

    /**
     * 公开接口那个 service。这里只用它算装修预览
     * （{@link #previewContent}）——合并规则只有它那一份，管理台不重算。
     */
    @Autowired
    private SiteOpenService siteOpenService;

    @Autowired
    private SiteGoodsService goodsService;

    /** 买家 3D 定制方案（2026-09-29），管理台这边只读 */
    @Autowired
    private CustomPlanService customPlanService;

    @Autowired
    private SiteInquiryService inquiryService;

    @Autowired
    private SiteFileService fileService;

    @Autowired
    private ModelGenService modelGenService;

    @Autowired
    private SiteStorageService storageService;

    @Autowired
    private SiteFolderService folderService;

    @Autowired
    private SiteUserService userService;

    @Autowired
    private SiteOrderService orderService;

    @Autowired
    private OrderWizardService orderWizardService;

    @Autowired
    private SiteBuyerService buyerService;

    @Autowired
    private ConsoleProperties consoleProperties;

    @Autowired
    private RateService rateService;

    @Autowired
    private RateFetcher rateFetcher;

    @Autowired
    private PageViewStatService pageViewStatService;

    @Autowired
    private SiteFollowupService followupService;

    @Autowired
    private SitePostService postService;

    @Autowired
    private SiteSeoService siteSeoService;

    @Autowired
    private SeoAuditService seoAuditService;

    @Autowired
    private SiteTranslateService siteTranslateService;

    @Autowired
    private SiteCategoryService siteCategoryService;

    // ==================== 当前身份 ====================

    /**
     * 前端顶栏用的当前身份。路径不变。
     *
     * <p>不查库：会话里那份 + 站点档案（进程内缓存）。<b>整个管理台的第一帧等着它</b>
     * （{@code main.js} 先拉这一趟再 mount），所以这里不许出现真正的查询——
     * 多一次往返全站首屏就慢一次。</p>
     *
     * <p><b>{@code perms} 的含义变了</b>：以前它只用来决定界面上显示不显示某个按钮
     * （真正的授权在 ERP 侧现查）；现在它就是授权依据本身，
     * 界面显隐和接口放行用的是同一份。滞后性的取舍见 {@link ConsoleIdentity} 类注释。</p>
     */
    @GetMapping("/me")
    @RequirePerm
    public Map<String, Object> me(HttpServletRequest request)
    {
        ConsoleIdentity identity = ConsoleSession.require(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siteCode", identity.getSiteCode());
        /*
          ⚠ siteName / langs / currency 这三个也从**站点档案**读，不读会话快照。
          和下面 consoleLang 同一个理由，而且这三个是踩出来的，不是推演出来的：

          2026-09-22 现场：站长把结算币种从 EUR 改成 USD，商品价确实按汇率换算了
          （库里 458.8419），但商品管理那一列画的还是 `€458.84`——数字换了符号没换，
          看着像「换算只做了一半」，最容易被当成后端算错去查，而后端是对的。

          第一版的修法是在保存站点设置时把会话里那份改掉（refreshIdentityFromSite，
          已删）。**那个修法在这个项目里根本不成立**：会话存在 Redis 里
          （spring-boot-starter-session-data-redis，命名空间 station:console:session:*），
          而 Spring Session 默认 saveMode=ON_SET_ATTRIBUTE ——
          只有显式 setAttribute 过的属性才写回。原地改对象的字段一个字都不会进 Redis，
          下一个请求从 Redis 反序列化出来的还是旧值。它不报错、不警告，
          本地用内存会话测还是「好的」。

          改成在这儿读档案就不存在这个问题：档案是进程内缓存，写操作会 evict，
          而且**所有已经建立的老会话立刻全部正确**，不用等人重新登录。

          这三个字段全项目只有这个方法在消费（identity 上的 setter 留着给
          SsoController 建会话用，不再有读的人）。require() 走缓存，不查库——
          整个管理台的第一帧等着这一趟，见上面。
        */
        SiteInfo site = siteInfoService.require();
        data.put("siteName", site.getSiteName());
        data.put("langs", site.langList());
        /*
          管理台界面用哪种语言。

          ⚠ 读的是站点档案，**不是会话快照**，和上面那几个不一样。两个原因：

            · 会话是从 ERP 验票那一刻拷进去的。站长刚在「站点设置」里把管理台语言
              改成法语，自己这个会话里还是旧值——而这恰恰是最会被当成「保存没生效」
              的一个字段，因为它改的就是眼前这一屏；
            · 它没有 setter 也就不必进 ConsoleIdentity：Redis 里躺着的旧会话
              反序列化出来会是 null，又得在这儿兜一次，白加一层。

          require() 走的是进程内缓存，不是每次查库（SiteInfoService）。
        */
        data.put("consoleLang", site.consoleLangOrDefault());
        data.put("currency", site.baseCurrency());
        data.put("userName", identity.getUserName());
        data.put("displayName", identity.displayName());
        data.put("roleCode", identity.getRoleCode());
        data.put("perms", identity.getPerms());
        // 能不能开「站点用户」那一页。只有从 ERP「站点用户」按钮进来的会话是 true，见 ConsoleIdentity
        data.put("userAdmin", identity.isUserAdmin());
        // 左边菜单里摆不摆「用户管理」。按部署开关（console.users-menu），统领 true、法国站 false
        data.put("usersMenu", consoleProperties.isUsersMenu());
        // 这次是怎么进来的。只有 'erp' 一个值了（本站没有登录页），下发它只为让前端
        // 在需要时能区分历史会话，界面上不显示
        data.put("via", identity.getVia());
        data.put("erpHomeUrl", consoleProperties.erpHomeUrl());
        // ⚠ siteKey 不再下发：那一列删了（§7.2）。前台取的是自己这台机器上的后端，
        //   没有范围要限定。前端如果还在读它，会拿到 undefined——那正是该改的地方
        // ⚠ shopCode 不下发：一个部署一个站，界面上一处都用不到它
        return ok(data);
    }

    // ==================== 站点 ====================

    /** 1. 站点档案，含首页装修内容。路径不变 */
    @GetMapping("/site")
    @RequirePerm("site:station:list")
    public Map<String, Object> site()
    {
        return ok(siteInfoService.require());
    }

    /**
     * 2. 保存首页装修<b>草稿</b>。路径不变，语义在 2026-09-13 变了。
     *
     * <p>请求体是完整的装修 JSON 字符串，不是拆开的字段：这块内容整体存整体读，
     * 拆成字段传过来还得在服务端拼回去，中间少一个键就是首页少一块。</p>
     *
     * <p><b>⚠ 这个接口不再直接改前台看到的内容。</b>在此之前它写的是 contentjson，
     * 也就是「保存即上线」；现在写的是 draftjson，要再调一次 {@link #publishContent}
     * 才见客。路径和方法都没变是有意的：管理台和后端是分开发版的，
     * 万一只发了后端，老管理台点保存仍然能存下来，只是存成了草稿——
     * 比 404 强得多（那会让商家以为自己填的东西全丢了）。</p>
     */
    @PutMapping(value = "/site/content", consumes = MediaType.TEXT_PLAIN_VALUE)
    @RequirePerm("site:station:edit")
    public Map<String, Object> saveContent(@RequestBody(required = false) String contentJson,
            HttpServletRequest request)
    {
        return ok(siteInfoService.saveDraft(contentJson, operator(request)), "草稿已保存");
    }

    /**
     * 2c. 发布：把草稿推成前台看到的那份。
     *
     * <p>不带请求体——要发布的就是库里那份草稿。让管理台把 JSON 再传一遍的话，
     * 就有了「发布的和刚才保存的不是同一份」的可能（两个标签页各开一个装修页面，
     * 后一个发布时把前一个的草稿顶掉），而这种错只会在事后被发现。</p>
     */
    @PostMapping("/site/content/publish")
    @RequirePerm("site:station:edit")
    public Map<String, Object> publishContent(HttpServletRequest request)
    {
        return ok(siteInfoService.publishDraft(operator(request)), "已发布，刷新站点即可看到");
    }

    /**
     * 2d. 放弃草稿，回到上一次发布的那份。
     *
     * <p>已发布那份一个字不动，所以这也是这一版的「回滚」：改坏了点它就回去了。</p>
     */
    @DeleteMapping("/site/content/draft")
    @RequirePerm("site:station:edit")
    public Map<String, Object> discardDraft()
    {
        return ok(siteInfoService.discardDraft(), "已放弃草稿");
    }

    /**
     * 2e. 机器翻译：把一批文案从一种语言翻成另一种。
     *
     * <p>请求体 {@code { from, to, texts: [...] }}，返回 {@code { texts: [...] }}，
     * <b>和入参等长、按下标对应</b>。空串原样回来，不消耗翻译额度。</p>
     *
     * <p>只翻字符串，不认识装修 JSON 的结构：哪些格子要翻、翻回来填到哪儿，
     * 全由管理台那边决定。这么分是因为「哪些字段算文案」这件事在表单里最清楚
     * （它本来就按共享层/语言层分好了），搬到后端来就要再维护一份字段清单。</p>
     */
    @PostMapping("/site/translate")
    @RequirePerm("site:station:edit")
    public Map<String, Object> translate(@RequestBody Map<String, Object> body)
    {
        String from = body.get("from") == null ? null : String.valueOf(body.get("from"));
        String to = body.get("to") == null ? null : String.valueOf(body.get("to"));
        List<String> texts = new ArrayList<>();
        Object raw = body.get("texts");
        if (raw instanceof List<?> list)
        {
            for (Object item : list)
            {
                texts.add(item == null ? "" : String.valueOf(item));
            }
        }
        return ok(Map.of("texts", siteTranslateService.translate(from, to, texts)));
    }

    /**
     * 2b. 装修预览：把一份**还没保存**的装修 JSON 算成前台会看到的那份数据。
     *
     * <p>返回的形状和公开接口 {@code /openapi/site/info} 一模一样。管理台把它
     * postMessage 给预览窗口，那个窗口顶掉自己那份就渲染出草稿的样子——
     * <b>合并规则只有后端这一份</b>，前端不重算，也就不会和线上不一致。</p>
     *
     * <p>⚠ 不写库。商家点「保存」之前，库里那份一个字都不会变。</p>
     *
     * <p>⚠ 权限用 edit 而不是 list：能看到还没保存的草稿，等于能看到别人正在改什么，
     * 而且这个能力只有正在编辑的人用得上。</p>
     */
    @PostMapping(value = "/site/content/preview", consumes = MediaType.TEXT_PLAIN_VALUE)
    @RequirePerm("site:station:edit")
    public Map<String, Object> previewContent(@RequestBody(required = false) String contentJson,
            @RequestParam(required = false) String lang)
    {
        return ok(siteOpenService.siteInfoPreview(lang, contentJson));
    }

    /**
     * 3. 改站点档案（域名、语言、币种、前台开关、通知邮箱）。<b>新增的</b>。
     *
     * <p>ERP 那版没有这个：站点档案在 ERP 那边改，管理台只读。现在档案一分为二——
     * 只有站点编码和跳转密钥还必须和 ERP 登记的那份一致（§4.3，改了「进入管理」就进不来），
     * 其余都搬到本站（§7.2），搬过来的当然要能在本站改。</p>
     *
     * <p><b>⚠ 域名归本站，不归 ERP。</b>它一度被算进「留在 ERP 的登记信息」里，
     * 管理台上还写着「在 ERP 里改」——但两边库之间从来没有同步通道，
     * 在 ERP 改完这边纹丝不动。而它空着的时候，买家的验证邮件和找回密码邮件发不出去
     * （{@code BuyerAuthService#siteBase} 拼不出链接就抛 500）。</p>
     *
     * <p><b>⚠ 这是个整份覆盖的接口，不是 PATCH。</b>{@code langs} / {@code currencies} /
     * {@code notifyemail} 在 SQL 里是直赋值，请求体里不带就是清空——这是有意的，
     * 「不给买家切币种了」得做得到。只有 {@code domain} 和 {@code remark} 走 coalesce，
     * 因为它们曾经被表单漏送、于是每保存一次站点设置就被抹一次。</p>
     *
     * <p><b>⚠ 改结算币种会连带换算全站金额</b>，商品价、型号价、<b>以及已经下的订单</b>
     * 一起按当前汇率乘一遍，见 {@code CurrencyConvertService}。这是有代价的：
     * 已经发给买家的报价邮件里是旧币种旧数字，库里换完就对不上了；而且<b>不可逆</b>。
     * 所以调用方（管理台的确认框）必须先把这件事说清楚。</p>
     *
     * <p>只想改正一个填错的币种代码、数字一个不动，请求里显式送
     * {@code "convertAmounts": false}。不送就是换算——默认值落在这一边是有意的，
     * 见 {@code SiteInfo#shouldConvertAmounts()}。</p>
     */
    @PutMapping("/site")
    @RequirePerm("site:station:edit")
    public Map<String, Object> saveSite(@RequestBody SiteInfo site, HttpServletRequest request)
    {
        // ⚠ 这里曾经还有一句 refreshIdentityFromSite(request)，把会话里那份站点快照
        //   改掉。删了：会话在 Redis 上，原地改对象根本不会被写回（理由写在 /me 上）。
        //   现在 /me 直接读站点档案，不需要碰会话。
        CurrencyConvertLog converted = siteInfoService.update(site, operator(request));

        // ⚠ reload 不能省。update() 里的 evict 发生在**事务提交之前**，那个窗口里
        //   任何一次前台渲染（requirePublic 每个请求都读）都可能把还没提交的旧值
        //   重新缓存起来；提交之后缓存里躺着的仍然是旧的。而 /me 现在就读这份缓存，
        //   旧值的表现就是「设置里是 USD、商品管理还画着 €」。全文见 SiteInfoService#reload
        siteInfoService.reload();
        return ok(converted);
    }

    /**
     * 3b. 改结算币种之前看一眼汇率。<b>不改任何数据。</b>
     *
     * <p>管理台的确认框拿它给运营看两个数：会用哪个汇率、换完 1 块钱变多少。
     * 没有这一步的话，确认框只能说一句「将按汇率换算」——而运营真正要判断的是
     * 「这个汇率对不对」，那是他唯一能在事前发现汇率拉歪了的机会。</p>
     *
     * <p><b>⚠ 汇率取不到时返回 {@code rate = null} + 一句原因，而不是抛异常。</b>
     * 抛出去的话前端弹一个红框、确认流程断在这里，运营连「只改代码不换算」
     * 那条路都走不了——而那条路恰恰不需要汇率。</p>
     */
    @GetMapping("/site/currency-preview")
    @RequirePerm("site:station:edit")
    public Map<String, Object> previewCurrency(@RequestParam("to") String to)
    {
        SiteInfo site = siteInfoService.require();
        String from = site.baseCurrency();
        String target = to == null ? "" : to.trim().toUpperCase(Locale.ROOT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from);
        data.put("to", target);
        data.put("same", from.equals(target));
        data.put("rate", null);
        data.put("error", null);
        if (!from.equals(target) && !target.isEmpty())
        {
            try
            {
                data.put("rate", currencyConvertService.previewRate(from, target));
            }
            catch (ServiceException e)
            {
                data.put("error", e.getMessage());
            }
        }
        return ok(data);
    }

    // ==================== 汇率 ====================

    /**
     * 3a. 汇率状态。
     *
     * <p>给管理台展示用：最后一次成功拉取的时间、当前生效的汇率、拉取失败的原因。
     * <b>免费汇率 API 静悄悄挂掉是这个功能最可能的故障</b>，而它在页面上没有任何
     * 其他症状——价格照常显示，只是停在几周前的汇率上。所以这一屏必须存在。</p>
     */
    @GetMapping("/rate")
    @RequirePerm("site:station:edit")
    public Map<String, Object> rateStatus()
    {
        SiteRate rate = rateService.require();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCode", rate == null ? null : rate.getBaseCode());
        data.put("fetchTime", rate == null ? null : rate.getFetchTime());
        data.put("fetchStatus", rate == null ? null : rate.getFetchStatus());
        data.put("fetchError", rate == null ? null : rate.getFetchError());
        data.put("overrideJson", rate == null ? null : rate.getOverrideJson());
        data.put("staleDays", rateService.staleDays());
        data.put("staleLimit", SiteRate.STALE_DAYS);
        // 以站点本币为基准的生效汇率，就是前台实际用的那一份——
        // 让运营看到的和买家看到的是同一个数，不要在这里另算一遍
        SiteInfo site = siteInfoService.require();
        data.put("rates", rateService.ratesFor(site.baseCurrency(), site.currencyList()));
        data.put("baseCurrency", site.baseCurrency());
        /*
          最近几次币种换算。挂在这张卡片上而不是单开一个接口：运营会来看汇率，
          正是因为他在琢磨币种的事——两件事在同一屏里才对得上。

          ⚠ 少了这一行，u_s_currency_convert_log 就是一张只写不读的表，
            而它存在的全部理由是「事后查得到那次用的什么汇率」。换算不可逆，
            那个数字是唯一的还原依据。
        */
        data.put("convertLogs", currencyConvertService.recentLogs(5));
        return ok(data);
    }

    /**
     * 3b. 改手工覆盖的汇率。
     *
     * <p>请求体 {@code {"overrideJson": "{\"EUR\":0.90}"}}，传空串或 null 表示不覆盖、
     * 全用自动拉取的值。</p>
     *
     * <p>⚠ 覆盖值相对的是 {@code u_s_rate.base_code}（现在是 USD），<b>不是站点本币</b>。
     * 这一点在管理台页面上要写清楚，否则运营会按「1 美元换 7.2 人民币」的直觉填反。</p>
     */
    @PutMapping("/rate")
    @RequirePerm("site:station:edit")
    public Map<String, Object> saveRate(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        Object value = body == null ? null : body.get("overrideJson");
        String json = value == null ? null : String.valueOf(value).trim();
        return ok(rateService.saveOverride(json == null || json.isEmpty() ? null : json,
                operator(request)));
    }

    /**
     * 3c. 立即刷新汇率。
     *
     * <p>让运营当场看到结果，而不是改完配置去翻服务器日志等第二天。
     * 返回 {@code true} 表示这次拉成功了。</p>
     */
    @PostMapping("/rate/refresh")
    @RequirePerm("site:station:edit")
    public Map<String, Object> refreshRate()
    {
        boolean ok = rateFetcher.fetchNow();
        return ok(ok, ok ? "汇率已更新" : "拉取失败，仍在用上一次的汇率，原因见下方状态");
    }

    // ==================== 商品 ====================

    /** 4. 商品列表。路径不变。后台视角，含草稿和已下架 */
    @GetMapping("/goods")
    @RequirePerm("site:goods:list")
    public Map<String, Object> goodsList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "publishStatus", required = false) String publishStatus,
            @RequestParam(value = "category", required = false) String category)
    {
        List<SiteGoods> rows = goodsService.selectList(keyword, publishStatus, category);
        // 外壳保持 {total, rows}：前端表格组件认的是这两个键
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("rows", rows);
        return okTable(data);
    }

    /** 5. 单条详情：商品档案 + 全部语言文案 + 本站开了哪几种语言。路径不变 */
    @GetMapping("/goods/{goodsCode}")
    @RequirePerm("site:goods:query")
    public Map<String, Object> goodsDetail(@PathVariable("goodsCode") String goodsCode)
    {
        Map<String, Object> detail = goodsService.selectDetail(goodsCode);
        if (detail == null)
        {
            throw new ServiceException("这个商品不存在");
        }
        return ok(detail);
    }

    /**
     * 6. 新建一个商品。<b>路径不变，请求体变了。</b>
     *
     * <p>ERP 那版收的是 {@code List<String> goodsCodes}——「把 ERP 商品档案里的这几个
     * 加进本站」。现在收一个完整的商品对象：名称、规格、图片、价格全在本站录（§5.4）。</p>
     *
     * <p><b>前端要跟着改</b>：「加商品」弹窗从一个 ERP 商品选择器变成一张表单。
     * 这是这次拆分里前端唯一必须动的地方。</p>
     */
    @PostMapping("/goods")
    @RequirePerm("site:goods:add")
    public Map<String, Object> goodsAdd(@RequestBody SiteGoods goods, HttpServletRequest request)
    {
        String code = goodsService.createGoods(goods, operator(request));
        return ok(code, "已创建商品 " + code + "，状态为草稿");
    }

    /**
     * 7. 保存商品档案和各语言文案。路径不变。
     *
     * <p>请求体形如：</p>
     * <pre>
     * {
     *   goods:  {...},
     *   langs:  [{lang, title, subtitle, description, keywords}],
     *   combos: [{comboCode, price, image, sortNum, status, titles: {en: '...', ja: '...'}}]
     * }
     * </pre>
     *
     * <p>档案、文案、组合在一个事务里存：只存住一半的话，站点上会出现
     * 「有标题没价格」这种半截数据。</p>
     *
     * <p>⚠ {@code combos} <b>缺省和空数组不是一回事</b>：缺省 = 这次不动组合
     * （老版本前端就不带这个字段），空数组 = 把这个商品的组合全删掉。
     * 所以这里用 {@code containsKey} 判断，不能直接取值判空——
     * {@code body.get("combos")} 对这两种情况都返回 null。</p>
     */
    @PutMapping("/goods")
    @RequirePerm("site:goods:edit")
    public Map<String, Object> goodsEdit(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        SiteGoods goods = JSON.convertValue(body.get("goods"), SiteGoods.class);
        if (goods == null)
        {
            throw new ServiceException("缺少商品信息");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> langs = (List<Map<String, Object>>) body.get("langs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> combos = body.containsKey("combos")
                ? (List<Map<String, Object>>) (body.get("combos") == null
                        ? new ArrayList<Map<String, Object>>() : body.get("combos"))
                : null;
        Object saved = goodsService.saveGoods(goods, langs, combos, operator(request));

        /*
         * 顺手补齐这个商品分类的各语言译名。
         *
         * ⚠ 放在这里而不是 saveGoods 里面，是因为那个方法是 @Transactional 的：
         * 翻译要打一次腾讯云的 HTTP 接口，几种语言下来可能一两秒，放进事务里等于
         * 让数据库事务白白开着等一个外部服务。这里已经提交完了。
         *
         * ⚠ 这一句不会抛异常（见 autoFillQuietly）。保存商品和翻分类名是两件事，
         * 不能让腾讯云抖一下就让商家存不了商品——失败了前台先显示原文，
         * 下次保存、或者在管理台点一次「一键补齐」再补上。
         */
        siteCategoryService.autoFillQuietly(goods.getSiteCategory());
        return ok(saved);
    }

    // ==================== 分类译名 ====================

    /**
     * 9a. 分类译名表：行是全站分类，列是各语言。管理台那个弹窗打开时拉一次。
     *
     * <p>返回 {@code { keys: [...], langs: [...], defaultLang, names: { catkey: { lang: 名 } } }}。
     * {@code keys} 已经按路径排好序，父级排在子级前面，前端直接按缩进渲染即可。</p>
     */
    @GetMapping("/categories/langs")
    @RequirePerm("site:goods:edit")
    public Map<String, Object> categoryLangs()
    {
        return ok(siteCategoryService.adminTable());
    }

    /**
     * 9b. 保存手工改过的译名格子。
     *
     * <p>请求体 {@code { rows: [{catKey, lang, name}] }}。<b>只写提交上来的那几格</b>，
     * 没提交的一个字不动——译名是站点级共享的，按「提交的就是全部」去删会把
     * 别的分类的译名一起删掉。</p>
     */
    @PostMapping("/categories/langs")
    @RequirePerm("site:goods:edit")
    public Map<String, Object> categoryLangsSave(@RequestBody Map<String, Object> body,
            HttpServletRequest request)
    {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        return ok(Map.of("saved", siteCategoryService.saveManual(rows, operator(request))));
    }

    /**
     * 9c. 一键补齐：把全站分类缺的译名翻出来。<b>已有的永远不覆盖</b>，
     * 所以重复点是安全的，全都有译名时一次翻译接口都不调。
     *
     * <p>返回 {@code { filled, failed: [语种] }}。某个语种失败不影响其他语种，
     * 失败的会列在 {@code failed} 里让用户看见——这和商品保存那条路不一样，
     * 那边是后台静默补，这边是用户主动点的按钮，失败必须可见。</p>
     */
    @PostMapping("/categories/translate")
    @RequirePerm("site:station:edit")
    public Map<String, Object> categoryTranslate()
    {
        return ok(siteCategoryService.fillAll());
    }

    /** 8. 批量上架 / 下架。路径不变 */
    @PostMapping("/goods/status")
    @RequirePerm("site:goods:edit")
    public Map<String, Object> goodsStatus(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) body.get("goodsCodes");
        String status = body.get("publishStatus") == null ? null : String.valueOf(body.get("publishStatus"));
        return ok(goodsService.changeStatus(
                codes == null ? new String[0] : codes.toArray(new String[0]), status, operator(request)));
    }

    /**
     * 9. 删除商品。路径不变。
     *
     * <p>⚠ 语义变了：ERP 那版删的是「这个站上架了它」这层关系，ERP 的商品档案还在。
     * 现在<b>商品档案就在本站</b>，删了就是真删了，连多语言文案一起。</p>
     */
    @DeleteMapping("/goods/{goodsCodes}")
    @RequirePerm("site:goods:remove")
    public Map<String, Object> goodsRemove(@PathVariable("goodsCodes") String[] goodsCodes)
    {
        return ok(goodsService.deleteGoods(goodsCodes));
    }

    // ==================== 询盘 ====================

    /** 10. 询盘列表。路径不变 */
    @GetMapping("/inquiry")
    @RequirePerm("site:inquiry:list")
    public Map<String, Object> inquiryList(SiteInquiry query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(inquiryService.selectList(query, pageNum, pageSize));
    }

    /** 11. 询盘详情。路径不变 */
    @GetMapping("/inquiry/{id}")
    @RequirePerm("site:inquiry:query")
    public Map<String, Object> inquiryDetail(@PathVariable("id") Long id)
    {
        SiteInquiry inquiry = inquiryService.selectById(id);
        if (inquiry == null)
        {
            throw new ServiceException("询盘不存在");
        }
        return ok(inquiry);
    }

    /** 12. 改处理状态和内部备注。路径不变。客户填的内容一个字都不许改——那是原始凭据 */
    @PutMapping("/inquiry")
    @RequirePerm("site:inquiry:edit")
    public Map<String, Object> inquiryEdit(@RequestBody SiteInquiry inquiry, HttpServletRequest request)
    {
        return ok(inquiryService.updateHandle(inquiry, operator(request)));
    }

    /**
     * 12.1 询盘转客户。
     *
     * <h3>⚠ 这不是 ERP 那个「转客户」</h3>
     * <p>这里原来有一段注释说明 {@code POST /api/inquiry/{id}/convert} 被删掉了，
     * 因为它往 {@code sdsshop.u_m_buyer} 建 ERP 客户档案——那是一条互通
     * （boundary §3.2）。<b>那条互通永远不会回来。</b></p>
     *
     * <p>现在这个接口把询盘挂到<b>本站自己的</b> {@code u_s_buyer} 上，
     * 也就是 {@code u_s_inquiry.buyercode} 那一列的注释一直写着的「将来转的是这里」。
     * 全程不出这台机器。</p>
     *
     * <p>可以重复点：已经转过的只会更新一下联系方式，不会重复建档。</p>
     */
    @PostMapping("/inquiry/{id}/convert")
    @RequirePerm("site:buyer:edit")
    public Map<String, Object> inquiryConvert(@PathVariable("id") Long id)
    {
        return ok(buyerService.linkInquiry(id));
    }

    // ==================== 订单 ====================
    //
    // boundary §11 把「订单管理」列在「留位置」里。这一组接口是那一格真正长出东西的地方，
    // 依据是前台的询价流程已经把模型定完了（station-site/app/lib/shop.ts）。
    //
    // ⚠ 没有「新建订单」接口。订单只有两个来源：前台买家自助提交
    //   （OpenApiController 的 POST /openapi/site/order），以及将来可能加的
    //   管理台手工录（source=console，现在还没做）。管理台这一组只读和推进状态。
    //
    // ⚠ 也没有任何「同步到 ERP」的接口。要点 4：两边订单没有隶属关系；
    //   §8：一期人工在 ERP 里录一张，不做也不预埋接口。

    /** 15. 订单列表 */
    @GetMapping("/order")
    @RequirePerm("site:order:list")
    public Map<String, Object> orderList(SiteOrder query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(orderService.selectList(query, pageNum, pageSize));
    }

    /** 16. 订单详情（含订单行） */
    @GetMapping("/order/{orderCode}")
    @RequirePerm("site:order:query")
    public Map<String, Object> orderDetail(@PathVariable("orderCode") String orderCode)
    {
        return ok(orderService.detail(orderCode));
    }

    /**
     * 17. 推进订单状态。
     *
     * <p>只能往前走，外加「任何状态都能取消」。为什么不让随便改，见
     * {@link com.ruoyi.station.console.service.SiteOrderService#advance}。</p>
     */
    @PostMapping("/order/{orderCode}/status")
    @RequirePerm("site:order:edit")
    public Map<String, Object> orderStatus(@PathVariable("orderCode") String orderCode,
            @RequestBody Map<String, String> body, HttpServletRequest request)
    {
        String status = body == null ? null : body.get("status");
        return ok(orderService.advance(orderCode, status, operator(request)));
    }

    /** 18. 改订单的内部备注。买家看不到这一段 */
    @PutMapping("/order/{orderCode}/remark")
    @RequirePerm("site:order:edit")
    public Map<String, Object> orderRemark(@PathVariable("orderCode") String orderCode,
            @RequestBody Map<String, String> body, HttpServletRequest request)
    {
        String remark = body == null ? null : body.get("remark");
        return ok(orderService.updateRemark(orderCode, remark, operator(request)));
    }

    // ==================== 引导式下单（2026-09-28） ====================
    //
    // 设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md
    // 权限复用 site:station:*：向导和首页装修一样是「站点长什么样」，不单开一套权限。
    // 保存即生效，不做草稿两态——不开开关就没人看得到，理由见设计文档「存储」。

    /** 向导编辑页：开关 + 原样的 schema 文本 */
    @GetMapping("/wizard")
    @RequirePerm("site:station:list")
    public Map<String, Object> wizardGet()
    {
        return ok(orderWizardService.adminView());
    }

    /** 存向导。格式不对直接 400，报错说清是哪个分组 / 字段 / 选项 */
    @PutMapping("/wizard")
    @RequirePerm("site:station:edit")
    public Map<String, Object> wizardSave(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        Object enabled = body == null ? null : body.get("enabled");
        Object schema = body == null ? null : body.get("schemaJson");
        orderWizardService.save(Boolean.TRUE.equals(enabled), schema == null ? null : String.valueOf(schema),
                operator(request));
        return ok(orderWizardService.adminView());
    }

    /** 有哪些预置模板、哪份是本站的（按站点编码对应，见 OrderWizardService.PRESETS） */
    @GetMapping("/wizard/templates")
    @RequirePerm("site:station:edit")
    public Map<String, Object> wizardTemplates()
    {
        return ok(orderWizardService.templates());
    }

    /**
     * 取一份预置模板。只是取出来给编辑器，不落库——落库仍走上面那个保存。
     * 不带 code = 本站对应的那份；本站没有对应模板时报错，不替人挑别家的
     */
    @GetMapping("/wizard/template")
    @RequirePerm("site:station:edit")
    public Map<String, Object> wizardTemplate(@RequestParam(value = "code", required = false) String code)
    {
        return ok(orderWizardService.template(code));
    }

    // ==================== 客户 ====================
    //
    // ⚠ 没有「新建客户」接口。档案是询盘/下单时按邮箱长出来的
    //   （SiteBuyerService#resolveByEmail），管理台只能改标签和备注。
    //   理由见 SiteBuyer 的类注释：没有「新建」按钮，就不会有对不上任何人的档案。
    //
    // ⚠ 和 ERP 的 u_m_buyer 没有任何关系（§6c）。这一组里没有一个接口会出这台机器。

    /** 19. 客户列表 */
    @GetMapping("/buyer")
    @RequirePerm("site:buyer:list")
    public Map<String, Object> buyerList(SiteBuyer query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(buyerService.selectList(query, pageNum, pageSize));
    }

    /**
     * 20. 客户详情：档案 + 他的订单 + 他的询盘。
     *
     * <p>三样一起返回，不拆成三个接口——运营点开一个客户就是想看
     * 「这个人跟我们来往过什么」。</p>
     */
    @GetMapping("/buyer/{buyerCode}")
    @RequirePerm("site:buyer:query")
    public Map<String, Object> buyerDetail(@PathVariable("buyerCode") String buyerCode)
    {
        return ok(buyerService.detail(buyerCode));
    }

    /** 21. 改客户的标签和备注。联系方式不给改——那是客户自己填的，改了就对不上他的来信 */
    @PutMapping("/buyer/{buyerCode}/note")
    @RequirePerm("site:buyer:edit")
    public Map<String, Object> buyerNote(@PathVariable("buyerCode") String buyerCode,
            @RequestBody Map<String, String> body)
    {
        String tag = body == null ? null : body.get("tag");
        String remark = body == null ? null : body.get("remark");
        return ok(buyerService.updateNote(buyerCode, tag, remark));
    }

    // ==================== 买家 3D 定制方案（2026-09-29） ====================
    //
    // 只读：买家在前台 /customize 保存的方案。设计 docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md

    /** 定制方案列表。from / to 是 yyyy-MM-dd，含两端 */
    @GetMapping("/customplan")
    @RequirePerm("site:customplan:list")
    public Map<String, Object> customPlanList(
            @RequestParam(value = "goodsCode", required = false) String goodsCode,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(customPlanService.selectList(goodsCode, from, to, pageNum, pageSize));
    }

    /** 定制方案详情：整行（带买家留的邮箱、备注）+ 解析后的标注 */
    @GetMapping("/customplan/{planId}")
    @RequirePerm("site:customplan:list")
    public Map<String, Object> customPlanDetail(@PathVariable("planId") String planId)
    {
        return ok(customPlanService.selectDetail(planId));
    }

    // ==================== 文件资源 ====================
    //
    // 图片和视频是两张表、两组接口，路径全部不变。
    //
    // ⚠ 存储介质变了：商家自己的对象存储 → 本机磁盘，由 file.<主域名> 直接对外
    //   （§10.3.8）。理由见 SiteFileService 的类注释。

    /** 13. 图片列表。路径不变 */
    @GetMapping("/file/image")
    @RequirePerm("site:file:list")
    public Map<String, Object> imageList(SiteImage query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(fileService.selectImageList(query, pageNum, pageSize));
    }

    /**
     * 14. 上传图片。路径不变。
     *
     * <p>width/height 由前端在浏览器里量好一并提交——选文件时本来就要加载一次，
     * 服务端再解码一遍纯属浪费。量不到就不传，存 null。</p>
     */
    @PostMapping("/file/image")
    @RequirePerm("site:file:upload")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "height", required = false) Integer height,
            @RequestParam(value = "folderId", required = false) String folderId,
            HttpServletRequest request)
    {
        return ok(fileService.uploadImage(file, width, height, folderId, operator(request)));
    }

    /** 15. 图片改名/改备注。路径不变。地址、尺寸是上传时定死的，不接受修改 */
    @PutMapping("/file/image")
    @RequirePerm("site:file:edit")
    public Map<String, Object> imageEdit(@RequestBody SiteImage image, HttpServletRequest request)
    {
        return ok(fileService.renameImage(image.getImageId(), image.getImageName(),
                image.getRemark(), operator(request)));
    }

    /** 16. 删图片。路径不变。只删库里的记录，磁盘上的文件留着——可能已经被页面引用了 */
    @DeleteMapping("/file/image/{imageId}")
    @RequirePerm("site:file:remove")
    public Map<String, Object> imageRemove(@PathVariable("imageId") String imageId)
    {
        return ok(fileService.removeImage(imageId));
    }

    /** 17. 视频列表。路径不变 */
    @GetMapping("/file/video")
    @RequirePerm("site:file:list")
    public Map<String, Object> videoList(SiteVideo query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(fileService.selectVideoList(query, pageNum, pageSize));
    }

    /** 18. 上传视频。路径不变。duration 是时长（秒），和宽高一样由前端量好提交 */
    @PostMapping("/file/video")
    @RequirePerm("site:file:upload")
    public Map<String, Object> uploadVideo(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "height", required = false) Integer height,
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam(value = "duration", required = false) Integer duration,
            HttpServletRequest request)
    {
        return ok(fileService.uploadVideo(file, width, height, duration, folderId, operator(request)));
    }

    /** 19. 视频改名/改备注。路径不变 */
    @PutMapping("/file/video")
    @RequirePerm("site:file:edit")
    public Map<String, Object> videoEdit(@RequestBody SiteVideo video, HttpServletRequest request)
    {
        return ok(fileService.renameVideo(video.getVideoId(), video.getVideoName(),
                video.getRemark(), operator(request)));
    }

    /** 20. 删视频。路径不变 */
    @DeleteMapping("/file/video/{videoId}")
    @RequirePerm("site:file:remove")
    public Map<String, Object> videoRemove(@PathVariable("videoId") String videoId)
    {
        return ok(fileService.removeVideo(videoId));
    }

    // ---- 3D 模型（2026-09-20 加）。四个端点和视频一一对应，权限复用 site:file:* ----

    /** 模型列表 */
    @GetMapping("/file/model")
    @RequirePerm("site:file:list")
    public Map<String, Object> modelList(SiteModel query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(fileService.selectModelList(query, pageNum, pageSize));
    }

    /** 上传模型。只收 .glb，没有宽高时长要量 */
    @PostMapping("/file/model")
    @RequirePerm("site:file:upload")
    public Map<String, Object> uploadModel(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) String folderId,
            HttpServletRequest request)
    {
        return ok(fileService.uploadModel(file, folderId, operator(request)));
    }

    /** 模型改名/改备注 */
    @PutMapping("/file/model")
    @RequirePerm("site:file:edit")
    public Map<String, Object> modelEdit(@RequestBody SiteModel model, HttpServletRequest request)
    {
        return ok(fileService.renameModel(model.getModelId(), model.getModelName(),
                model.getRemark(), operator(request)));
    }

    /** 删模型。只删记录不删文件，理由同图片 */
    @DeleteMapping("/file/model/{modelId}")
    @RequirePerm("site:file:remove")
    public Map<String, Object> modelRemove(@PathVariable("modelId") String modelId)
    {
        return ok(fileService.removeModel(modelId));
    }

    // ---- 用照片生成 3D（腾讯混元生 3D，2026-09-26）。设计见 docs/superpowers/specs/2026-09-26-hunyuan-3d-generate-design.md ----

    /** 提交一个生成任务。图片必须是本站文件库里的；每次消耗混元积分，每天有上限 */
    @PostMapping("/file/model/gen")
    @RequirePerm("site:file:upload")
    public Map<String, Object> modelGenSubmit(@RequestBody ModelGenService.Request body, HttpServletRequest request)
    {
        return ok(modelGenService.submit(body, operator(request)));
    }

    /** 任务列表。带 active（还有没跑完的）、今日已用次数和上限，前端据此决定要不要继续刷新 */
    @GetMapping("/file/model/gen")
    @RequirePerm("site:file:list")
    public Map<String, Object> modelGenList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return ok(modelGenService.list(pageNum, pageSize));
    }

    /** 删任务记录。已经生成进模型库的模型不受影响 */
    @DeleteMapping("/file/model/gen/{jobId}")
    @RequirePerm("site:file:remove")
    public Map<String, Object> modelGenRemove(@PathVariable("jobId") String jobId)
    {
        modelGenService.remove(jobId);
        return ok(null);
    }

    // ==================== 存储设置 ====================

    /**
     * 21. 查存储配置。路径不变。
     *
     * <p>一期恒为 {@code local}（文件落本机磁盘），这个接口基本没内容可回。
     * 保留它是因为 §10.3.6 要求 URL 不变，前端那个页面还在。</p>
     *
     * <p>{@code accessKeySecret} 永远不在返回体里（字段上挂着 {@code @JsonIgnore}），
     * 只有 {@code secretConfigured} 说明配过没有。</p>
     */
    @GetMapping("/file/storage")
    @RequirePerm("site:storage:query")
    public Map<String, Object> storageGet(
            @RequestParam(value = "provider", required = false) String provider)
    {
        Map<String, Object> result = ok(storageService.selectForDisplay(provider));
        result.put("configured", storageService.configuredProviders());
        return result;
    }


    // ==================== 文件夹 ====================
    //
    // 图片和视频是两棵独立的树，全靠 mediaType 参数分开——每个接口都要带上它。
    //
    // ⚠ 权限用的是 site:file:folder 这一个，不按增删改分三个：能传图的人本来
    //   就能建文件夹，再切细只是给运营多添一道配置，挡不住什么。

    /** 23. 文件夹树。顶层在最外面，children 已经拼好，每个节点带 fileCount */
    @GetMapping("/file/folder")
    @RequirePerm("site:file:list")
    public Map<String, Object> folderTree(
            @RequestParam(value = "mediaType", required = false) String mediaType)
    {
        return ok(folderService.tree(mediaType));
    }

    /** 24. 建文件夹。parentId 留空就是建在顶层 */
    @PostMapping("/file/folder")
    @RequirePerm("site:file:folder")
    public Map<String, Object> folderAdd(@RequestBody SiteFolder folder, HttpServletRequest request)
    {
        return ok(folderService.add(folder.getMediaType(), folder.getParentId(),
                folder.getFolderName(), operator(request)));
    }

    /**
     * 25. 改名或挪位置。
     *
     * <p>传了 folderName 就改名，传了 parentId 就挪——两件事可以在一次请求里都做，
     * 但校验是分开的（同级重名 vs 不能挪进自己的子孙里）。</p>
     */
    @PutMapping("/file/folder")
    @RequirePerm("site:file:folder")
    public Map<String, Object> folderEdit(@RequestBody SiteFolder folder, HttpServletRequest request)
    {
        String operator = operator(request);
        if (folder.getFolderName() != null && !folder.getFolderName().trim().isEmpty())
        {
            folderService.rename(folder.getFolderId(), folder.getFolderName(), operator);
        }
        // ⚠ 判 null 不判空串：空串是「挪到顶层」这个有意义的值，不是「没传」
        if (folder.getParentId() != null)
        {
            folderService.move(folder.getFolderId(), folder.getParentId(), operator);
        }
        return ok(folder.getFolderId());
    }

    /**
     * 26. 删文件夹。
     *
     * <p>⚠ 里面的文件回落到根，子文件夹提升一级，<b>都不删</b>。
     * 图片可能正被商品页或博客引用着——见 SiteFolderService 的类注释。</p>
     */
    @DeleteMapping("/file/folder/{folderId}")
    @RequirePerm("site:file:folder")
    public Map<String, Object> folderRemove(@PathVariable("folderId") String folderId,
            HttpServletRequest request)
    {
        folderService.remove(folderId, operator(request));
        return ok(folderId);
    }

    /** 27. 把一批文件挪进某个文件夹。folderId 留空 = 挪回根（未分类） */
    @PostMapping("/file/folder/move")
    @RequirePerm("site:file:folder")
    public Map<String, Object> folderMoveFiles(@RequestBody Map<String, Object> body)
    {
        Object ids = body.get("fileIds");
        List<String> fileIds = new ArrayList<>();
        if (ids instanceof List)
        {
            for (Object one : (List<?>) ids)
            {
                if (one != null)
                {
                    fileIds.add(String.valueOf(one));
                }
            }
        }
        int moved = folderService.moveFiles(String.valueOf(body.get("mediaType")), fileIds,
                body.get("folderId") == null ? null : String.valueOf(body.get("folderId")));
        Map<String, Object> result = ok(moved);
        return result;
    }

    /**
     * 22. 保存存储配置。路径不变。
     *
     * <p>⚠ 请求体里有明文密钥。这个 Controller 不打请求体日志，
     * <b>往后给它加任何「打一下 body 方便排查」的日志之前，先想起这一条</b>——
     * 那等于把密钥明文落进日志文件。</p>
     *
     * <h3>先自检再落库，顺序不能反</h3>
     * <p>{@code verifyStorage} 会真的往桶里放一个探针对象、再按访客的方式取回来，
     * 不通就抛异常，这一行配置根本不会写进 {@code u_s_storage}。反过来写的话，
     * 一份取不到东西的配置会先生效——而它的症状不是「保存失败」，是「以后每张图都是
     * 死链」，要等到有人打开页面才发现，那时候得改历史数据。慢两三秒换这个，值。</p>
     */
    @PostMapping("/file/storage")
    @RequirePerm("site:storage:edit")
    public Map<String, Object> storageSave(@RequestBody SiteStorage storage, HttpServletRequest request)
    {
        String previous = storageService.currentProvider();
        fileService.verifyStorage(storage);

        boolean changed = previous != null && !previous.equalsIgnoreCase(storage.getProvider());

        // ⚠ 必须在 save 之前数。save 之后「当前生效的是哪家」就变了，
        //   而这两个数要回答的是「原来那家有多少个文件，即将从列表里消失」
        int hiddenImages = changed ? fileService.countImagesOf(previous) : 0;
        int hiddenVideos = changed ? fileService.countVideosOf(previous) : 0;
        int hiddenModels = changed ? fileService.countModelsOf(previous) : 0;

        storageService.save(storage, operator(request));

        Map<String, Object> result = ok(null);
        result.put("providerChanged", changed);
        if (changed)
        {
            result.put("previousProvider", previous);
            // 前端那个「存储已切换」的弹框拿这两个数说话。
            //
            // ⚠ 它们一直是前端在读、后端没给（`result.hiddenImages || 0` 兜住了，
            //   所以不报错，只是那句「原来存在 X 上的 N 张图片」永远说不出数目）。
            //   而且在按 provider 过滤补上之前，那句话本身也不成立——老文件照样显示。
            //   两处一起修才有意义：过滤让它变成真的，这两个数让它说得出来。
            result.put("hiddenImages", hiddenImages);
            result.put("hiddenVideos", hiddenVideos);
            result.put("hiddenModels", hiddenModels);
        }
        return result;
    }

    // ==================== 询盘跟进 / 营销管理（新增） ====================
    //
    // §11 里「营销管理」只有一个名字。落到这个站上，营销最值钱的一块不是优惠券
    // 也不是群发邮件——**是「询盘来了没人回」**。这是个 B2B 询盘站：前台不收款、
    // 价格 0 表示面议、成交靠人跟出来的报价单。漏一条询盘 = 漏一单，而且是静悄悄地漏。
    //
    // ⚠ 这一组**不加新权限**，复用 site:inquiry:list / site:inquiry:edit。
    //   跟进记录就是询盘数据；单开一套 site:followup:* 会造出「能看跟进看板、
    //   却点不开背后的询盘」这种没用的中间状态。operator 本来就有这两个，开箱即用。

    /**
     * 50. 跟进看板 —— 「营销管理」那一页的全部数据。
     *
     * <p>回四个数字 + 一份清单，两者<b>同一套口径</b>（mapper 里共用一段 SQL）。
     * 不一致的话会出现「顶上说 3 条逾期、点进去列表是 5 条」，
     * 而那种不一致一旦出现，人就不再信这一页——一个不被信的待办列表等于没有。</p>
     *
     * @param bucket overdue（默认）/ today / unowned / all
     * @param owner  只看某个负责人的；为空看全部
     */
    @GetMapping("/followup/board")
    @RequirePerm("site:inquiry:list")
    public Map<String, Object> followupBoard(
            @RequestParam(value = "bucket", required = false) String bucket,
            @RequestParam(value = "owner", required = false) String owner)
    {
        return ok(followupService.board(bucket, owner));
    }

    /** 51. 一条询盘的跟进历史，最新的在最前面 */
    @GetMapping("/inquiry/{id}/followup")
    @RequirePerm("site:inquiry:query")
    public Map<String, Object> followupList(@PathVariable("id") Long id)
    {
        return ok(followupService.listByInquiry(id));
    }

    /**
     * 52. 记一条跟进。
     *
     * <p>请求体 {@code { action, content }}。⚠ 顺带把「待处理」推成「已联系」——
     * 有人写了第一条跟进，它就不再是待处理了。已成交 / 无效 / 已联系都不动，
     * 那些是人明确设过的结论。</p>
     */
    @PostMapping("/inquiry/{id}/followup")
    @RequirePerm("site:inquiry:edit")
    public Map<String, Object> followupAdd(@PathVariable("id") Long id,
            @RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        return ok(followupService.addForInquiry(id, action, content, operator(request)), "已记录");
    }

    // ==================== 博客（新增） ====================
    //
    // §11 原来把它列在「一期不做、只留位置」里。做出来的理由：这个站的 SEO 价值
    // 目前全压在商品详情页上，而商品页能覆盖的关键词就是商品名，覆盖不到
    // 「how to choose silicone tubing」这类真正带来询盘的长尾词。
    //
    // 五个权限，和商品那一套对齐，**全给 operator**：写文章就是运营的日常活。
    // ⚠ 和站长工具的分工不一样，不是不一致：那边有「整站禁收录」这种一开就影响
    //   全站的开关，所以归站长；文章改错了只影响那一篇，而且看得见、改得回来。

    /** 40. 文章列表。列表上显示站点默认语言那一条文案，没填就是空标题——那正是要让人看见的 */
    @GetMapping("/post")
    @RequirePerm("site:post:list")
    public Map<String, Object> postList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "publishStatus", required = false) String publishStatus,
            @RequestParam(value = "category", required = false) String category)
    {
        return ok(postService.adminList(keyword, publishStatus, category));
    }

    /** 41. 文章详情：档案 + 全部语言文案 + 站点开着的语言（编辑弹窗按它排语言页签） */
    @GetMapping("/post/{postCode}")
    @RequirePerm("site:post:query")
    public Map<String, Object> postDetail(@PathVariable("postCode") String postCode)
    {
        return ok(postService.adminDetail(postCode));
    }

    /**
     * 42. 新建文章。只建骨架（编码 + slug），文案由随后的保存写入。
     *
     * <p>⚠ 一律是草稿，不接受前端传状态：一篇还没有任何正文的文章
     * 不该有机会直接出现在公网上。</p>
     */
    @PostMapping("/post")
    @RequirePerm("site:post:add")
    public Map<String, Object> postCreate(@RequestBody SitePost post, HttpServletRequest request)
    {
        String code = postService.createPost(post, operator(request));
        return ok(code, "已创建");
    }

    /**
     * 43. 保存文章档案和各语言文案。
     *
     * <p>请求体：{@code { post: {...}, langs: [{lang, title, summary, content, keywords}, ...] }}</p>
     *
     * <p>⚠ 正文在服务层过 {@code RichTextCleaner} 白名单——前台是 v-html 渲染的，
     * 和商品详情同一条规矩。</p>
     */
    @PutMapping("/post")
    @RequirePerm("site:post:edit")
    public Map<String, Object> postSave(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        SitePost post = JSON.convertValue(body.get("post"), SitePost.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> langs = (List<Map<String, Object>>) body.get("langs");
        postService.savePost(post, langs, operator(request));
        return ok(null, "已保存");
    }

    /** 44. 批量改状态（发布 / 下架 / 转回草稿） */
    @PutMapping("/post/status")
    @RequirePerm("site:post:edit")
    public Map<String, Object> postStatus(@RequestBody Map<String, Object> body, HttpServletRequest request)
    {
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) body.get("postCodes");
        String status = body.get("publishStatus") == null ? null : String.valueOf(body.get("publishStatus"));
        int rows = postService.updateStatus(
                codes == null ? new String[0] : codes.toArray(new String[0]),
                status, operator(request));
        return ok(rows, "已更新 " + rows + " 篇");
    }

    /** 45. 删文章。档案和文案一起删 */
    @DeleteMapping("/post/{postCodes}")
    @RequirePerm("site:post:remove")
    public Map<String, Object> postRemove(@PathVariable("postCodes") String[] postCodes,
            HttpServletRequest request)
    {
        int rows = postService.deletePosts(postCodes, operator(request));
        return ok(rows, "已删除 " + rows + " 篇");
    }

    // ==================== 站长工具（新增） ====================
    //
    // docs/独立站与ERP的边界.md §11 把它列在「一期不做、只留位置」里。
    // 这一轮做出来了，理由是它的需求已经不只有名字了：前台的 SEO 基建
    // （useSeo.ts 的 canonical + hreflang、sitemap.xml.ts、robots.txt.ts）
    // 已经把规则定完了，缺的只是「让站长能改」和「验证码 / 统计代码的注入位」。
    //
    // ⚠ 权限只有两个，不是三个：
    //     site:seo:query   看体检、看配置   → operator + admin
    //     site:seo:edit    改配置           → 只有 admin
    //   体检给运营看是因为它就是运营的日常活（哪个商品缺日语标题）；
    //   改配置里有 noindexall 那种一开就静默毁掉自然流量的开关，归站长。
    //
    // 不加第三个 site:seo:script——统计代码只存 ID，和验证码不再是两个风险等级。

    /**
     * 30. 查站长工具配置。
     *
     * <p>⚠ <b>永远返回一份配置，不会 404 也不会 500。</b>老库没跑
     * {@code 2026-09-08-seo-tools.sql} 时回的是 {@link SiteSeo#defaults()}，
     * 而那份默认值等于这张表不存在时前台的原有行为——所以「没跑迁移」和
     * 「跑了但没配」在界面上表现一致，不多出一种要解释的状态。</p>
     */
    @GetMapping("/seo")
    @RequirePerm("site:seo:query")
    public Map<String, Object> seoGet()
    {
        return ok(siteSeoService.current());
    }

    /**
     * 31. 保存站长工具配置。
     *
     * <p>⚠ 整份送，<b>没有一个字段走 coalesce</b>（见 SiteSeoMapper.xml 里那段注释）：
     * 每一项都会原样回显到界面上，所以「传上来什么就是什么」是对的，
     * 清空 GA4 测量 ID 就该真的清掉。</p>
     *
     * <p>⚠ 这里的校验<b>不是</b>「只存 ID」那条安全前提的依靠。库里的值可能是别人用
     * SQL 直接改的（部署时手工补配置是常态），所以 {@code seo-head.ts} 在拼进 HTML
     * 之前还会再校验一次。两处都要有，缺了输出端那一次，整条防线就是纸的。</p>
     */
    @PutMapping("/seo")
    @RequirePerm("site:seo:edit")
    public Map<String, Object> seoSave(@RequestBody SiteSeo seo, HttpServletRequest request)
    {
        siteSeoService.save(seo, operator(request));
        // 回最新的那份：normalized() 可能把越界值夹回去了，界面上要看到真正生效的值，
        // 而不是自己刚才填的那个
        return ok(siteSeoService.current());
    }

    /**
     * 32. SEO 体检。
     *
     * <p><b>现算，不落库。</b>落库就会出现「已经改好了但体检表还在报警」——
     * 一份需要人手动刷新才准的检查清单，比没有清单更糟，因为它会被信。</p>
     *
     * <p>⚠ 返回体里有一个 {@code notChecked} 数组，界面上<b>必须显示</b>它。
     * 一份体检报告最危险的地方不是漏报，是让人以为「没报就是没问题」——
     * canonical 指向的域名这个后端确实查不到（那是 station-site 那个 Node 进程的
     * 环境变量），所以要明说，并给出自己验的命令。</p>
     *
     * <p>只要 {@code site:seo:query}：这是只读的，而且运营正是要看它的人。</p>
     */
    @GetMapping("/seo/audit")
    @RequirePerm("site:seo:query")
    public Map<String, Object> seoAudit()
    {
        return ok(seoAuditService.audit());
    }

    // ==================== 用户管理（新增） ====================
    //
    // 本站自己的用户体系（boundary §6）。ERP 那边没有对应物——
    // 那时候管理台没有用户表，身份全来自 ERP 发的票。
    //
    // ⚠ 这里只能「改」和「删」，建不了：用户是谁第一次从 ERP 跳进来就自动落一行的
    // （SiteUserService.createFromErp）。站长在这一组接口里做的是调角色、停用、删人。
    //
    // ⚠ 2026-09-24 起这一组**不认角色权限**，只认会话上的 userAdmin 标记（requireUserAdmin）：
    //   用户管理从管理台菜单里拿掉了——那一页全是 ERP 登录名、「ERP 免密」来源，不该给海外客户看——
    //   唯一入口是 ERP「独立站管理」的「站点用户」按钮（令牌带 scope=users）。
    //   @RequirePerm 留空只表示「要登录」；站长角色、CONSOLE_GRANT_ALL_PERMS 的 * 都开不了这一组。
    //   u_s_role 里那几个 site:user:* 权限串因此不再起作用。

    /** 用户接口的闸：不是从「站点用户」进来的会话一律 403 */
    private static void requireUserAdmin(HttpServletRequest request)
    {
        if (!ConsoleSession.require(request).isUserAdmin())
        {
            // 和 AuthInterceptor 的 403 同一句话，管理台前端那层（i18n/server.js）认得它
            throw new ServiceException("没有该操作的权限，需要联系站长授权", 403);
        }
    }

    /** 23. 用户列表 */
    @GetMapping("/user")
    @RequirePerm
    public Map<String, Object> userList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request)
    {
        requireUserAdmin(request);
        List<SiteUser> rows = userService.selectList(keyword, status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("rows", rows);
        return okTable(data);
    }

    /** 24. 有哪些角色。前端那个下拉用它 */
    @GetMapping("/user/roles")
    @RequirePerm
    public Map<String, Object> roles(HttpServletRequest request)
    {
        requireUserAdmin(request);
        return ok(userService.roles());
    }

    // ⚠ 25（POST /user，新建用户）和 27（PUT /user/{id}/password，重置密码）删了。
    //
    // 本站没有登录页了，唯一入口是从 ERP 点「进入管理」（SsoController）。于是：
    //   · 手工建的账号谁都用不了——免密进入是按 erpuser 找人的，匹配不上那一行；
    //   · 「重置密码」当时的作用是给一个从 ERP 进来的人开本地登录，
    //     那正是 docs/独立站与ERP的边界.md §6d 要挡的「在闸旁边开门」。
    //
    // 剩下的 23/24/26/28 号不重排：改号会让历史提交、日志和排查记录里的引用全部对不上。

    /** 26. 改用户（昵称、角色、状态）。登录名不允许改，理由见 SiteUserMapper.xml */
    @PutMapping("/user")
    @RequirePerm
    public Map<String, Object> userEdit(@RequestBody SiteUser user, HttpServletRequest request)
    {
        requireUserAdmin(request);
        return ok(userService.update(user, operator(request)));
    }

    /** 28. 删用户。最后一个站长删不掉——删了就没人能再授权了 */
    @DeleteMapping("/user/{id}")
    @RequirePerm
    public Map<String, Object> userRemove(@PathVariable("id") Long id, HttpServletRequest request)
    {
        requireUserAdmin(request);
        return ok(userService.delete(id));
    }

    // ==================== 统计 ====================

    /**
     * 29. 前台浏览量。概览页那个卡片一次把要的全拿走，不发五个请求。
     *
     * <p>回的东西：今天/昨天的 PV·UV、最近 14 天趋势、<b>商品排行</b>、
     * 页面类型分布、来源分布。</p>
     *
     * <h3>商品排行是这个接口存在的理由</h3>
     * <p>它把浏览量和询盘数、成单数、标价放在同一行里——接 GA4 之类的第三方统计
     * 做不到这件事：那边的数据在别人的服务器上，没法和这个库里的
     * {@code u_s_inquiry} / {@code u_s_order} join。</p>
     *
     * <p>回答的是「这个商品被看了 200 次却一个询盘都没有，是不是价格有问题」。
     * 而「多少人来、从哪个国家来、搜什么词进来的」那些问题，
     * 第三方统计答得比自己写好得多——两边各管一头，见
     * {@link PageViewStatService} 的类注释。</p>
     *
     * <p>⚠ 统计被关掉时这个接口<b>照样回 200</b>，data 里 {@code enabled=false}
     * 且各列表为空。回错误的话前端那个卡片会显示成「加载失败」，
     * 而它其实是被有意关掉的——这两种状态必须能分开。</p>
     */
    @GetMapping("/stat/pv")
    @RequirePerm("site:stat:query")
    public Map<String, Object> pageViewStat()
    {
        return ok(pageViewStatService.overview());
    }

    // ==================== 内部 ====================

    /** 操作人。只从服务端会话取，前端传什么都不看 */
    private String operator(HttpServletRequest request)
    {
        return ConsoleSession.require(request).getUserName();
    }

    /**
     * 响应外壳。
     *
     * <p>{@code {code, msg, data}}，和 ERP 的 {@code AjaxResult} 一致——
     * 前端的 request.js 认的就是这个形状，换了它整个 UI 都要改。</p>
     */
    private Map<String, Object> ok(Object data)
    {
        return ok(data, "操作成功");
    }

    private Map<String, Object> ok(Object data, String msg)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    /**
     * 列表的响应外壳。
     *
     * <p>{@code {code, msg, total, rows}}——和 ERP 的 {@code TableDataInfo} 一致，
     * <b>total 和 rows 在顶层，不在 data 里</b>。这是 RuoYi 前端表格组件的约定，
     * 塞进 data 里的话表格会显示空。</p>
     */
    private Map<String, Object> okTable(Map<String, Object> page)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "查询成功");
        result.put("total", page.get("total"));
        result.put("rows", page.get("rows"));
        return result;
    }
}
