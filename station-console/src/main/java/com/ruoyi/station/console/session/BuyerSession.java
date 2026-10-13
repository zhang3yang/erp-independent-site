package com.ruoyi.station.console.session;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 前台买家的会话。<b>和管理台的会话是两套东西，一点都不共用。</b>
 *
 * <h3>为什么不复用 {@link ConsoleSession}</h3>
 * <p>管理台那套用的是 Servlet 的 {@code HttpSession}（Cookie 名 {@code CONSOLE_SESSION}，
 * 存 Redis）。买家会话不走它，理由有三条，第一条是硬的：</p>
 * <ol>
 *   <li><b>一个 Servlet 容器只有一个 session Cookie 名。</b>买家和操作员共用
 *       {@code HttpSession} 就意味着共用那个 Cookie 和那批 Redis 键——
 *       两拨完全不同的人挤在同一个身份容器里，靠「读的是不同的 attribute」区分。
 *       那种区分是一句约定，而约定会被下一次重构违反，代价是买家拿到管理台会话。</li>
 *   <li><b>有效期差两个数量级。</b>管理台 8 小时不活跃就掉线（那是自己人在办公室里用），
 *       买家要 30 天——外贸买家一个月才回来看一次订单进度。同一套 session 配不出两个值。</li>
 *   <li><b>两者的失效面不同。</b>删一批买家会话不该把运营踢下线，反过来也一样。
 *       键前缀分开，运维一条 {@code redis-cli --scan --pattern 'station:buyer:session:*'}
 *       就能只清买家那一批。</li>
 * </ol>
 *
 * <h3>会话长什么样</h3>
 * <pre>
 * Cookie: BUYER_SESSION=&lt;32 字节随机数的 base64url&gt;   HttpOnly, SameSite=Lax, Secure
 *                            │
 *                            ▼
 * Redis:  station:buyer:session:&lt;token&gt;  →  &lt;buyercode&gt;      TTL 30 天（滑动）
 * </pre>
 *
 * <p><b>Cookie 里是一个不透明的随机串，不是签名令牌。</b>取舍：签名令牌（JWT 那类）
 * 不用查 Redis，但<b>签出去就撤不回</b>——买家点了「退出」、或者站长停用了他，
 * 那张令牌在过期前照样有效。不透明令牌每次多一次 Redis 读（本机、亚毫秒），
 * 换来「删掉键就等于立刻踢下线」。前台这条链路本来每个请求都要查库，多一次 Redis 不值一提。</p>
 *
 * <p>Cookie 是 {@code HttpOnly} 的：JS 读不到，所以 XSS 偷不走会话。这和管理台
 * 那套的理由是同一条（见 {@code station-console-ui/src/utils/request.js} 顶部）。</p>
 *
 * <h3>⚠ Domain 作用域：永远 host-only，两个 host 各持一份装同一把钥匙</h3>
 *
 * <p>这个类<b>从不调用 {@code setDomain}</b>。前台分成两个 host 之后，
 * 两个域各自持有一份 host-only 的 {@code BUYER_SESSION}，<b>内容是同一个令牌</b>，
 * 于是指向同一个 Redis 键。令牌怎么从一个域交到另一个域，见 {@link BuyerHandoff}：
 * 一张 60 秒的一次性票。任意一边退出，{@link #end} 删掉那个键，两边一起失效——
 * <b>不需要任何跨域同步代码</b>。</p>
 *
 * <h3>为什么不放宽成 {@code Domain=.example.com}</h3>
 *
 * <p>2026-09-08 有过一版改成可配 {@code station.cookie-domain} 的实现，
 * 前提是「登录发生在 {@code buyer.} 上，而用会话的页面大半在主域」。
 * <b>康辰的布局不是那样</b>：登录页在<b>主域</b>（用户 2026-09-08 明确确认，
 * 理由见 {@code docs/设计稿/2026-09-08-独立站域名拆分.md}），
 * 所以那个前提不成立。2026-09-09 按用户决定把 {@code setDomain} 去掉了。</p>
 *
 * <p>而放宽的代价是具体的，不是「理论上扩大暴露面」：</p>
 *
 * <blockquote>
 * {@code file.<主域>} 是 <b>CNAME 到腾讯云 COS</b> 的（康辰指向
 * {@code kangchen-1479674498.cos.ap-shanghai.myqcloud.com}，不经我们的机器）。
 * 带 Domain 的 Cookie 会<b>跟着每一张图片请求发到腾讯云</b>，
 * 30 天有效的会话令牌就落进了第三方的访问日志。
 * {@code HttpOnly} 挡不住这个——它挡的是 JS 读取，不是发送。
 * </blockquote>
 *
 * <p>顺带一提，{@code SameSite} 在这里也帮不上忙：它判的是「站点」（eTLD+1），
 * 而 {@code kangchen.vclinker.com}、{@code buyer.} 、{@code console.} 的 eTLD+1
 * 都是 {@code vclinker.com}，彼此同站。<b>真正把买家会话挡在管理台和 COS 之外的，
 * 就是「不调 setDomain」这一件事。</b></p>
 *
 * <h3>CSRF</h3>
 * <p>靠 {@code SameSite=Lax}：跨站的 POST 不会带上这个 Cookie，所以别人的页面
 * 没法拿买家的身份替他下单。这也是管理台那套的做法。<b>不要为了某个第三方嵌入
 * 把它改成 {@code None}</b>——那一改就等于把 CSRF 的门打开，届时得先上 CSRF token。</p>
 */
@Component
public class BuyerSession
{
    private static final Logger log = LoggerFactory.getLogger(BuyerSession.class);

    /** 和会话、jti、限流用同一个命名空间风格，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:buyer:session:";

    /**
     * 32 字节 = 256 位。
     *
     * <p>这个串是唯一的凭据，猜中就是别人的账号，所以长度按「不可穷举」取，
     * 不按「够短好看」取。base64url 之后 43 个字符，Cookie 完全放得下。</p>
     */
    private static final int TOKEN_BYTES = 32;

    /** {@code SecureRandom} 不是 {@code Random}。后者可预测，用它生成会话令牌等于没有令牌 */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 登录成功后建立会话：发一个新令牌，写 Redis，种 Cookie。
     *
     * <p>顺手把请求里带来的旧令牌销毁（如果有）——防会话固定，理由和
     * {@link ConsoleSession#start} 里那句 {@code invalidate()} 完全一样：
     * 攻击者先让受害者的浏览器带上一个他知道的令牌，等受害者登录成功，那个令牌就有效了。</p>
     */
    public void start(HttpServletRequest request, HttpServletResponse response, String buyerCode)
    {
        end(request, response);

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redis.opsForValue().set(KEY_PREFIX + token, buyerCode, ttl());
        response.addCookie(cookie(token, (int) ttl().toSeconds()));
    }

    /**
     * 当前买家的客户编码，没有会话就返回 null。
     *
     * <p><b>有副作用：会续期。</b>带着有效 Cookie 来一次，Redis 里的 TTL 就重新算
     * 30 天（滑动过期）。不续期的话，一个每周都来看订单的买家会在第 30 天被莫名踢出去。</p>
     *
     * <p>⚠ Cookie 的 Max-Age 不跟着续：那需要每个响应都重发一次 Set-Cookie。
     * 浏览器侧那份存的是 30 天，服务端 Redis 里那份是滑动的——以服务端为准，
     * 最坏情况是 Cookie 先过期，买家重新登录一次。</p>
     */
    public String current(HttpServletRequest request)
    {
        String token = tokenOf(request);
        if (token == null)
        {
            return null;
        }
        try
        {
            String buyerCode = redis.opsForValue().get(KEY_PREFIX + token);
            if (buyerCode == null || buyerCode.isBlank())
            {
                return null;
            }
            // 滑动续期
            redis.expire(KEY_PREFIX + token, ttl());
            return buyerCode;
        }
        catch (Exception e)
        {
            // ⚠ Redis 挂了这里返回 null = 所有买家都变成未登录。
            //
            // 这和 InquiryRateLimiter「拿不准就放行」是相反的选择，而且是对的：
            // 那边放行只是多几条垃圾询盘，这边「放行」意味着无凭据地认下一个身份。
            // 认证上拿不准必须拒绝。
            log.warn("读买家会话失败，本次按未登录处理：{}", e.getMessage());
            return null;
        }
    }

    /** 退出：删 Redis 里那把，并让浏览器把 Cookie 丢掉 */
    public void end(HttpServletRequest request, HttpServletResponse response)
    {
        String token = tokenOf(request);
        if (token != null)
        {
            try
            {
                redis.delete(KEY_PREFIX + token);
            }
            catch (Exception e)
            {
                // 删不掉也要把 Cookie 清了：至少这个浏览器不再带着它
                log.warn("删买家会话失败：{}", e.getMessage());
            }
        }
        // Max-Age=0 让浏览器立刻丢弃。⚠ 属性要和当初种下去时一致，否则浏览器认为
        // 这是另一个 Cookie，不会覆盖掉原来那个
        response.addCookie(cookie("", 0));
    }

    /**
     * 当前请求带来的<b>会话令牌本身</b>（不是买家编码），没有就返回 null。
     *
     * <p>只给跨域交接用（{@link BuyerHandoff}）：主域要把自己这把钥匙的副本
     * 交给 {@code buyer.} 子域。业务代码要认身份请用 {@link #current}，
     * 那个会顺带续期，这个不会。</p>
     */
    public String currentToken(HttpServletRequest request)
    {
        return tokenOf(request);
    }

    /**
     * 这把钥匙还开得了门吗。
     *
     * <p>交接时用：票签发之后的 60 秒里，买家完全可能在另一个域上点了退出，
     * 那时候这个令牌对应的 Redis 键已经没了，不该再把它种到第二个域上去——
     * 种下去只是个开不了门的废 Cookie，但会让「我明明退出了怎么还显示登录」
     * 这种问题多一个来源。</p>
     *
     * <p>⚠ Redis 挂了返回 false，理由和 {@link #current} 里那段一样：
     * 认证上拿不准必须拒绝。</p>
     */
    public boolean isLive(String token)
    {
        if (token == null || token.isBlank())
        {
            return false;
        }
        try
        {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + token));
        }
        catch (Exception e)
        {
            log.warn("校验买家会话失败，按无效处理：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 拿一把<b>已经存在</b>的钥匙，只种 Cookie，<b>不碰 Redis</b>。
     *
     * <p>这是域名拆分的核心动作：主域和 {@code buyer.} 子域各自持有一份 host-only
     * Cookie，<b>内容相同</b>，于是指向同一个 Redis 键。任意一边点退出，
     * {@link #end} 删掉那个键，另一边的 Cookie 立刻变成废钥匙——
     * <b>退出登录不需要任何跨域同步代码</b>。</p>
     *
     * <h3>⚠ 不能用 {@link #start} 代替</h3>
     * <p>{@code start()} 干的第一件事是 {@code end()}——它会<b>删掉旧令牌的 Redis 键</b>，
     * 然后生成一个<b>新</b>令牌。用它来交接，{@code buyer.} 会拿到一把和主域不同的钥匙，
     * 「删一个键两边一起失效」这个前提当场作废。</p>
     *
     * <p>而且<b>表面上完全看不出来</b>：两个域都能正常登录、正常用，只有在一边退出时
     * 另一边不掉线。这种缺陷不会在开发时被发现。</p>
     *
     * <h3>为什么这不是会话固定漏洞</h3>
     * <p>会话固定的要害是「攻击者能让受害者的浏览器用上一个攻击者已知的令牌」。
     * 这里的令牌来自一张<b>一次性、60 秒有效、只发给已登录会话</b>的票
     * （{@link BuyerHandoff}），攻击者拿不到也造不出。</p>
     *
     * @param token 从票里兑换出来的令牌，调用方必须先用 {@link #isLive} 验过
     */
    public void adopt(HttpServletResponse response, String token)
    {
        response.addCookie(cookie(token, (int) ttl().toSeconds()));
    }

    private Cookie cookie(String value, int maxAgeSeconds)
    {
        Cookie cookie = new Cookie(properties.getBuyer().getCookieName(), value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(properties.getBuyer().isCookieSecure());
        // Servlet 6 没有 setSameSite，只能走属性
        cookie.setAttribute("SameSite", "Lax");
        // ⚠ 刻意不调 setDomain，见类注释「Domain 作用域」那一节。
        //   这个 Cookie 永远是 host-only 的，两个 host 各持一份、装同一把钥匙。
        return cookie;
    }

    private String tokenOf(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return null;
        }
        String name = properties.getBuyer().getCookieName();
        for (Cookie cookie : cookies)
        {
            if (name.equals(cookie.getName()))
            {
                String value = cookie.getValue();
                // ⚠ 空值要跳过、不能 return null：放宽 Cookie 作用域的那次发布之后，
                //   请求里可能同时带着「被清空的老 host-only 那份」和「有效的新那份」
                //   （见 dropLegacyHostCookie），而浏览器发上来的顺序是不确定的。
                //   原来写的是 return，撞上空值在前的顺序就恒定判成未登录。
                if (value != null && !value.isBlank())
                {
                    return value;
                }
            }
        }
        return null;
    }

    private Duration ttl()
    {
        int days = properties.getBuyer().getSessionDays();
        return Duration.ofDays(days <= 0 ? 30 : days);
    }
}
