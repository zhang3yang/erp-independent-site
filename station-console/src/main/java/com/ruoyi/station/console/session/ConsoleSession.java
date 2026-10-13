package com.ruoyi.station.console.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 会话的读写口子。全项目只有这里碰 {@code HttpSession}。
 *
 * <p>收敛成一个类是为了让「身份怎么进会话的」只有一条路径可看——尤其是
 * {@link #start} 里那句 {@code invalidate()}，那是防会话固定攻击的关键一步，
 * 散在 Controller 里迟早会被某次重构顺手删掉。</p>
 */
public final class ConsoleSession
{
    private static final String ATTR_IDENTITY = "console.identity";

    private ConsoleSession()
    {
    }

    /**
     * 验票成功后建立会话。
     *
     * <p><b>先销毁旧会话再建新的</b>，这一步不能省：会话固定攻击的做法是先在受害者浏览器里
     * 种一个攻击者已知的 session id，等受害者验票成功后，那个 id 就成了一个有效会话。
     * 换一个全新的 session id 是唯一的解法。</p>
     */
    public static void start(HttpServletRequest request, ConsoleIdentity identity)
    {
        HttpSession existing = request.getSession(false);
        if (existing != null)
        {
            existing.invalidate();
        }
        request.getSession(true).setAttribute(ATTR_IDENTITY, identity);
    }

    /** 取当前身份，没有会话就返回 null */
    public static ConsoleIdentity current(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);
        if (session == null)
        {
            return null;
        }
        Object value = session.getAttribute(ATTR_IDENTITY);
        return value instanceof ConsoleIdentity identity ? identity : null;
    }

    /**
     * 取当前身份，没有就抛。
     *
     * <p>给已经过了拦截器的地方用——走到那里时会话必然存在，返回 null 让调用方自己判的话，
     * 某个分支忘了判就是一次无身份的调用。</p>
     */
    public static ConsoleIdentity require(HttpServletRequest request)
    {
        ConsoleIdentity identity = current(request);
        if (identity == null)
        {
            throw new IllegalStateException("会话缺失：这个请求应该已经被 AuthInterceptor 拦下了");
        }
        return identity;
    }

    public static void end(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);
        if (session != null)
        {
            session.invalidate();
        }
    }
}
