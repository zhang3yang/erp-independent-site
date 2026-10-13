package com.ruoyi.station.console.session;

import java.security.SecureRandom;
import java.util.Base64;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 匿名访客的统计标识。两个 Cookie：{@code SITE_VISITOR}（长期）+ {@code SITE_SESSION}（30 分钟）。
 *
 * <h3>⚠ 这是这个工程里第四套 Cookie，前三套一个都不能拿来用</h3>
 * <ul>
 *   <li>{@code CONSOLE_SESSION} —— 管理台身份，钥匙在 Redis；</li>
 *   <li>{@code BUYER_SESSION} —— 买家身份，钥匙在 Redis；</li>
 *   <li>{@code CHAT_VISITOR} —— 哪条对话线程，真身在 {@code u_s_chat}；</li>
 *   <li><b>这一套</b> —— 「这两次浏览是不是同一个浏览器」。<b>不代表任何身份、
 *       不能打开任何数据</b>。</li>
 * </ul>
 *
 * <p>为什么不复用 {@code CHAT_VISITOR}：它只在访客<b>真的开口说话</b>时才发
 * （{@link ChatVisitor} 类注释最后一条）。绝大多数访客一句话都不说，
 * 拿它当 UV 依据的话，UV 会变成「说过话的人数」——那是另一个指标，而且小两个数量级。</p>
 *
 * <h3>为什么这个值可以短、可以不 HttpOnly（但还是设了）</h3>
 * <p>另外三套的 Cookie 值都是凭据，猜中就等于拿到别人的东西，所以是 32 字节。
 * 这个值猜中什么都拿不到——最坏的结果是「把自己的浏览算到别人头上」，
 * 而那个人的浏览记录里也没有任何私人内容。所以 16 字节够了。</p>
 *
 * <p>{@code HttpOnly} 照样设上：前台的埋点脚本<b>不需要读</b>这个值
 * （Cookie 会自己跟着请求走），既然不需要读，就没有理由把它暴露给 JS。
 * 这一条是习惯，不是这里有什么具体的攻击面。</p>
 *
 * <h3>⚠ 只在 {@code /openapi/site/pv} 上种，不在取数接口上种</h3>
 * <p>诱惑是「在 {@code /info} 上就把 Cookie 种好」。<b>不行</b>：那个接口首屏是
 * <b>Nuxt 服务端</b>调的（{@code useSite.ts} 里 {@code import.meta.server} 那一支），
 * {@code Set-Cookie} 会被发给 Node 进程而不是浏览器——服务器自己变成了一个
 * 永远在浏览的「访客」，而真实访客一个 Cookie 都没拿到。</p>
 *
 * <p>埋点接口是<b>浏览器直接调</b>的（{@code sendBeacon}），这条路上的
 * {@code Set-Cookie} 才真的会落到浏览器里。这也是整个方案不蹭现有取数接口的原因之一。</p>
 */
@Component
public class SiteVisitor
{
    /**
     * 16 字节 = 128 位。
     *
     * <p>比另外三套的 32 字节短一半，是有意的：见类注释——这个值不是凭据。
     * 128 位的碰撞概率在这个量级的访客数下完全不用考虑。</p>
     */
    private static final int ID_BYTES = 16;

    /** {@code SecureRandom} 不是 {@code Random}。这里其实不需要密码学强度，但没理由用弱的 */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StationProperties properties;

    /**
     * 访客标识：有就用，没有就发一个新的并种 Cookie。
     *
     * <p>⚠ 这个方法<b>每次都会重种</b> Cookie（即使已经有值）。这是刻意的：
     * 不重种的话 {@code Max-Age} 从第一次发出起就在倒计时，一个每周都来的老访客
     * 会在第 180 天突然变成新访客。重种等于滑动过期，和买家会话是同一个做法。</p>
     */
    public String ensureVisitorId(HttpServletRequest request, HttpServletResponse response)
    {
        String name = properties.getPageview().getVisitorCookieName();
        String current = read(request, name);
        String id = current != null ? current : newId();
        int maxAge = (int) (properties.getPageview().getVisitorCookieDays() * 86400L);
        response.addCookie(cookie(name, id, maxAge));
        return id;
    }

    /**
     * 会话标识：有就用，没有就发一个新的。
     *
     * <p>「30 分钟不活动算一次新会话」这个语义<b>完全由 Cookie 的 Max-Age 实现</b>，
     * 服务端不存任何东西：每次上报都把 Max-Age 重设成 30 分钟，
     * 访客一直在点就一直是同一个会话；停了超过 30 分钟，浏览器自己把 Cookie 删了，
     * 下次来拿到的就是一个新的。</p>
     *
     * <p>⚠ 所以这个会话<b>跨不过浏览器重启</b>（Max-Age 会存盘，其实跨得过；
     * 但访客关掉浏览器超过 30 分钟就跨不过了）。这和通行的「会话」定义一致。</p>
     */
    public String ensureSessionId(HttpServletRequest request, HttpServletResponse response)
    {
        String name = properties.getPageview().getSessionCookieName();
        String current = read(request, name);
        String id = current != null ? current : newId();
        int maxAge = properties.getPageview().getSessionMinutes() * 60;
        response.addCookie(cookie(name, id, maxAge));
        return id;
    }

    private String newId()
    {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String read(HttpServletRequest request, String name)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return null;
        }
        for (Cookie cookie : cookies)
        {
            if (name.equals(cookie.getName()))
            {
                String value = cookie.getValue();
                if (value == null || value.isBlank())
                {
                    return null;
                }
                // ⚠ 长度上限挡的是「有人手工把这个 Cookie 改成 10KB 的串」。
                //   库里那一列是 varchar(64)，不挡的话插入会报错——而报错发生在
                //   异步写线程里，请求侧完全看不出来，只是这个访客的浏览永远记不上
                return value.length() > 64 ? null : value;
            }
        }
        return null;
    }

    private Cookie cookie(String name, String value, int maxAgeSeconds)
    {
        Cookie cookie = new Cookie(name, value);
        // 见类注释：埋点脚本不需要读它，既然不需要就不暴露给 JS
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(properties.getPageview().isCookieSecure());
        // Servlet 6 没有 setSameSite，只能走属性
        cookie.setAttribute("SameSite", "Lax");
        // ⚠ 不 setDomain：host-only。理由见 BuyerSession 类注释里同样的那条
        return cookie;
    }
}
