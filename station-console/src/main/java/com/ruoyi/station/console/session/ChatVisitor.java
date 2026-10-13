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
 * 匿名访客的聊天令牌。存在 {@code CHAT_VISITOR} 这个 Cookie 里。
 *
 * <h3>⚠ 令牌为什么在 Cookie 里，不在 URL 上</h3>
 * <p>这个串是匿名会话的<b>唯一凭据</b>——拿到就能读走整条对话。放 query 参数
 * （{@code ?token=xxx}）会让它原样进 nginx 的 access log，那份日志谁都能看，
 * 而且会跟着日志备份一起留很久。</p>
 *
 * <p>所以：{@code HttpOnly}（JS 读不到，XSS 偷不走）、{@code SameSite=Lax}（挡 CSRF）、
 * {@code Secure} 跟随配置、不带 {@code Domain}（host-only）。
 * 这几条和 {@link BuyerSession}、{@code ConsoleSession} 是同一套，不是新发明。</p>
 *
 * <h3>和另外两套会话的区别：这里没有 Redis</h3>
 * <p>买家会话的 Cookie 值是一把钥匙，真身在 Redis 里（{@code station:buyer:session:*}），
 * 所以能「删键即踢下线」。聊天令牌不需要那个能力——它认的不是<b>身份</b>，
 * 是<b>哪条对话线程</b>，而线程本身就在库里（{@code u_s_chat.visitortoken}）。
 * 多一层 Redis 只是多一个会和库不一致的地方。</p>
 *
 * <p>代价：没法「使某个访客的聊天令牌立即失效」。真要那么做就是删那条线程，
 * 而删对话不是运营该有的日常操作。</p>
 *
 * <h3>⚠ 拿到令牌 ≠ 建了线程</h3>
 * <p>{@link #issue} 只发令牌、种 Cookie，<b>不写库</b>。线程要等访客真的发出第一条消息
 * 才建（{@code SiteChatService.send}）。呼应 {@code u_s_buyer} 那条「没有『新建』按钮，
 * 就不会有一个对不上任何人的档案」——只是好奇点了一下小圆圈的人，
 * 不该在运营的会话列表里占一行。</p>
 */
@Component
public class ChatVisitor
{
    /**
     * 32 字节 = 256 位，和买家会话同一个长度。
     *
     * <p>这个串猜中就是别人的对话，所以长度按「不可穷举」取，不按「够短好看」取。</p>
     */
    private static final int TOKEN_BYTES = 32;

    /** {@code SecureRandom} 不是 {@code Random}。后者可预测，用它生成令牌等于没有令牌 */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StationProperties properties;

    /** 请求里带的令牌，没有就返回 null */
    public String current(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return null;
        }
        String name = properties.getChat().getCookieName();
        for (Cookie cookie : cookies)
        {
            if (name.equals(cookie.getName()))
            {
                String value = cookie.getValue();
                // ⚠ 跳过空值继续找，不要在这里 return null：作用域从 host-only 改成
                //   带 Domain 之后，老访客的请求里会同时带着两份同名 Cookie
                //   （键是「名字+Domain+Path」三元组，新的盖不掉老的），
                //   而发上来的顺序不确定。理由和 BuyerSession#tokenOf 那一条一样。
                if (value != null && !value.isBlank())
                {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 发一个新令牌并种 Cookie。<b>不写库</b>，见类注释最后一条。
     *
     * @return 新令牌
     */
    public String issue(HttpServletResponse response)
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        response.addCookie(cookie(token, (int) (properties.getChat().getCookieDays() * 86400L)));
        return token;
    }

    /**
     * 已经有令牌就用它，没有就发一个。
     *
     * <p>大部分调用方要的是这个语义，而不是分别判两次。</p>
     */
    public String ensure(HttpServletRequest request, HttpServletResponse response)
    {
        String token = current(request);
        return token != null ? token : issue(response);
    }

    private Cookie cookie(String value, int maxAgeSeconds)
    {
        Cookie cookie = new Cookie(properties.getChat().getCookieName(), value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(properties.getChat().isCookieSecure());
        // Servlet 6 没有 setSameSite，只能走属性
        cookie.setAttribute("SameSite", "Lax");
        // ⚠ 刻意不调 setDomain，和 BUYER_SESSION 保持一致（见那边类注释的「Domain 作用域」）。
        //
        //   这个 Cookie 认的不是身份，是「哪条对话线程」，放宽作用域的收益只是
        //   「在 buyer. 上点开咨询，和刚才在商品页聊的是同一条线程」——很小。
        //   而代价和会话 Cookie 是同一个：file.<主域> CNAME 到腾讯云 COS，
        //   带 Domain 的 Cookie 会跟着每一张图片请求发过去，
        //   等于把一个 180 天稳定的访客标识送给第三方。
        //
        //   不放宽的代价只是多出一条空线程（那条线程会进运营的待回复列表，
        //   看着像有个客人问了话又跑了）。而买家中心那三页本来也不是聊天的场景。
        return cookie;
    }
}
