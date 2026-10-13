package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteBuyerAddress;
import com.ruoyi.station.console.service.BuyerAuthService;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteBuyerAddressService;
import com.ruoyi.station.console.service.SiteBuyerService;
import com.ruoyi.station.console.service.SiteOrderService;
import com.ruoyi.station.console.session.BuyerHandoff;
import com.ruoyi.station.console.session.BuyerSession;

/**
 * 前台买家的账号接口。挂在 {@code /openapi/site/account/**} 下。
 *
 * <h3>⚠ 三组接口，三道门，别搞混</h3>
 * <table border="1">
 *   <tr><th>路径</th><th>谁访问</th><th>门</th></tr>
 *   <tr><td>{@code /openapi/site/**}</td><td>匿名访客</td><td><b>没有门</b>，公开只读 + 两个写（询盘、下单）</td></tr>
 *   <tr><td>{@code /openapi/site/account/**}</td><td>买家</td>
 *       <td>注册/登录那几个是公开的；{@code /me}、{@code /profile}、
 *           {@code /addresses}、{@code /orders} 要买家会话
 *           （{@link BuyerSession}）</td></tr>
 *   <tr><td>{@code /api/**}</td><td>管理台操作员</td>
 *       <td>{@code AuthInterceptor}：一律要会话 + 要权限，一个例外都没有</td></tr>
 * </table>
 *
 * <p><b>为什么买家接口放在 {@code /openapi/site/} 下面而不是 {@code /api/} 下面</b>：
 * {@code AuthInterceptor} 拦的是整个 {@code /api/**}，规则是「要管理台会话」。
 * 把买家接口放进去就得给它开例外，而例外列表是会被忘记维护的东西——
 * 下一个人加接口时不会去看它。放在外面，那条规则就没有例外。</p>
 *
 * <p>代价是这一组的鉴权得<b>自己在每个方法里写</b>（{@link #requireBuyer}）。
 * 所以下面每一个要身份的方法，第一行都是它。<b>加新接口时别忘了。</b></p>
 *
 * <h3>为什么不给买家做一个拦截器</h3>
 * <p>想过。但这一组里公开和要登录的接口是<b>混在一起</b>的（注册公开、我的订单要登录），
 * 拦截器就得配一张路径例外表——那正是上面说的「会被忘记维护的东西」。
 * 方法里显式写一行，漏了就是 500 而不是「谁都能看别人的订单」。</p>
 */
@RestController
@RequestMapping("/openapi/site/account")
public class BuyerAccountController
{
    @Autowired
    private BuyerAuthService authService;

    @Autowired
    private BuyerSession buyerSession;

    @Autowired
    private SiteBuyerService buyerService;

    @Autowired
    private SiteOrderService orderService;

    @Autowired
    private SiteBuyerAddressService addressService;

    @Autowired
    private BuyerHandoff buyerHandoff;

    // ==================== 公开的（还没有身份） ====================

    /**
     * 注册。请求体 {@code {email, password, name, company, phone}}。
     *
     * <p><b>无论这个邮箱是新的、还是已经有账号了，回的都是同一句话。</b>
     * 理由见 {@code BuyerAuthService} 类注释第 1 条：区分开的话，这个公开接口
     * 就是一个「查这个邮箱是不是这家的客户」的探测器。</p>
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body,
            HttpServletRequest request)
    {
        Map<String, String> in = body == null ? Map.of() : body;
        authService.register(in.get("email"), in.get("password"), in.get("name"),
                in.get("company"), in.get("phone"), SsoController.clientIp(request), in.get("lang"));
        return ok(null, "验证邮件已发出，请查收后点击链接激活账号");
    }

    /** 点验证链接。请求体 {@code {token}} */
    @PostMapping("/verify-email")
    public Map<String, Object> verifyEmail(@RequestBody Map<String, String> body)
    {
        SiteBuyer buyer = authService.verifyEmail(body == null ? null : body.get("token"));
        // ⚠ 验证通过不顺手建会话：那意味着任何拿到链接的人（转发的邮件、
        //   邮件客户端的链接预取）都直接登进去了。让他自己登一次
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", buyer.getEmail());
        return ok(data, "邮箱已验证，现在可以登录了");
    }

