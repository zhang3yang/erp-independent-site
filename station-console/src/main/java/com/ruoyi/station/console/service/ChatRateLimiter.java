package com.ruoyi.station.console.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 聊天发消息的限流。按 IP，两层固定窗口（分钟 + 天）。
 *
 * <h3>和 {@link InquiryRateLimiter} 是两套，值差一个数量级</h3>
 * <p>询盘是 5 条/分钟——填一张询价单要几十秒，5 条绰绰有余。<b>聊天不是</b>：
 * 一个着急的买家连发「在吗」「多少钱」「能发货吗」就是三条，套询盘那个值会把
 * 正常对话卡死。所以这里默认 20 条/分钟、500 条/天。</p>
 *
 * <p>键前缀也分开（{@code station:chat:rate:}）：和询盘共用一个计数器的话，
 * 聊几句就把当天的询盘额度用光了，而那个故障从现象上完全看不出原因。</p>
 *
 * <h3>⚠ Redis 挂了放行</h3>
 * <p>和 {@code InquiryRateLimiter} 同一个选择，理由也一样：这一层拦的是垃圾消息，
 * 拿不准时放行的代价是几条垃圾，而拒绝的代价是<b>所有真实买家都发不出话</b>。
 * 注意这和 {@code BuyerSession} 那边「拿不准就当未登录」是<b>相反</b>的选择——
 * 那边是认证，认证上拿不准必须拒绝。两处不一样是有意的。</p>
 */
@Component
public class ChatRateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);

    /** 和会话、jti、询盘限流用同一个命名空间风格，运维在 Redis 里一眼能看出是谁的键 */
    private static final String KEY_PREFIX = "station:chat:rate:";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 这个 IP 还能不能再发。
     *
     * <p><b>有副作用</b>：返回 {@code true} 的同时已经把计数加上去了。
     * 所以每条消息只能调一次，而且要在真正落库<b>之前</b>调——
     * 放在后面的话，被限流的那条已经写进库了，限流就只剩一个假象。</p>
     *
     * @param clientIp 真实客户端 IP。⚠ 反代必须转发 {@code X-Forwarded-For}，
     *                 否则所有访客都是 127.0.0.1，这里就退化成全站限流
     */
    public boolean tryAcquire(String clientIp)
    {
        String ip = clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();

        // ⚠ 两层都要走一遍，不能因为分钟窗口过了就跳过日窗口——
        //   跳过的话日计数永远加不上去，那一层等于没有
        boolean minuteOk = withinLimit(ip, "m", properties.getChat().getRatePerMinute(),
                Duration.ofMinutes(1));
        boolean dayOk = withinLimit(ip, "d", properties.getChat().getRatePerDay(),
                Duration.ofDays(1));

        if (!minuteOk || !dayOk)
        {
            log.warn("聊天限流：IP {} 被拒（分钟窗口 {}，日窗口 {}）", ip,
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
                //   这个键就永远不过期——计数也就永远不清零，正常访客被永久挡住
                redis.expire(key, window.multipliedBy(2));
            }
            return count <= limit;
        }
        catch (Exception e)
        {
            // 见类注释：这一层拿不准就放行
            log.warn("聊天限流读 Redis 失败，本次放行：{}", e.getMessage());
            return true;
        }
    }
}
