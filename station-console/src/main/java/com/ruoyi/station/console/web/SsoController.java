package com.ruoyi.station.console.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoyi.station.console.config.ConsoleProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteUser;
import com.ruoyi.station.console.security.JumpToken;
import com.ruoyi.station.console.security.JumpTokenVerifier;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteInfoService;
import com.ruoyi.station.console.service.SiteUserService;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * 从 ERP 免密进入。<b>这是进这个后台的唯一入口。</b>
 *
 * <p>⚠ 这句话有一段时间不成立：docs/独立站与ERP的边界.md §6a 曾经给管理台加过
 * 账号密码登录（{@code LoginController} + {@code /login} 那个表单），
 * 这里也跟着降级成「两个入口之一」。<b>那一版整个撤掉了</b>——理由见
 * {@link SessionController} 的类注释和 docs/独立站与ERP的边界.md §6d。</p>
 *
 * <p>所以 {@code POST /sso} 是本站唯一能建立会话的地方。直接打开 {@code /login}
 * 是一个空页面，没有任何可以输入的东西。</p>
 *
 * <h3>新协议长什么样</h3>
 * <pre>
 * [ERP 前端] 独立站管理，点「进入管理」
 *    │ ① POST /site/station/{siteCode}/console-jump     带 ERP 登录态
 *    ▼
 * [ERP 后端] 校验站点 → 用**这个站登记的跳转密钥**签一个令牌（HMAC-SHA256，60 秒）
 *            返回 { action: "https://console.domain.com/sso", token: "<payload>.<sig>" }
 *    │ ② ERP 前端造一个隐藏 form：method=POST、action=①返回的地址、target=_blank，提交
 *    │    ↑ 令牌在**请求体**里，不在 URL 上
 *    ▼
 * [这里] ③ 用自己那份密钥验签 → 查 exp → 查 jti 没用过（Redis，一次性）
 *        ↑ **不回调 ERP**。纯本地计算，ERP 那边不需要在线
 *       ④ 按 payload.user 匹配/落一个**本站自己的**操作用户，建本站会话
 *    ▼ ⑤ 302 到管理台首页
 * </pre>
 *
 * <h3>⚠ 为什么只接受 POST</h3>
 * <p>§4.2：自包含令牌只要进了 URL，就会进浏览器历史、进书签、进 nginx access log、
 * 进任何外链的 {@code Referer}——<b>而它本身就是凭据</b>，不像旧方案那样
 * 「捡到也得有 clientSecret 才换得动」。POST 表单让它只出现在请求体里，
 * 这是 SAML 的 HTTP-POST binding 用了二十年的办法。</p>
 *
 * <p>所以 {@code GET /sso} 一律回那个空页面，不接受任何形式的 {@code ?t=}。
 * <b>这一条是 §4.4 明列的「必须守住的两条」之一，不要为了调试方便加个 GET 分支。</b></p>
 */
@Controller
public class SsoController
{
    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    /**
     * 那个空页面上要显示哪句话。
     *
     * <p>用码不用文字：码是一个封闭集合，页面自己映射成中文，URL 上塞什么
     * 都只会落到「什么都不显示」那一支上。</p>
     *
     * <p><b>这是那一页唯一会显示内容的时候。</b>不带 reason 直接打开它是彻底空白——
     * 但跳转被拒时得有一句话，不然「账号被停用」表现成一个白屏，
     * 谁都不知道该找谁。</p>
     */
    private static final String REASON_NO_TOKEN = "no-token";
    private static final String REASON_REJECTED = "rejected";

    @Autowired
    private JumpTokenVerifier verifier;

    @Autowired
    private SiteUserService userService;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private ConsoleProperties consoleProperties;

    /**
     * 验签并建立会话。<b>只接受 POST。</b>
     *
     * @param token ERP 前端用隐藏表单提交过来的自包含令牌
     */
    @PostMapping("/sso")
    public String sso(@RequestParam(value = "token", required = false) String token,
            HttpServletRequest request)
    {
        if (token == null || token.isBlank())
        {
            return blankPage(REASON_NO_TOKEN, null);
        }

        JumpToken claims;
        try
        {
            claims = verifier.verify(token);
        }
        catch (ServiceException e)
        {
            // ⚠ 不把 e.getMessage() 带给用户。
            //
            // 那句话是给日志看的（「签名不匹配」「已过期」「用过了」），
            // 对用户来说处置方式是同一个：回 ERP 重新点一次。
            // 区分开只会方便有人拿一堆构造出来的令牌探测哪一步失败了。
            // 只说事实，不重复操作路径：那一页第二行固定写着
            // 「回到众联 ERP 的独立站管理页面，重新点【进入管理】」
            return blankPage(REASON_REJECTED, "令牌无效或已过期");
        }

        SiteUser user;
        try
        {
            user = userService.resolveFromErp(claims.getUser(), claims.getName(), clientIp(request));
        }
        catch (ServiceException e)
        {
            // 这一类可以说清楚：「你在本站的账号已被停用」——他已经证明了自己是谁，
            // 告诉他为什么进不来是合理的，而且这是站长能处理的事
            return blankPage(REASON_REJECTED, e.getMessage());
        }

        // 从 ERP「站点用户」按钮进来的（令牌带 scope=users）能管本站用户，见 ConsoleIdentity.userAdmin；
        // 菜单里留着「用户管理」的站（console.users-menu），从「进入管理」进来的站长也能
        boolean viaUsersButton = JumpToken.SCOPE_USERS.equals(claims.getScope());
        boolean userAdmin = grantsUserAdmin(viaUsersButton, consoleProperties.isUsersMenu(),
                userService.permsOf(user.getRoleCode()));
        startSession(request, user, ConsoleIdentity.VIA_ERP, userAdmin);

        log.info("管理台入场（ERP 免密）：jti={} 用户={} 角色={} 用途={}",
                claims.getJti(), user.getUsername(), user.getRoleCode(), viaUsersButton ? "站点用户" : "进入管理");

        // 302 到首页。落地不带 siteCode：一个部署一个站，站点身份从服务端来，
        // 地址栏上根本不该出现它——出现了就会有人去改它。
        // 「站点用户」进来的直接落到用户列表（前端 /site-users，不在左边菜单里）
        return viaUsersButton ? "redirect:/site-users" : "redirect:/";
    }