    /** 再发一次验证信。请求体 {@code {email}}。同样不透露邮箱状态 */
    @PostMapping("/resend-verify")
    public Map<String, Object> resendVerify(@RequestBody Map<String, String> body)
    {
        authService.resendVerify(body == null ? null : body.get("email"),
                body == null ? null : body.get("lang"));
        return ok(null, "如果这个邮箱有待验证的账号，验证邮件已经再发一次");
    }

    /**
     * 登录。请求体 {@code {email, password}}。成功后种买家会话 Cookie。
     *
     * <p>失败的四种情况分两类：邮箱/密码不对 → 同一句「邮箱或密码不正确」；
     * 停用、没验证 → 说清楚（那两种都要求密码先对上，说话对象已经证明了自己是账号主人）。</p>
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response)
    {
        Map<String, String> in = body == null ? Map.of() : body;
        SiteBuyer buyer = authService.login(in.get("email"), in.get("password"),
                SsoController.clientIp(request), in.get("lang"));
        buyerSession.start(request, response, buyer.getBuyerCode());
        return ok(profile(buyer), "登录成功");
    }

    /** 忘记密码：发重置信。请求体 {@code {email}}。⚠ 邮箱不存在也回成功 */
    @PostMapping("/password/forgot")
    public Map<String, Object> forgot(@RequestBody Map<String, String> body,
            HttpServletRequest request)
    {
        authService.requestReset(body == null ? null : body.get("email"),
                SsoController.clientIp(request), body == null ? null : body.get("lang"));
        return ok(null, "如果这个邮箱有账号，重置邮件已经发出，请查收");
    }

    /** 用重置令牌设新密码。请求体 {@code {token, password}} */
    @PostMapping("/password/reset")
    public Map<String, Object> reset(@RequestBody Map<String, String> body)
    {
        Map<String, String> in = body == null ? Map.of() : body;
        authService.resetPassword(in.get("token"), in.get("password"));
        // 不顺手登录，理由同 verify-email
        return ok(null, "密码已重置，请用新密码登录");
    }

    // ==================== 要买家会话的 ====================

