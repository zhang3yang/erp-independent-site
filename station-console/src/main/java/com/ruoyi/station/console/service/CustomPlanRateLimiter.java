package com.ruoyi.station.console.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 买家 3D 定制方案保存（{@code POST /openapi/site/customize/plan}）的限流，2026-09-29。
 *
 * <h3>它挡的是什么</h3>
 * <p>这是<b>唯一一个能让匿名请求往对象存储写文件</b>的接口：一次最多 6 张图、每张 2MB。
 * 不限的话，一个脚本就能把桶写满、把流量费刷上去。</p>
 *
 * <h3>⚠ Redis 挂了要<b>拒绝</b>，和 {@link InquiryRateLimiter} 正好相反</h3>
 * <p>询盘那边「拿不到计数就放行」的理由是丢一条真询盘等于丢一个客户。这里放行的后果是
 * 不限量写 COS，代价不对称。拒绝的代价是 Redis 故障期间存不了方案——页面提示稍后再试，
 * 买家编辑的内容还在页面上，不会丢。和 {@link OrderLookupRateLimiter} 同一个取舍，
 * <b>别顺手改成和询盘一样</b>。</p>
 *
 * <h3>三层窗口</h3>
 * <ul>
 *   <li>IP / 小时、IP / 天：一台机器闷头刷；</li>
 *   <li><b>站点 / 天</b>：换一批 IP 也有个封顶，存储费用心里有数。</li>
 * </ul>
 */
@Component
public class CustomPlanRateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(CustomPlanRateLimiter.class);

    private static final String KEY_PREFIX = "station:customplan:rate:";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    /**
     * 这一次保存还能不能放过去。
     *
     * <p><b>有副作用</b>：返回 true 的同时计数已经加上了。一次请求只调一次，
     * 而且要在写 COS 之前调。</p>
     *
     * @return 允许返回 true；超限或 Redis 不可用返回 false
     */
    public boolean tryAcquire(String clientIp)
    {
        String ip = clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();
        StationProperties.CustomPlan limits = properties.getCustomPlan();

        // ⚠ 三层都要走一遍，不能短路——短路的话后面几层的计数永远加不上去
        boolean hourOk = withinLimit("iph", ip, limits.getIpRatePerHour(), Duration.ofHours(1));
        boolean dayOk = withinLimit("ipd", ip, limits.getIpRatePerDay(), Duration.ofDays(1));
        boolean siteOk = withinLimit("site", "all", limits.getSiteRatePerDay(), Duration.ofDays(1));

        if (!hourOk || !dayOk || !siteOk)
        {
            log.warn("定制方案限流：IP {} 被拒（IP 小时 {}，IP 天 {}，全站天 {}）", ip,
                    hourOk ? "通过" : "超限", dayOk ? "通过" : "超限", siteOk ? "通过" : "超限");
            return false;
        }
        return true;
    }

    /** 一层固定窗口计数。写法同 {@link OrderLookupRateLimiter}：catch 里返回 false */
    private boolean withinLimit(String scope, String subject, int limit, Duration window)
    {
        if (limit <= 0)
        {
            return true;
        }
        long slot = System.currentTimeMillis() / window.toMillis();
        String key = KEY_PREFIX + scope + ":" + slot + ":" + subject;
        try
        {
            Long count = redis.opsForValue().increment(key);
            if (count == null)
            {
                return false;
            }
            if (count == 1L)
            {
                // ⚠ 只在第一次设过期，理由见 OrderLookupRateLimiter 同名方法
                redis.expire(key, window.multipliedBy(2));
            }
            return count <= limit;
        }
        catch (Exception e)
        {
            // 见类注释：拿不到计数就拒绝
            log.error("定制方案限流计数失败（{}），本次拒绝：{}", key, e.getMessage());
            return false;
        }
    }
}
