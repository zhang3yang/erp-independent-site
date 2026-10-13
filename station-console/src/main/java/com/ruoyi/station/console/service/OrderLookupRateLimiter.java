package com.ruoyi.station.console.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 匿名查单接口（{@code POST /openapi/site/order/lookup}）的限流。
 *
 * <h3>它挡的是什么</h3>
 * <p>那个接口让<b>没有账号</b>的买家凭「单号 + 下单邮箱」查回自己那张单。
 * 单号是 {@code DD} + 流水（{@code CodeGenerator}），<b>可枚举</b>——
 * 从任意一张自己的单号往前后数就能猜出别人的。唯一挡着的是必须同时报对邮箱。</p>
 *
 * <p>于是真正的攻击面是：知道某个买家的邮箱（外贸站的客户邮箱常常印在名片、
 * 展会名录、甚至对方官网上），拿它去撞单号，撞中就能读到那张单的联系人、电话、
 * 目的城市、商品明细和买家备注——备注里经常写着目标价。对同行来说这是有价值的东西。</p>
 *
 * <p>所以这里的限流<b>不是防灌水，是访问控制的一部分</b>：撞号需要很多次尝试，
 * 把尝试次数压到一天几十次，撞中的概率就低到没有意义。</p>
 *
 * <h3>⚠ Redis 挂了要<b>拒绝</b>，和 {@link InquiryRateLimiter} 正好相反</h3>
 * <p>那个类的注释里写着「拿不到计数就放行，丢一条真询盘比放进几条垃圾询盘严重得多」。
 * <b>那条理由在这里不成立，而且反过来了</b>：这里放行的后果不是多几行垃圾数据，
 * 是把一个可枚举的接口完全敞开——Redis 一挂，撞号就没有任何上限了。</p>
 *
 * <p>代价是 Redis 不可用时买家查不了单。可以接受：会话本身就在 Redis 上
 * （{@code spring.session.redis}），Redis 挂了整个买家中心都登不进去，
 * 查单这一条路多挡一下不改变什么；而且查不到单的人手上还有确认邮件。</p>
 *
 * <h3>三层窗口</h3>
 * <table border="1">
 *   <tr><th>层</th><th>按什么算</th><th>挡的是</th></tr>
 *   <tr><td>IP / 小时</td><td>客户端 IP</td><td>一台机器闷头撞号</td></tr>
 *   <tr><td>IP / 天</td><td>客户端 IP</td><td>卡着小时额度慢慢撞（10/小时 = 240/天）</td></tr>
 *   <tr><td>邮箱 / 天</td><td><b>被查的那个邮箱</b></td>
 *       <td>换一批 IP 接着撞同一个受害者——这一层和 IP 换不换无关</td></tr>
 * </table>
 *
 * <p>⚠ 邮箱那一层要用<b>规范化之后</b>（去空格、转小写）的值，否则
 * {@code Foo@x.com} 和 {@code foo@x.com} 各占一份额度，换个大小写就绕过去了。</p>
 */
@Component
public class OrderLookupRateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(OrderLookupRateLimiter.class);

    /** 和会话、询盘限流用同一个命名空间前缀，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:orderlookup:rate:";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 这一次查单还能不能放过去。
     *
     * <p><b>有副作用</b>：返回 {@code true} 的同时计数已经加上去了。所以一次请求只能调一次，
     * 而且要在查库之前调——放在后面的话，被限流的那次其实已经把单查出来了。</p>
     *
     * @param clientIp 真实客户端 IP。⚠ 反代必须转发 {@code X-Forwarded-For}，
     *                 否则所有访客都是 127.0.0.1，前两层退化成全站限流
     * @param email    <b>已经规范化</b>的下单邮箱（去空格、小写），见类注释最后一段
     * @return 允许查返回 true；超限或者 Redis 不可用返回 false
     */
    public boolean tryAcquire(String clientIp, String email)
    {
        String ip = clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();
        String mail = email == null ? "" : email.trim().toLowerCase();

        StationProperties.OrderLookup limits = properties.getOrderLookup();

        // ⚠ 三层都要走一遍，不能短路——短路的话后面那几层的计数永远加不上去，等于没有
        boolean hourOk = withinLimit("iph", ip, limits.getIpRatePerHour(), Duration.ofHours(1));
        boolean dayOk = withinLimit("ipd", ip, limits.getIpRatePerDay(), Duration.ofDays(1));
        boolean mailOk = withinLimit("mail", mail, limits.getEmailRatePerDay(), Duration.ofDays(1));

        if (!hourOk || !dayOk || !mailOk)
        {
            // ⚠ 日志里不写完整邮箱：这条日志的触发者可能正是那个受害者本人
            log.warn("查单限流：IP {} 被拒（IP 小时 {}，IP 天 {}，邮箱天 {}）", ip,
                    hourOk ? "通过" : "超限", dayOk ? "通过" : "超限", mailOk ? "通过" : "超限");
            return false;
        }
        return true;
    }

    /**
     * 一层固定窗口计数。
     *
     * <p>和 {@link InquiryRateLimiter} 的同名方法只有一处不同：<b>catch 里返回 false</b>。
     * 那一处就是这个类存在的全部理由，别顺手改成和它一样。</p>
     */
    private boolean withinLimit(String scope, String subject, int limit, Duration window)
    {
        if (limit <= 0)
        {
            // 配成 0 或负数 = 关掉这一层。⚠ 三层都关等于把订单详情开给任何人撞单号
            return true;
        }

        // 窗口序号进键名：窗口一换键就换，过期由 Redis 自己收
        long slot = System.currentTimeMillis() / window.toMillis();
        String key = KEY_PREFIX + scope + ":" + slot + ":" + subject;

        try
        {
            Long count = redis.opsForValue().increment(key);
            if (count == null)
            {
                // 命令回了个空——当成计数不可用，按类注释拒绝
                return false;
            }
            if (count == 1L)
            {
                // ⚠ 只在第一次设过期。每次都设的话，持续打请求会把过期时间一直往后推，
                //   这个键就永远不过期、计数永远不清零，正常买家被永久挡住
                redis.expire(key, window.multipliedBy(2));
            }
            return count <= limit;
        }
        catch (Exception e)
        {
            // 见类注释：这里和询盘限流相反，拿不到计数就拒绝
            log.error("查单限流计数失败（{}），本次拒绝：{}", key, e.getMessage());
            return false;
        }
    }
}