    /**
     * 有人直接用浏览器打开了 {@code /sso}。
     *
     * <p>这不是错误，是走错门了——送去那个空页面即可。<b>这个方法不接受任何参数</b>：
     * 加一个 {@code ?t=} 的兼容分支，就等于把令牌重新放回 URL 上，
     * 前面费劲改成 POST 就白做了。</p>
     */
    @GetMapping("/sso")
    public String ssoGet()
    {
        return blankPage(REASON_NO_TOKEN, null);
    }

    /**
     * 这个会话能不能管本站用户。
     *
     * <p>从「站点用户」按钮进来的一律能。菜单里留着「用户管理」的站（{@code console.users-menu=true}，
     * 统领）从「进入管理」进来的也能，但角色要有 {@code site:user:list}——和菜单拿掉之前
     * 那几个接口的 {@code @RequirePerm} 同一个口径。</p>
     */
    static boolean grantsUserAdmin(boolean viaUsersButton, boolean usersMenu, List<String> perms)
    {
        if (viaUsersButton)
        {
            return true;
        }
        return usersMenu && perms != null && (perms.contains("*") || perms.contains("site:user:list"));
    }

    /** 建会话。以前这段是「两个入口共用」的，现在只有这一个入口 */
    private void startSession(HttpServletRequest request, SiteUser user, String via, boolean userAdmin)
    {
        SiteInfo site = siteInfoService.require();
        ConsoleIdentity identity = new ConsoleIdentity();
        identity.setUserId(user.getId());
        identity.setUserName(user.getUsername());
        identity.setNickName(user.getNickname());
        identity.setRoleCode(user.getRoleCode());
        identity.setPerms(userService.permsOf(user.getRoleCode()));
        identity.setVia(via);
        identity.setUserAdmin(userAdmin);
        identity.setSiteCode(site.getSiteCode());
        identity.setSiteName(site.getSiteName());
        identity.setLangs(site.getLangs());
        identity.setCurrency(site.getCurrency());
        identity.setShopCode(site.getShopCode());
        // 防会话固定：先销毁可能存在的旧会话，再建新的
        ConsoleSession.start(request, identity);
    }

    /**
     * 取客户端 IP。
     *
     * <p>优先 {@code X-Forwarded-For} 的第一段——这个服务一定跑在 nginx 后面，
     * {@code getRemoteAddr()} 拿到的永远是 {@code 127.0.0.1}。</p>
     *
     * <p>⚠ 这个头是客户端可伪造的，所以它<b>只用来记日志</b>，不参与任何判定。
     * 要让它可信，得在 nginx 上用 {@code proxy_set_header X-Forwarded-For $remote_addr}
     * 覆盖掉客户端传来的值（注意不是 {@code $proxy_add_x_forwarded_for}，那是追加）。</p>
     */
    static String clientIp(HttpServletRequest request)
    {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
        {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty())
            {
                return first.length() > 64 ? first.substring(0, 64) : first;
            }
        }

        // ⚠ X-Real-IP 这一层是 2026-09-09 补的，为了 IPv6。别删。
        //
        //   生产上配了 SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK，Spring 的
        //   ForwardedHeaderFilter 会**把 X-Forwarded-For 消费掉并从请求里删除**，
        //   然后用它重写 getRemoteAddr()。所以上面那一支在生产上根本走不到。
        //
        //   而那个重写对 IPv6 是**破坏性**的：它走 URI 解析，而 URI 规范里
        //   IPv6 必须带方括号，nginx 的 $proxy_add_x_forwarded_for 给的却是裸写。
        //   实测（Spring 的 UriComponentsBuilder）：
        //
        //       "2a00:1450:4001::1"    -> host=2a00, port=1450   ← 地址被拆毁
        //       "[2a00:1450:4001::1]"  -> host=[2a00:1450:4001::1]  ← 方括号留着
        //       "8.8.8.8"              -> host=8.8.8.8            ← IPv4 没事
        //
        //   这就是为什么这个问题**只在 IPv6 上出现**，IPv4 一切正常——
        //   而它的表现是「IPv6 访客的国家和 IP 全是空的」，不报任何错。
        //
        //   X-Real-IP 是 nginx 用 $remote_addr 原样设的，而且**不在**
        //   ForwardedHeaderFilter 的删除清单里，所以它是没被动过的那一份。
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank())
        {
            String value = real.trim();
            return value.length() > 64 ? value.substring(0, 64) : value;
        }
        return request.getRemoteAddr();
    }

    private String blankPage(String reason, String detail)
    {
        StringBuilder url = new StringBuilder("redirect:/login?reason=")
                .append(URLEncoder.encode(reason, StandardCharsets.UTF_8));
        if (detail != null && !detail.isBlank())
        {
            url.append("&detail=").append(URLEncoder.encode(detail, StandardCharsets.UTF_8));
        }
        return url.toString();
    }
}
