package com.ruoyi.station.console.session;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 把买家会话从一个域名交接到另一个域名。
 *
 * <p>前台拆成了两个域：商城在主域（首页、商品、购物车、下单、登录注册），
 * 买家中心在 {@code buyer.} 子域（我的订单、账号资料、地址簿管理）。
 * 两边各自持有一份 host-only 的 {@code BUYER_SESSION}，<b>装的是同一把钥匙</b>。
 * 这个类负责让第二个域拿到那把钥匙。</p>
 *
 * <p>设计见 {@code docs/设计稿/2026-09-08-独立站域名拆分.md}。</p>
 *
 * <h3>为什么要一张票，不直接把令牌拼在 URL 上</h3>
 * <pre>
 * https://buyer.example.com/...?t=&lt;会话令牌&gt;      ← 绝对不行
 * </pre>
 * <p>URL 会进浏览器历史、进 {@code Referer} 头、进 nginx 的访问日志。
 * 把 30 天有效的会话令牌抄进这三个地方，等于把钥匙贴在门上。</p>
 *
 * <p>所以走一张<b>一次性、60 秒有效</b>的票：URL 里出现的是票，票兑换成令牌之后
 * 立刻作废，日志里留下的那串再也换不出任何东西。</p>
 *
 * <h3>为什么是 Redis 票，不是 HMAC 签名令牌</h3>
 * <p>ERP「进入管理」跳独立站管理台走的是自包含签名令牌（{@code SsoController}），
 * 那是因为<b>那两端是两套系统</b>，跨的是信任边界，只能靠签名。</p>
 *
 * <p>这里两个域名是<b>同一个后端进程、同一个 Redis</b>，不存在信任边界。
 * 用 Redis 票更简单，而且一次性作废是免费的——签名令牌要另做防重放（jti 黑名单），
 * 那反而又要一个 Redis 键。</p>
 *
 * <h3>⚠ 别把这个挂到 {@code /sso} 上</h3>
 * <p>那个路径是管理台的（{@code SsoController}），而且 nginx 只在 {@code console.}
 * 域上转发它。两回事。</p>
 */
@Component
public class BuyerHandoff
{
    private static final Logger log = LoggerFactory.getLogger(BuyerHandoff.class);

    /** 和会话键同一个命名空间风格，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:buyer:handoff:";

    /**
     * 票只活 60 秒。
     *
     * <p>它的全部寿命就是「从一个 302 传到下一个 302」，正常情况下是毫秒级。
     * 给 60 秒是留给慢网络和用户手动点一下的余量，再长没有意义——
     * 票在 URL 里露过面，能早点作废就早点作废。</p>
     *
     * <p>过期了的表现是交接失败，用户被送去登录页，最坏情况是「再点一次」。</p>
     */
    private static final Duration TTL = Duration.ofSeconds(60);

    /** 和会话令牌同样长度：这串也是猜中就能拿到别人身份的东西，按不可穷举取 */
    private static final int TICKET_BYTES = 32;

    /** {@code SecureRandom} 不是 {@code Random}。后者可预测，用它生成票等于没有票 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 取值并删除，一条命令里做完。见 {@link #redeem} 里为什么不用 {@code GETDEL}。
     *
     * <p>取不到时 Lua 返回 {@code nil}，Spring 那边转成 Java 的 {@code null}。</p>
     */
    private static final RedisScript<String> GET_DEL = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v then redis.call('DEL', KEYS[1]) end "
                    + "return v",
            String.class);

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 给一个已经存在的会话令牌签一张票。
     *
     * @param sessionToken 调用方必须已经确认请求带着有效会话（走过 requireBuyer）
     * @return 票号；Redis 写失败返回 null，调用方据此让前端跳过交接
     */
    public String issue(String sessionToken)
    {
        if (sessionToken == null || sessionToken.isBlank())
        {
            return null;
        }
        byte[] bytes = new byte[TICKET_BYTES];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try
        {
            redis.opsForValue().set(KEY_PREFIX + ticket, sessionToken, TTL);
            return ticket;
        }
        catch (Exception e)
        {
            // 签不出票不该让登录失败——登录本身已经成功了，只是另一个域这次拿不到会话。
            // 买家在买家中心会被要求再走一次登录，那时候主域认得他，无感换回来
            log.warn("签发买家交接票失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 用票换回会话令牌，<b>换完立刻作废</b>。
     *
     * <p>取和删必须是原子的：分成「先 GET 再 DEL」的话中间有窗口，
     * 同一张票被日志抓到之后可以在那个窗口里重放。</p>
     *
     * <h3>⚠ 为什么是 Lua 脚本，不是 {@code GETDEL}</h3>
     * <p>{@code GETDEL}（Spring Data 的 {@code getAndDelete}）是 <b>Redis 6.2</b>
     * 才有的命令。而<b>线上那台跑的是 3.0.503</b>，开发机上是 5.0.14——两个都不够。</p>
     *
     * <p>用它的后果是静默的：命令不认识，Spring 抛异常，这里 catch 住返回 null，
     * 于是<b>交接永远失败</b>（买家进买家中心显示未登录），<b>而且票不会被删</b>
     * ——一次性保护同时也没了。2026-09-08 本机实测踩到过，日志里只有一行
     * 「兑换买家交接票失败：Error in execution」。</p>
     *
     * <p>{@code EVAL} 从 Redis <b>2.6</b> 就有，脚本在服务端单线程执行，
     * 原子性和 {@code GETDEL} 一样。</p>
     *
     * @return 会话令牌；票不存在、已用过、已过期，或 Redis 挂了，都返回 null
     */
    public String redeem(String ticket)
    {
        if (ticket == null || ticket.isBlank())
        {
            return null;
        }
        // 长度上限挡的是「有人手工构造一个 10KB 的票号」——那种串没必要送到 Redis 去
        if (ticket.length() > 64)
        {
            return null;
        }
        try
        {
            return redis.execute(GET_DEL, Collections.singletonList(KEY_PREFIX + ticket));
        }
        catch (Exception e)
        {
            log.warn("兑换买家交接票失败：{}", e.getMessage());
            return null;
        }
    }
}
