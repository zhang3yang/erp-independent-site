package com.ruoyi.station.console.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 询盘接口的按 IP 限流。
 *
 * <h3>为什么这个东西非有不可</h3>
 *
 * <p>{@code POST /openapi/site/inquiry} 是<b>整个服务唯一对公网开放的写接口</b>：
 * 匿名、没有会话、没有验证码。它之前只有一个蜜罐字段挡着。</p>
 *
 * <p>蜜罐挡的是「把表单里每个 input 都填一遍」的通用垃圾脚本，
 * <b>不挡任何一个专门冲着这个站来的人</b>——照着页面上的字段发请求就绕过去了。
 * 后果不是「多几条垃圾数据」这么轻：</p>
 * <ul>
 *   <li>{@code u_s_inquiry} 被灌满，运营在真询盘里再也捞不出东西——
 *       而这个站存在的全部理由就是收询盘；</li>
 *   <li>每条都会触发一封通知邮件，而 {@link InquiryNotifier} 是<b>单线程串行</b>发的，
 *       队列只会越堆越长，真客户那封信被排到几小时之后；</li>
 *   <li>发件量突增会让 SMTP 服务商直接把这个发件账号限流或者停掉，
 *       那时候连正常询盘也发不出去了。</li>
 * </ul>
 *
 * <h3>⚠ 这是补回来的，不是新加的</h3>
 * <p>ERP 那版接口上写着 {@code @RateLimiter(count = 5, limitType = IP)}。
 * 接口迁到这个工程时那个注解<b>没有跟过来</b>——RuoYi 的限流切面在
 * {@code ruoyi-framework} 里，而这个工程刻意不依赖 ERP 的任何一个包
 * （docs/独立站与ERP的边界.md 要点 1）。</p>
 *
 * <p>但 nginx 配置和前台的兜底代理里，那几段「必须转发真实 IP，否则每分钟 5 条/IP
 * 会退化成全站每分钟 5 条」的注释一直留着——它们指的就是这个已经不存在了的限流。
 * 于是那两处注释在很长一段时间里描述的是一件根本没发生的事。
 * 这个类把它变回真的。</p>
 *
 * <h3>两层窗口</h3>
 * <p>只有分钟窗口挡不住<b>慢速灌水</b>：卡着 5 条/分钟不间断就是 7200 条/天，
 * 上面那三个后果一个都不少。所以再叠一层「每 IP 每天」。</p>
 *
 * <h3>⚠ Redis 挂了要放行，不是拦住</h3>
 * <p>取计数失败时一律<b>放行</b>并打警告。理由很直接：
 * 丢一条真询盘 = 丢一个客户，而这一分钟里多进来几条垃圾询盘只是多几行数据。
 * 「拿不准就拒绝」在登录这种地方是对的，在收客户线索这里是反的。</p>
 *
 * <h3>为什么用 Redis 而不是进程内的 Map</h3>
 * <p>会话已经在 Redis 上了（{@code spring.session.redis}），跳转令牌的 jti 防重放
 * 也在（{@link com.ruoyi.station.console.security.JumpTokenVerifier}），
 * 所以这里不引入任何新依赖。进程内计数会在重启时清零，也没法多开一个实例——
 * 那两条正好是 application.yml 里选 Redis 存会话时写的同一个理由。</p>
 */
@Component
public class InquiryRateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(InquiryRateLimiter.class);

    /** 和会话、jti 用同一个命名空间前缀，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:inquiry:rate:";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 这个 IP 还能不能再提交。
     *
     * <p><b>有副作用</b>：返回 {@code true} 的同时已经把计数加上去了。
     * 所以每次提交只能调一次，而且要在真正落库之前调——
     * 放在后面的话，被限流的请求已经写进库了，限流就只剩一个假象。</p>
     *
     * @param clientIp 真实客户端 IP。⚠ 反代必须转发 {@code X-Forwarded-For}，
     *                 否则所有访客都是 127.0.0.1，这里就退化成全站限流
     * @return 允许提交返回 true；超了返回 false
     */
    public boolean tryAcquire(String clientIp)
    {
        String ip = clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();

        // ⚠ 两层都要走一遍，不能因为分钟窗口过了就跳过日窗口——
        //   跳过的话日计数永远加不上去，那一层等于没有
        boolean minuteOk = withinLimit(ip, "m", properties.getInquiry().getRatePerMinute(),
                Duration.ofMinutes(1));
        boolean dayOk = withinLimit(ip, "d", properties.getInquiry().getRatePerDay(),
                Duration.ofDays(1));

        if (!minuteOk || !dayOk)
        {
            log.warn("询盘限流：IP {} 被拒（分钟窗口 {}，日窗口 {}）", ip,
                    minuteOk ? "通过" : "超限", dayOk ? "通过" : "超限");
            return false;
        }
        return true;
    }

    /**
     * 一层固定窗口计数。
     *
     * <p>固定窗口不是滑动窗口，所以窗口交界处理论上能挤进两倍的量。
     * 这里不值得为那个上滑动窗口：限流的目的是把量级从「一秒几百条」压到
     * 「一分钟几条」，差一倍无所谓，而滑动窗口要么存时间戳列表、
     * 要么写 Lua 脚本，复杂度换来的精度在这个场景里没有用处。</p>
     */
    private boolean withinLimit(String ip, String scope, int limit, Duration window)
    {
        if (limit <= 0)
        {
            // 配成 0 或负数 = 关掉这一层。留这个开关是为了本地压测，生产别用
            return true;
        }

        // 窗口序号进键名：窗口一换键就换，过期由 Redis 自己收
        long slot = System.currentTimeMillis() / window.toMillis();
        String key = KEY_PREFIX + scope + ":" + slot + ":" + ip;

        try
        {
            Long count = redis.opsForValue().increment(key);
            if (count == null)
            {
                return true;
            }
            if (count == 1L)
            {
                // ⚠ 只在第一次设过期。每次都设的话，持续打请求会把过期时间一直往后推，
                //   这个键就永远不过期了——计数也就永远不清零，正常访客被永久挡住
                //
                // 窗口本身按 slot 切了，所以 TTL 只是「让用不到的键别留在内存里」，
                // 给一倍余量避免临界时被提前删掉
                redis.expire(key, window.multipliedBy(2));
            }
            return count <= limit;
        }
        catch (Exception e)
        {
            // 见类注释：拿不到计数就放行。丢一条真询盘比放进几条垃圾询盘严重得多
            log.warn("询盘限流计数失败（{}），本次放行：{}", key, e.getMessage());
            return true;
        }
    }
}
