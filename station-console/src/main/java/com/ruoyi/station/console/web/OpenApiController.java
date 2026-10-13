package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.domain.SiteOrderItem;
import com.ruoyi.station.console.service.InquiryRateLimiter;
import com.ruoyi.station.console.service.OrderWizardService;
import com.ruoyi.station.console.service.OrderLookupRateLimiter;
import com.ruoyi.station.console.service.PageViewRateLimiter;
import com.ruoyi.station.console.service.PageViewService;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteOpenService;
import com.ruoyi.station.console.service.SiteOrderService;
import com.ruoyi.station.console.session.SiteVisitor;

/**
 * 前台开放接口。Nuxt 前台（{@code station-site}）从这里取数。
 *
 * <p>从 ERP 的 {@code StationOpenController} 移植过来（docs/独立站与ERP的边界.md §10.3.7），
 * <b>路径原样保留</b>：{@code /openapi/site/{info,products,products/{code},inquiry}}，
 * 前台只需要把 {@code NUXT_API_BASE} 从 ERP 的地址改成本机 {@code 127.0.0.1:8081}。</p>
 *
 * <h3>⚠ siteKey 参数没了</h3>
 * <p>ERP 那版每个接口都要带 {@code ?siteKey=xxx}：一个 ERP 服务 N 个站，
 * 得靠它区分是哪个站来取数。现在<b>一个部署一个站，前台取的是自己这台机器上的后端</b>，
 * 没有范围要限定——{@code u_s_site.sitekey} 那一列跟着删了（§7.2）。</p>
 *
 * <p>前台如果还在发 {@code siteKey}，这里收到就丢，不报错：升级时前后端不必同时上线。</p>
 *
 * <h3>这是唯一对公网开放的一组接口</h3>
 * <p>没有会话、没有权限——它就是给匿名访客用的（{@code AuthInterceptor} 只拦 {@code /api/**}）。
 * 三条硬规矩写在 {@link SiteOpenService} 的类注释里，改这个文件之前先读它。</p>
 */
@RestController
@RequestMapping("/openapi/site")
public class OpenApiController
{
    private static final Logger log = LoggerFactory.getLogger(OpenApiController.class);

    @Autowired
    private SiteOpenService openService;

    @Autowired
    private SiteOrderService orderService;

    @Autowired
    private OrderWizardService orderWizardService;

    /** 询盘的按 IP 限流。见 {@link InquiryRateLimiter} 的类注释——它是补回来的，不是新加的 */
    @Autowired
    private InquiryRateLimiter rateLimiter;

    /**
     * 浏览量上报的按 IP 限流。
     *
     * <p>⚠ <b>和上面那个是两个不同的限流器，别复用</b>。共用一个的话，
     * 访客随便翻几页就把当天的询盘额度用光了，之后他<b>提交不了询盘</b>——
     * 而那个故障从现象上完全看不出原因（表单说「提交过于频繁」，
     * 而他一条都没提交过）。值也差一个数量级：询盘 5 条/分钟，这里 60 条/分钟。</p>
     */
    /**
     * 匿名查单的按 IP / 按邮箱限流。
     *
     * <p>⚠ <b>又是一个独立的限流器，同样别复用上面那个。</b>和浏览量那条注释
     * 说的是同一类事故，但这一个的理由更硬：那两个限流器 Redis 挂了要放行，
     * 这一个 Redis 挂了必须<b>拒绝</b>——它挡的是撞单号读别人订单，不是灌水。
     * 详见 {@link OrderLookupRateLimiter} 的类注释。</p>
     */
    @Autowired
    private OrderLookupRateLimiter orderLookupRateLimiter;

    @Autowired
    private PageViewRateLimiter pageViewRateLimiter;

    @Autowired
    private PageViewService pageViewService;

    /** 访客标识的 Cookie。⚠ 只在埋点接口这条路上种，理由见 {@link SiteVisitor} 类注释最后一段 */
    @Autowired
    private SiteVisitor siteVisitor;

    /** 站点信息：公司名、导航、Banner、能力介绍、联系方式、商品分类 */
    @GetMapping("/info")
    public Map<String, Object> info(@RequestParam(value = "lang", required = false) String lang)
    {
        return ok(openService.siteInfo(lang));
    }