    /**
     * 我是谁。前台顶栏拿它决定显示「登录」还是「我的账户」。
     *
     * <p>⚠ 没登录时回 {@code 401} 而不是 200+空——前台那边靠状态码区分
     * 「没登录」和「登录了但没数据」。回 200 的话，一次网络故障看起来就像退出登录。</p>
     */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        return ok(profile(buyer), null);
    }

    /** 退出：销毁会话 + 清 Cookie。没登录也回成功——退出这件事是幂等的 */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response)
    {
        buyerSession.end(request, response);
        return ok(null, "已退出");
    }

    /** 改密码。请求体 {@code {oldPassword, newPassword}}。改完会话作废，要重新登录 */
    @PostMapping("/password/change")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response)
    {
        SiteBuyer buyer = requireBuyer(request);
        Map<String, String> in = body == null ? Map.of() : body;
        authService.changePassword(buyer.getBuyerCode(), in.get("oldPassword"), in.get("newPassword"));
        // 改完把会话作废，逼一次重新登录：如果这次改密码是因为怀疑密码泄露了，
        // 留着旧会话等于让攻击者继续待在里面
        buyerSession.end(request, response);
        return ok(null, "密码已修改，请重新登录");
    }

    /**
     * 改自己的联系方式。请求体 {@code {name, company, phone, country}}。
     *
     * <p>⚠ 改不了邮箱：那是他的身份（唯一键、登录名、找回密码的去处）。
     * 换邮箱等于换一个账号，要走一遍验证流程，不是这个接口的事。</p>
     *
     * <p>⚠ 也改不了 {@code tag} / {@code remark}：那两列是运营的内部标签和备注
     * （「重点客户」「压价狠」这类话）。买家能写进去就等于他能看到那个字段的存在。
     * 挡在这里而不是靠前端不传——{@code SiteBuyerService.updateProfile} 那边只 set 四列。</p>
     */
    @PostMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        Map<String, String> in = body == null ? Map.of() : body;
        SiteBuyer updated = buyerService.updateProfile(buyer.getBuyerCode(), in.get("name"),
                in.get("company"), in.get("phone"), in.get("country"));
        return ok(profile(updated), "资料已保存");
    }

    /**
     * 设头像。请求体 {@code {avatar}}，三种合法取值：{@code null}（清掉）、
     * {@code preset:<六个色块之一>}、浏览器裁好缩到 256px 的 JPEG/PNG data URL。
     *
     * <p>⚠ 单独一个接口，不并进 {@code /profile}：那张表单每次保存都会把
     * 姓名/公司/电话/国家一起发一遍，头像跟着走的话，改个电话号就要重传几十 KB 的图。
     * 而且色块是点一下就生效的，和「填完表单按保存」不是同一种交互。</p>
     *
     * <p>校验在 {@code SiteBuyerService.updateAvatar} 里，<b>别放宽成接受任意字符串</b>
     * ——那一列的值会被直接放进 {@code <img src>}，而且会显示在管理台的客户列表上。</p>
     */
    @PostMapping("/avatar")
    public Map<String, Object> updateAvatar(@RequestBody Map<String, String> body,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        buyerService.updateAvatar(buyer.getBuyerCode(), body == null ? null : body.get("avatar"));
        return ok(profile(buyer), "头像已更新");
    }

    // ==================== 收货地址簿 ====================

    /**
     * 我的收货地址。默认的排最前。
     *
     * <p>这一组四个接口<b>一个都不接受「谁的」参数</b>，{@code buyerCode} 全部从会话取。
     * 这张表对外用连号的自增 id，少了这一条，把 id 改个数字就能读到、改掉
     * 别人的收货地址和电话。</p>
     */
    @GetMapping("/addresses")
    public Map<String, Object> myAddresses(HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        return ok(addressService.list(buyer.getBuyerCode()), null);
    }

    /**
     * 新增或修改一条收货地址。
     *
     * <p>请求体就是一条地址（{@code label / contactName / phone / addrLine1 /
     * addrLine2 / city / region / postal / country}），多一个 {@code makeDefault}。
     * <b>带 {@code id} 就是改，不带就是新增</b>——两个动作一个接口，
     * 因为前端那张表单本来就是同一张。</p>
     *
     * <p>⚠ 改「不是他的那条」报的是 404 不是 403：403 等于告诉对方
     * 「这个 id 是真的，只是不属于你」，那是一台探测器。</p>
     */
    @PostMapping("/addresses")
    public Map<String, Object> saveAddress(@RequestBody SiteBuyerAddress body,
            @RequestParam(value = "makeDefault", required = false, defaultValue = "false")
                    boolean makeDefault,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        return ok(addressService.save(buyer.getBuyerCode(), body, makeDefault), "地址已保存");
    }

    /**
     * 设为默认收货地址。
     *
     * <p>⚠ 用 POST 而不是 PUT：{@code WebMvcConfig} 给 {@code /openapi/site/**} 放开的
     * 跨域方法只有 GET / POST / OPTIONS。生产上前台和后端同源（nginx 转发），
     * 但开发机上是跨域的，用 PUT 的话本机调不通、线上又是好的——那种差异最难查。</p>
     */
    @PostMapping("/addresses/{id}/default")
    public Map<String, Object> setDefaultAddress(@PathVariable("id") Long id,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        addressService.setDefault(buyer.getBuyerCode(), id);
        return ok(addressService.list(buyer.getBuyerCode()), "已设为默认");
    }

    /** 删一条收货地址。POST 不 DELETE，理由同上。删掉默认的会自动把剩下最新的一条顶上 */
    @PostMapping("/addresses/{id}/delete")
    public Map<String, Object> deleteAddress(@PathVariable("id") Long id,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        addressService.remove(buyer.getBuyerCode(), id);
        return ok(addressService.list(buyer.getBuyerCode()), "地址已删除");
    }

    /**
     * 我的订单。
     *
     * <p><b>这是买家登录真正的用处</b>：在此之前前台的「我的询价」列表只存在
     * 那台浏览器的 localStorage 里（{@code station-site/app/pages/orders/index.vue}
     * 的页头注释写着「换台电脑发现全没了」）。登录之后这份列表来自服务端。</p>
     *
     * <p>⚠ 只查<b>自己</b>的：{@code buyerCode} 从会话取，绝不从参数取。
     * 从参数取的话，把别人的客户编码填进去就能看别人的订单。</p>
     */
    @GetMapping("/orders")
    public Map<String, Object> myOrders(HttpServletRequest request,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size)
    {
        SiteBuyer buyer = requireBuyer(request);
        return ok(orderService.selectByBuyer(buyer.getBuyerCode(), page, size), null);
    }

    /**
     * 我的某一张订单（含订单行）。
     *
     * <p>⚠ 两层判：先要有会话，再确认这张单<b>属于</b>他。少了第二层，
     * 任何登录用户把单号一个个试出来就能看光全站的订单——而单号是有规律的流水号。</p>
     */
    @GetMapping("/orders/{orderCode}")
    public Map<String, Object> myOrder(@PathVariable("orderCode") String orderCode,
            @RequestParam(value = "lang", required = false) String lang,
            HttpServletRequest request)
    {
        SiteBuyer buyer = requireBuyer(request);
        Map<String, Object> detail = orderService.selectForBuyer(orderCode, buyer.getBuyerCode(), lang);
        if (detail == null)
        {
            // 「不存在」和「不是你的」回同一句话：区分开等于告诉他哪些单号是真的
            throw BuyerText.error("order.missing", 404);
        }
        return ok(detail, null);
    }

    // ==================== 跨域会话交接 ====================
    //
    // 前台拆成两个域：商城在主域，买家中心在 buyer. 子域。两边各自持有一份
    // host-only 的 BUYER_SESSION，装的是同一把钥匙（同一个 Redis 键），
    // 所以在任意一边退出，两边一起失效，不需要任何同步代码。
    //
    // 这两个接口负责把钥匙从主域交到 buyer. 手上。设计见
    // docs/设计稿/2026-09-08-独立站域名拆分.md。

    /**
     * 签一张交接票。<b>要会话。</b>
     *
     * <p>前端在主域登录成功之后调它，拿到票再把浏览器导到
     * {@code https://<买家域>/openapi/site/account/handoff?t=<票>&to=<路径>}。</p>
     *
     * <p>签不出票（Redis 抖了）回的是 {@code ticket: null} 而不是错误——
     * 登录本身已经成功了，交接失败不该让用户看见一个失败的登录。
     * 前端拿到 null 就跳过交接，买家下次进买家中心时再走一遍登录，
     * 那时候主域认得他，无感换回来。</p>
     */
    @PostMapping("/handoff-ticket")
    public Map<String, Object> handoffTicket(HttpServletRequest request)
    {
        requireBuyer(request);
        return ok(Map.of("ticket", String.valueOf(buyerHandoff.issue(buyerSession.currentToken(request)))), null);
    }

    /**
     * 用票换会话，然后 302 到目的地。<b>不要会话</b>——它就是来拿会话的。
     *
     * <p>这个接口是被<b>顶层导航</b>打的（浏览器地址栏跳过来），不是 XHR，
     * 所以回的是 302 而不是 JSON。</p>
     *
     * <h3>⚠ {@code to} 必须校验，否则是开放重定向</h3>
     * <p>不校验的话 {@code ?to=//evil.com} 会把访客从你的域名送到别人的站上，
     * 而且看起来是你把他送过去的——钓鱼最喜欢这种跳板。见 {@link #safePath}。</p>
     *
     * <h3>为什么失败也 302，不报错</h3>
     * <p>票过期、被用过、会话已经在另一边退掉了——这几种都不是异常，是正常的时序。
     * 一律送去目的地，让前端按「未登录」正常渲染（买家中心那几个页面本来就有
     * 未登录的处理）。给用户看一个 400 页面既没用又吓人。</p>
     */
    @GetMapping("/handoff")
    public void handoff(@RequestParam(value = "t", required = false) String ticket,
            @RequestParam(value = "to", required = false) String to,
            HttpServletResponse response) throws java.io.IOException
    {
        String token = buyerHandoff.redeem(ticket);
        if (token != null && buyerSession.isLive(token))
        {
            buyerSession.adopt(response, token);
        }
        response.sendRedirect(safePath(to));
    }

    /**
     * 把 {@code to} 收敛成一个本站的相对路径。拿不准就回首页。
     *
     * <p>只接受「{@code /} 开头，且第二个字符既不是 {@code /} 也不是 {@code \}」：</p>
     * <ul>
     *   <li>{@code //evil.com} —— 协议相对地址，浏览器会当成外站</li>
     *   <li>{@code /\evil.com} —— <b>部分浏览器把反斜杠等同于斜杠</b>，
     *       所以这个和上面那个是一回事。只挡 {@code //} 是不够的。</li>
     *   <li>{@code https://evil.com} —— 不以 {@code /} 开头，直接落到兜底</li>
     * </ul>
     *
     * <p>换行和回车也要挡：它们能在旧容器里把 {@code Location} 头拆成两个头
     * （HTTP 响应拆分）。</p>
     */
    private String safePath(String to)
    {
        if (to == null || to.length() < 2 || to.charAt(0) != '/')
        {
            return "/";
        }
        char second = to.charAt(1);
        if (second == '/' || second == '\\')
        {
            return "/";
        }
        if (to.indexOf('\n') >= 0 || to.indexOf('\r') >= 0)
        {
            return "/";
        }
        return to;
    }

    // ==================== 内部 ====================

    /**
     * 取当前买家，没登录就抛 401。
     *
     * <p><b>每一个要身份的方法第一行都调它。</b>这一组接口没有拦截器，
     * 理由见类注释——所以这一行就是那道门，漏了那个接口就是无鉴权的。</p>
     */
    private SiteBuyer requireBuyer(HttpServletRequest request)
    {
        String buyerCode = buyerSession.current(request);
        if (buyerCode == null)
        {
            throw BuyerText.error("auth.signInRequired", 401);
        }
        SiteBuyer buyer = buyerService.findByCode(buyerCode);
        if (buyer == null)
        {
            // 会话有效但档案没了（被运营删了）。当成未登录，不要 500
            throw BuyerText.error("auth.signInRequired", 401);
        }
        if (buyer.isDisabled())
        {
            throw BuyerText.error("auth.disabled", 403);
        }
        return buyer;
    }

    /**
     * 下发给前台的买家信息。
     *
     * <p>⚠ <b>手写字段，不要直接把 {@code SiteBuyer} 序列化出去。</b>那个对象上有
     * {@code tag} / {@code remark}（运营的内部标签和备注，写着「重点客户」「压价狠」
     * 这类话）、{@code lastLoginIp}、{@code shopCode}。买家看到自己的内部备注
     * 是一场事故。{@code passwordHash} 有 {@code @JsonIgnore} 挡着，但那只挡一个字段。</p>
     */
    private Map<String, Object> profile(SiteBuyer buyer)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", buyer.getEmail());
        data.put("name", buyer.getName());
        data.put("company", buyer.getCompany());
        data.put("phone", buyer.getPhone());
        data.put("country", buyer.getCountry());
        data.put("displayName", buyer.displayName());
        data.put("orderCount", buyer.getOrderCount());
        data.put("inquiryCount", buyer.getInquiryCount());
        // ⚠ 头像单独查一次。它不在 SiteBuyer 上，也不在 mapper 的列清单里——
        //   那是一串几十 KB 的 base64，挂在那个对象上就会跟着管理台的客户列表
        //   （一页 20 行）和 requireBuyer（每个请求一次）一起被读出来
        data.put("avatar", buyerService.avatarOf(buyer.getBuyerCode()));
        return data;
    }

    /** 响应外壳和本服务其余接口一致：{@code {code, msg, data}} */
    private Map<String, Object> ok(Object data, String msg)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", msg == null ? "操作成功" : msg);
        if (data != null)
        {
            result.put("data", data);
        }
        return result;
    }
}
