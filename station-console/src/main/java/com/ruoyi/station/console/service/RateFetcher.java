package com.ruoyi.station.console.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.ruoyi.station.console.mapper.SiteRateMapper;

/**
 * 每天拉一次汇率，写进 {@code u_s_rate}。
 *
 * <h3>汇率源</h3>
 * <p>{@code https://open.er-api.com/v6/latest/USD}——免费、不要 API key、166 个币种、
 * 每天 UTC 00:0x 更新一次。<b>2026-09-08 在部署机 49.234.194.194 上实测可达，818ms。</b>
 * 那台机器出网受限（GitHub 和一批镜像都不通），所以换源之前必须先在那台机器上实测，
 * 别照着「这个 API 很有名」就换。同样测通的备选：{@code api.frankfurter.app}（ECB 数据）、
 * {@code api.exchangerate-api.com/v4}。</p>
 *
 * <h3>失败了会怎样</h3>
 * <p><b>不抛异常。</b>写一条失败状态就返回，{@code ratesjson} 保持上一次的值。
 * 一个免费汇率接口超时，不该让任何东西停下来——页面上有一份三天前的汇率，
 * 远好过没有价格。</p>
 *
 * <h3>为什么启动时那次是延迟的</h3>
 * <p>用 {@code initialDelay} 而不是 {@code @PostConstruct}：后者会把一次公网请求
 * 塞进启动路径，API 慢的时候整个服务起不来，而症状是「服务启动超时」，
 * 没人会想到是汇率。</p>
 */
@Component
public class RateFetcher
{
    private static final Logger log = LoggerFactory.getLogger(RateFetcher.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 只用 JDK 自带的 HttpClient。
     *
     * <p>⚠ 不要为这个类引任何 HTTP 库：{@code pom.xml} 顶部那道闸明写「OkHttp 一个都不引」，
     * {@code CosClient} 也是这么写的。这里跟着走。</p>
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 整体超时。一个几 KB 的 JSON，10 秒还没回来就是不通了，没必要等更久 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Value("${station.rate.url:https://open.er-api.com/v6/latest/USD}")
    private String rateUrl;

    /** 关掉之后不再拉取，库里已有的汇率继续用。给「这个站不需要多币种」的部署留的开关 */
    @Value("${station.rate.enabled:true}")
    private boolean enabled;

    @Autowired
    private SiteRateMapper siteRateMapper;

    @Autowired
    private RateService rateService;

    /**
     * 启动后 30 秒拉一次，之后每 24 小时一次。
     *
     * <p>30 秒是为了让数据源、Redis 这些先就位——启动瞬间发请求，失败了会在日志最显眼
     * 的位置留一条误导性的错误。</p>
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 24L * 60 * 60 * 1000)
    public void fetchOnStartupAndDaily()
    {
        fetch("定时");
    }

    /**
     * 每天 01:30 再确认一次。
     *
     * <p>和上面那个 {@code fixedDelay} 不重复：{@code fixedDelay} 是「距上次结束满 24 小时」，
     * 服务重启一次时间点就漂了，可能长期卡在 API 刚好没更新的那个时刻。这一条是绝对时间，
     * 汇率源 UTC 00:0x 更新，北京时间 01:30 拉留足了余量。</p>
     */
    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Shanghai")
    public void fetchDaily()
    {
        fetch("每日");
    }

    /** 管理台点「立即刷新」。返回是否成功，让运营当场看到结果而不是去翻日志 */
    public boolean fetchNow()
    {
        return fetch("手动");
    }

    private boolean fetch(String trigger)
    {
        if (!enabled)
        {
            log.debug("汇率拉取已关闭（station.rate.enabled=false），跳过");
            return false;
        }
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rateUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                return fail(trigger, "HTTP " + response.statusCode());
            }

            Map<String, Object> body = JSON.readValue(response.body(), Map.class);
            Object rates = body.get("rates");
            if (!(rates instanceof Map) || ((Map<?, ?>) rates).isEmpty())
            {
                // API 返回 200 但内容不对（限流页、改版、账号过期）。这种失败最阴——
                // 不写这一条判断的话，会把一个空表当成成功写进库，页面上所有币种消失，
                // 而日志里一条错误都没有。
                return fail(trigger, "返回里没有 rates，或 rates 是空的");
            }

            String baseCode = String.valueOf(body.getOrDefault("base_code", "USD"));
            Map<String, Object> clean = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rates).entrySet())
            {
                clean.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            siteRateMapper.updateFetched(baseCode, JSON.writeValueAsString(clean));
            rateService.evict();
            log.info("汇率已更新（{}）：基准 {}，{} 个币种，来源 {}",
                    trigger, baseCode, clean.size(), rateUrl);
            return true;
        }
        catch (Exception e)
        {
            return fail(trigger, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 记一条失败。
     *
     * <p>只写状态和原因，{@code ratesjson} 与 {@code fetchtime} 都不动——见
     * {@code SiteRateMapper.updateFetchFailed} 上的注释。</p>
     *
     * <p>连写库都失败了就只记日志：这个方法在任何情况下都不能抛，
     * 它的调用点是定时任务，抛出去只会在日志里多一条堆栈，什么也修不了。</p>
     */
    private boolean fail(String trigger, String reason)
    {
        log.warn("汇率拉取失败（{}）：{}。继续用库里上一次的汇率，管理台可见此状态",
                trigger, reason);
        try
        {
            String trimmed = reason == null ? "" : reason;
            siteRateMapper.updateFetchFailed(
                    trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed);
            rateService.evict();
        }
        catch (Exception e)
        {
            log.error("汇率拉取失败后连状态都没写进去：{}", e.getMessage());
        }
        return false;
    }
}