    /**
     * 商品列表。
     *
     * <p>{@code size} 由服务端夹到 1~60：不夹的话一个 {@code size=999999} 就能把整库拉走。</p>
     */
    @GetMapping("/products")
    public Map<String, Object> products(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size)
    {
        return ok(openService.products(lang, keyword, category, page, size));
    }

    /** 商品详情。不存在或没上架都回同一句「商品不存在」，不区分——区分开只会方便探测 */
    @GetMapping("/products/{goodsCode}")
    public Map<String, Object> productDetail(@PathVariable("goodsCode") String goodsCode,
            @RequestParam(value = "lang", required = false) String lang)
    {
        Map<String, Object> detail = openService.productDetail(lang, goodsCode);
        if (detail == null)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 404);
            result.put("msg", "商品不存在或已下架");
            return result;
        }
        return ok(detail);
    }

    /**
     * 引导式下单的向导（2026-09-28）。文字已按语言挑好，隐藏的、挂的商品下架了的选项已剔掉。
     * 没开回 404——前台据此显示「暂未开放」，不是报错页。
     */
    @GetMapping("/wizard")
    public Map<String, Object> wizard(@RequestParam(value = "lang", required = false) String lang)
    {
        Map<String, Object> view = orderWizardService.publicView(lang);
        if (view == null)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 404);
            result.put("msg", BuyerText.render("wizard.closed", SiteLang.current()));
            return result;
        }
        return ok(view);
    }

    // ==================== 博客 ====================
    //
    // ⚠⚠ 这三个和商品那两个在「缺译文」上**行为相反**：文章某语言没译文 =
    //   那个语言版本不存在（列表里没有、详情 404）。理由见 SiteOpenService
    //   博客那一节的注释。别照着商品那边加默认语言回退。

    /**
     * 博客文章列表。
     *
     * <p>{@code size} 同商品，由服务端夹到 1~60。返回体里带一份 {@code categories}
     * （现算的），前台的筛选按钮用它——省掉一次单独的请求。</p>
     */
    @GetMapping("/posts")
    public Map<String, Object> posts(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size)
    {
        return ok(openService.posts(lang, keyword, category, page, size));
    }

    /**
     * 给 sitemap 用：每一篇已发布文章 + 它有译文的语言。
     *
     * <h3>⚠ 为什么不复用 {@code /posts}</h3>
     * <p>那一条是<b>按语言过滤</b>的（它服务的是「/ja/blog 这一页显示什么」）。
     * 用它生成 sitemap 会漏掉「只翻译了日语、没有默认语言版本」的文章——
     * 那种文章确实存在（先写日语版是正常的工作方式），而漏进 sitemap 的表现是
     * 「文章在站上能打开、Google 却一直发现不了」，恰好是最难注意到的一种。</p>
     *
     * <p>⚠ 路径故意<b>不放在 {@code /posts} 底下</b>：那样会和
     * {@code /posts/{slug}} 争同一个位置，而 slug 是用户可填的——
     * 有人把 slug 取名叫 {@code sitemap} 就撞上了。</p>
     *
     * <p>回的字段只有 {@code slug} / {@code langs} / {@code publishTime} 三样，
     * 标题和正文一个都不带。</p>
     */
    @GetMapping("/post-sitemap")
    public Map<String, Object> postSitemap(
            @RequestParam(value = "limit", required = false, defaultValue = "5000") int limit)
    {
        return ok(openService.postSitemap(limit));
    }

    /**
     * 博客文章详情，按 <b>slug</b> 查（公开地址那一段），不是按内部编码。
     *
     * <p>「文章不存在」「还是草稿」「这个语言没有译文」三种情况回同一句话，
     * 不区分——同商品详情那一条的理由：区分开只会方便探测。</p>
     */
    @GetMapping("/posts/{slug}")
    public Map<String, Object> postDetail(@PathVariable("slug") String slug,
            @RequestParam(value = "lang", required = false) String lang)
    {
        Map<String, Object> detail = openService.postDetail(lang, slug);
        if (detail == null)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 404);
            result.put("msg", "文章不存在");
            return result;
        }
        return ok(detail);
    }

    /**
     * 提交询盘。<b>这是本服务唯一对公网开放的写操作</b>，且只能写进询盘表。
     *
     * <p>蜜罐字段（{@code website}）有值时静默丢弃并返回成功：让机器人以为提交成功了，
     * 它就不会换个花样再来。所以这两条路径返回的是同一句话。</p>
     *
     * <h3>限流和蜜罐给的反馈刻意不一样</h3>
     * <p>蜜罐命中 → 装作成功（不给机器人任何信号）。<br>
     * 限流命中 → <b>明确回 429</b>。</p>
     *
     * <p>看起来矛盾，其实针对的是两种人：踩蜜罐的一定是脚本，骗它；
     * 而被限流挡住的很可能是<b>真人</b>——共用一个出口 IP 的办公室（NAT）、
     * 或者一个客户连着问了几批货。对他装作成功是最坏的选择：他以为询盘发出去了，
     * 就再也不会联系了，而商家什么都没收到。所以要如实告诉他「过一会儿再试」，
     * 前台那边同时会把联系邮箱显示出来当退路。</p>
     */
    @PostMapping("/inquiry")
    public Map<String, Object> submitInquiry(@RequestBody SiteInquiry inquiry, HttpServletRequest request)
    {
        String clientIp = SsoController.clientIp(request);

        // ⚠ 必须在落库之前判。放到后面的话，被限流的那条已经写进库了，限流只剩一个假象。
        //   蜜罐的判断在 service 里、在这之后——顺序无所谓，两者互不影响
        if (!rateLimiter.tryAcquire(clientIp))
        {
            Map<String, Object> limited = new LinkedHashMap<>();
            limited.put("code", 429);
            limited.put("msg", BuyerText.render("order.tooFrequent", SiteLang.current()));
            return limited;
        }

        openService.submitInquiry(inquiry, clientIp, request.getHeader("User-Agent"));
        // ⚠ 不要把 submitInquiry 的返回值（false = 被当成机器人丢掉了）透出去。
        //   透出去等于告诉机器人「你的蜜罐露馅了」，它下次就会绕开
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "提交成功，我们会尽快与您联系");
        return result;
    }

    /**
     * 提交询价清单，落成一张站点订单。<b>本服务第二个对公网开放的写操作。</b>
     *
     * <h3>⚠ 请求体里只有商品编码和数量会被采用</h3>
     * <p>价格、商品名、图片、单位全部由服务端从 {@code u_s_sitegoods} 现查，小计也在服务端算
     * （见 {@link SiteOrderService#submit}）。前台那份清单在 localStorage 里，
     * 改一个数字就能把单价变成 0.01——信前端传的价格等于把报价单交给买家自己填。</p>
     *
     * <p>请求体形如：</p>
     * <pre>
     * { "contactName": "...", "email": "...", "country": "...", "city": "...",
     *   "shippingPref": "sea", "paymentPref": "tt", "buyerNote": "...",
     *   "items": [ { "goodsCode": "G001", "comboCode": "CB01", "qty": 500 } ],
     *   "website": ""   ← 蜜罐，和询盘表单同一个字段名
     * }
     * </pre>
     *
     * <h3>限流和蜜罐照询盘那一套</h3>
     * <p>用的是同一个 {@link InquiryRateLimiter}：对一个买家来说「问一批货」和
     * 「提一张清单」是同一件事，分两个额度等于把限额放宽一倍。</p>
     *
     * <p>蜜罐命中 → <b>装作成功但不落库</b>，返回一个假单号。这里比询盘那边多一层考虑：
     * 询盘只回一句话，而这个接口正常时要回单号给前台跳详情页。回真单号等于替机器人
     * 建了一张单；回错误则告诉它蜜罐露馅了。所以回一个格式对得上、但库里不存在的号。</p>
     */
    @PostMapping("/order")
    public Map<String, Object> submitOrder(@RequestBody SiteOrderSubmit body, HttpServletRequest request)
    {
        String clientIp = SsoController.clientIp(request);

        // ⚠ 和询盘一样：必须在落库之前判，否则被限流的那条已经写进去了
        if (!rateLimiter.tryAcquire(clientIp))
        {
            Map<String, Object> limited = new LinkedHashMap<>();
            limited.put("code", 429);
            limited.put("msg", BuyerText.render("order.tooFrequent", SiteLang.current()));
            return limited;
        }

        if (body != null && body.website() != null && !body.website().isBlank())
        {
            // 蜜罐命中。见方法注释：回一个假单号，既不落库也不露馅
            log.info("拦下一条机器人提交的订单，IP {}", clientIp);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderCode", "DD0000000000");
            return ok(data);
        }
        if (body == null)
        {
            throw BuyerText.error("order.empty", 400);
        }

        SiteOrder order = body.toOrder();
        List<SiteOrderItem> items = body.toItems();
        if (body.wizard() != null)
        {
            // 从引导式下单来的：订单行由后端按向导复核后自己算，请求体里的 items 一概不用。
            // 理由和「价格不信前端」同一条——答案和数量都得按 schema 重走一遍，见 OrderWizardService 类注释
            OrderWizardService.Resolved resolved = orderWizardService.resolve(body.wizard().answers(), body.lang());
            order.setWizardJson(resolved.snapshotJson());
            items = resolved.lines();
        }
        SiteOrder saved = orderService.submit(order, items, clientIp);

        // 只回单号和几个前台要显示的字段，不回整张单：里面有 clientip、内部备注这些
        // 买家不该看到的东西。前台拿到单号就够了——它自己有那份清单
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", saved.getOrderCode());
        data.put("status", saved.getStatus());
        data.put("currency", saved.getCurrency());
        data.put("subtotal", saved.getSubtotal());
        data.put("inquireOnly", saved.getInquireOnly());
        return ok(data);
    }

    /**
     * 匿名查单：凭<b>单号 + 下单邮箱</b>取回一张单。<b>本服务第三个对公网开放的写方法</b>
     * （语义是读，但用 POST——见下）。
     *
     * <h3>为什么是 POST 不是 GET</h3>
     * <p>参数里有买家的邮箱。GET 的话它会进 nginx 的 access log、进浏览器历史、
     * 进 Referer 头，然后在一堆地方留着。这是一次查询没错，但查询参数本身是客户资料。</p>
     *
     * <h3>⚠ 三种失败回同一句话</h3>
     * <p>单号不存在、邮箱对不上、单号格式不对——一律 404 + 同一句提示。
     * 分开说的话，「邮箱不对」就等于确认了「这个单号是真的」，
     * 而单号是 {@code DD} + 流水、可枚举，剩下的只是换邮箱接着试。</p>
     *
     * <h3>⚠ 限流是这个接口鉴权的一部分</h3>
     * <p>不是防灌水。撞号需要大量尝试，把尝试次数压下去，撞中的概率才低到没有意义。
     * 所以它在<b>查库之前</b>，而且 {@link OrderLookupRateLimiter} 在 Redis 不可用时
     * 返回 false（拒绝）——别照着询盘那个限流把它改成放行。</p>
     */
    @PostMapping("/order/lookup")
    public Map<String, Object> lookupOrder(@RequestBody OrderLookupRequest body,
            HttpServletRequest request)
    {
        String clientIp = SsoController.clientIp(request);

        String orderCode = body == null ? null : trimToNull(body.orderCode());
        // ⚠ 规范化之后再往下传：限流按邮箱算的那一层要的就是这个值，
        //   大小写不统一的话换个大小写就是另一份额度
        String email = body == null ? null : lowerTrimToNull(body.email());

        if (orderCode == null || email == null)
        {
            throw BuyerText.error("order.lookupFields", 400);
        }

        // ⚠ 必须在查库之前。放在后面的话，被限流的那次其实已经把单查出来了
        if (!orderLookupRateLimiter.tryAcquire(clientIp, email))
        {
            Map<String, Object> limited = new LinkedHashMap<>();
            limited.put("code", 429);
            limited.put("msg", BuyerText.render("order.lookupTooFrequent", SiteLang.current()));
            return limited;
        }

        Map<String, Object> found = orderService.selectForGuest(orderCode, email,
                body == null ? null : body.lang());
        if (found == null)
        {
            // ⚠ 「没这张单」和「邮箱对不上」在这里是同一件事，见方法注释
            log.info("匿名查单没对上，IP {}", clientIp);
            throw BuyerText.error("order.notFound", 404);
        }
        return ok(found);
    }

    /**
     * 查单请求体。
     *
     * <p>单独一个 record 而不是两个 {@code @RequestParam}：见上面「为什么是 POST」。</p>
     */
    public record OrderLookupRequest(String orderCode, String email, String lang)
    {
    }

    /**
     * 下单请求体。
     *
     * <h3>为什么单独一个 record，不直接收 SiteOrder</h3>
     * <p>{@code SiteOrder} 上有 {@code status} / {@code subtotal} / {@code orderCode} /
     * {@code buyerCode} / {@code handleUser} 这些<b>只该由服务端写</b>的字段。
     * 直接把它当入参，等于把这些字段暴露成可以从公网塞值的洞——
     * Service 里现在确实会覆盖它们，但那是一个「记得覆盖」的约定，
     * 而下一个人往 SiteOrder 上加字段时不会想起这里。</p>
     *
     * <p>用一个只有该收的字段的 record，多写几行换掉那个约定。</p>
     */
    public record SiteOrderSubmit(
            String contactName, String email, String company, String phone,
            String addrLine1, String addrLine2, String city, String region,
            String postal, String country,
            String shippingPref, String paymentPref, String buyerNote,
            List<SiteOrderLine> items,
            /** 蜜罐。和询盘表单同一个字段名，前台那个隐藏输入框可以直接复用 */
            String website,
            /** 买家下单时浏览的语言。空 = 站点默认语言。回执邮件和买家中心的商品名按它 */
            String lang,
            /** 引导式下单的答案。有它时 items 被忽略，订单行由后端按向导算（2026-09-28） */
            WizardSubmit wizard)
    {
        SiteOrder toOrder()
        {
            SiteOrder order = new SiteOrder();
            order.setContactName(contactName);
            order.setEmail(email);
            order.setCompany(company);
            order.setPhone(phone);
            order.setAddrLine1(addrLine1);
            order.setAddrLine2(addrLine2);
            order.setCity(city);
            order.setRegion(region);
            order.setPostal(postal);
            order.setCountry(country);
            order.setShippingPref(shippingPref);
            order.setPaymentPref(paymentPref);
            order.setBuyerNote(buyerNote);
            order.setLang(lang);
            return order;
        }

        List<SiteOrderItem> toItems()
        {
            if (items == null)
            {
                return List.of();
            }
            List<SiteOrderItem> result = new java.util.ArrayList<>();
            for (SiteOrderLine line : items)
            {
                if (line == null)
                {
                    continue;
                }
                SiteOrderItem item = new SiteOrderItem();
                item.setGoodsCode(line.goodsCode());
                item.setComboCode(line.comboCode());
                item.setQty(line.qty());
                result.add(item);
            }
            return result;
        }
    }

    /**
     * 清单里的一行。⚠ <b>只有这三个字段，价格由服务端查</b>——型号的价格也一样：
     * {@code comboCode} 只表达「他选了哪一款」，那一款多少钱由
     * {@code SiteOrderService.repriceLines} 现查。
     *
     * <p>{@code comboCode} 可以缺省，大多数商品没有型号可选。</p>
     */
    public record SiteOrderLine(String goodsCode, String comboCode, java.math.BigDecimal qty)
    {
    }

    /**
     * 引导式下单的那一块：{@code {"answers": {字段编码: 值}}}。值的形状见
     * docs/superpowers/specs/2026-09-28-order-wizard-design.md「答案格式」。
     *
     * <p>收成 {@link tools.jackson.databind.JsonNode}：不同字段的值是字符串、数组、数字、
     * 嵌套对象都有，逐项的形状检查在 {@link OrderWizardService#resolve} 里做。</p>
     */
    public record WizardSubmit(tools.jackson.databind.JsonNode answers)
    {
    }

    // ==================== 浏览量埋点 ====================

    /**
     * 浏览量上报。<b>本服务第三个对公网开放的写操作</b>，也是唯一一个不回业务数据的。
     *
     * <p>前台在每次路由变化后用 {@code navigator.sendBeacon} 发一条
     * （{@code station-site/app/plugins/track.client.ts}）。请求体：</p>
     * <pre>
     * { "path": "/ja/products/G001", "type": "detail",
     *   "goodsCode": "G001", "lang": "ja", "referrer": "https://www.google.com/" }
     * </pre>
     *
     * <h3>⚠ 为什么浏览量不在 {@link #productDetail} 里顺手记一笔</h3>
     * <p>因为前台是 SSR：首屏那次取数是 <b>Nuxt 服务端</b>发的，不是浏览器发的。
     * 在那里计数的话，每个访客的 IP 都会是 {@code 127.0.0.1}、Cookie 种在 Node 进程上、
     * UV 永远等于 PV——而 PV 那个数字看起来完全正常。
     * 完整的说明在 {@link PageViewService} 的类注释里。</p>
     *
     * <h3>⚠ 永远回 204，任何情况下</h3>
     * <p>被限流回 204、统计关掉了回 204、请求体是垃圾回 204。理由：</p>
     * <ul>
     *   <li>访客根本不知道有这回事，回错误只会在正常人的浏览器控制台里刷红字；</li>
     *   <li>{@code sendBeacon} <b>拿不到响应</b>——前端连状态码都读不到，
     *       回什么都是给日志看的；</li>
     *   <li>回 429 等于告诉刷量的人限额在哪。</li>
     * </ul>
     * <p>所以这个方法里没有任何一条错误路径。要看它有没有在工作，
     * 查库或者看 {@code PageViewRateLimiter} 的 WARN。</p>
     *
     * <h3>⚠ 限流在入队之前</h3>
     * <p>和询盘、下单同一条规矩。放到后面的话，被限流的那条已经进队列了。</p>
     */
    @PostMapping("/pv")
    public ResponseEntity<Void> reportPageView(@RequestBody(required = false) PageViewReport body,
            HttpServletRequest request, HttpServletResponse response)
    {
        // ⚠ 这里拿的是**完整** IP，给限流用。落库那一份由 PageViewService 截断
        String clientIp = SsoController.clientIp(request);
        // ⚠ pageViewRateLimiter，不是 rateLimiter——那个是询盘的额度，见它的字段注释
        if (body != null && pageViewRateLimiter.tryAcquire(clientIp))
        {
            // Cookie 必须在这条路上种：它是浏览器直接调的，Set-Cookie 才真的会落到
            // 浏览器里。见 SiteVisitor 类注释最后一段
            String visitorId = siteVisitor.ensureVisitorId(request, response);
            String sessionId = siteVisitor.ensureSessionId(request, response);
            pageViewService.report(body.path(), body.type(), body.goodsCode(),
                    body.lang(), body.referrer(),
                    clientIp, request.getHeader("User-Agent"), visitorId, sessionId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 上报的请求体。
     *
     * <p>⚠ 单独一个 record，不收 {@code SitePageView}——照 {@link SiteOrderSubmit}
     * 那条同样的规矩：那个 domain 上有 {@code visitorId} / {@code clientIp} /
     * {@code isBot} / {@code siteCode} 这些<b>只该由服务端写</b>的字段，
     * 直接当入参等于把它们暴露成可以从公网塞值的洞。</p>
     *
     * <p>五个字段全都是「参考值」：{@code type} 不在白名单里会被记成 {@code other}，
     * {@code goodsCode} 要在库里真的存在，路径会被去掉 query string 并强制以 / 开头。
     * 校验都在 {@link PageViewService} 里。</p>
     */
    public record PageViewReport(String path, String type, String goodsCode,
            String lang, String referrer)
    {
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 同上，外加转小写。邮箱作为键用之前一律走这个，理由见 {@link #lookupOrder} */
    private static String lowerTrimToNull(String value)
    {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private Map<String, Object> ok(Object data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        return result;
    }
}
