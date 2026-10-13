package com.ruoyi.station.console.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 浏览量上报的按 IP 限流。按 {@link InquiryRateLimiter} 那一套：两层固定窗口（分钟 + 天）。
 *
 * <h3>为什么需要它</h3>
 * <p>{@code POST /openapi/site/pv} 是<b>第三个对公网开放的写接口</b>，而且是三个里
 * 门槛最低的一个：没有蜜罐、没有必填字段、请求体就五个可选值。
 * 不限流的话，任何人写四行脚本就能往 {@code u_s_pageview} 里灌几百万行。</p>
 *
 * <p>后果和询盘那边不一样，但一样实在：</p>
 * <ul>
 *   <li>报表<b>直接失真</b>——「哪个商品被看得多」是拿来做定价和曝光决策的，
 *       被人刷过的数字比没有数字更糟，因为它看起来是真的；</li>
 *   <li>这张表本来就是全库写入最频繁的，灌满之后磁盘和备份都受影响，
 *       而这台机器<b>没有数据库备份</b>；</li>
 *   <li>{@code u_s_pv_daily} 的 upsert 会在同一批主键上反复更新，
 *       高并发下那几行的行锁会成为瓶颈——影响的是整个前台的埋点，不只是刷的人。</li>
 * </ul>
 *
 * <h3>⚠ 值比另外两个限流器宽得多，这是有意的</h3>
 * <p>询盘 5 条/分钟、聊天 20 条/分钟，这里默认 <b>60 条/分钟</b>。
 * 因为这三个接口对应的行为完全不是一回事：</p>
 * <ul>
 *   <li>询盘和聊天是<b>主动操作</b>，真人一分钟做不了几次；</li>
 *   <li>浏览上报是<b>被动跟随</b>——访客点开一个页面就有一条。一个认真看货的买家
 *       一分钟点开七八个商品、来回切分类，三四十条很正常。
 *       套聊天那个 20 的值会把这种人的后半段浏览全部丢掉，
 *       而表现是「看得越认真的客户，记录反而越少」——这个偏差方向最要命。</li>
 * </ul>
 *
 * <h3>⚠ 被限流时接口<b>照样回 204</b>，不回 429</h3>
 * <p>和询盘那边相反。询盘要如实告诉真人「过一会儿再试」，因为他需要知道自己那条
 * 没发出去。浏览上报没有这个需要——访客根本不知道有这回事，
 * 而回 429 只会做两件事：在正常访客的浏览器控制台里刷红字，
 * 以及<b>告诉刷量的人限额在哪</b>。所以一律 204，拒绝只留在服务端日志里。</p>
 *
 * <h3>⚠ Redis 挂了要放行</h3>
 * <p>和另外两个限流器同一个选择。这里的理由甚至更轻：拿不准时多记几条浏览毫无代价，
 * 而拦住等于整站统计停摆。</p>
 */
@Component
public class PageViewRateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(PageViewRateLimiter.class);

    /** 和会话、jti、询盘、聊天限流用同一个命名空间风格，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:pv:rate:";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 这个 IP 还能不能再上报。
     *
     * <p><b>有副作用</b>：返回 {@code true} 的同时已经把计数加上去了。
     * 所以每次上报只能调一次，而且要在入队<b>之前</b>调。</p>
     *
     * @param clientIp 真实客户端 IP。⚠ 这里要的是<b>完整</b> IP，不是落库那个截断过的——
     *                 截断之后一个 C 段的 254 台机器会挤在同一个额度里
     * @return 允许上报返回 true；超了返回 false
     */
    public boolean tryAcquire(String clientIp)
    {
        String ip = clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();

        // ⚠ 两层都要走一遍，不能因为分钟窗口过了就跳过日窗口——
        //   跳过的话日计数永远加不上去，那一层等于没有
        boolean minuteOk = withinLimit(ip, "m", properties.getPageview().getRatePerMinute(),
                Duration.ofMinutes(1));
        boolean dayOk = withinLimit(ip, "d", properties.getPageview().getRatePerDay(),
                Duration.ofDays(1));

        if (!minuteOk || !dayOk)
        {
            // ⚠ 这条日志里是**完整** IP，库里那一列是截断过的。排查刷量只能靠这里，
            //   所以别把它也截掉——见 migration 脚本文件头关于 clientip 的那段
            log.warn("浏览上报限流：IP {} 被拒（分钟窗口 {}，日窗口 {}）", ip,
                    minuteOk ? "通过" : "超限", dayOk ? "通过" : "超限");
            return false;
        }
        return true;
    }

    /**
     * 一层固定窗口计数。写法和 {@code InquiryRateLimiter} 一致，见那里的注释：
     * 固定窗口在交界处能挤进两倍的量，这个场景里不值得为那个上滑动窗口。
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
                redis.expire(key, window.multipliedBy(2));
            }
            return count <= limit;
        }
        catch (Exception e)
        {
            // 见类注释：拿不到计数就放行。多记几条浏览没有代价，拦住等于统计停摆
            log.warn("浏览上报限流计数失败（{}），本次放行：{}", key, e.getMessage());
            return true;
        }
    }
}
