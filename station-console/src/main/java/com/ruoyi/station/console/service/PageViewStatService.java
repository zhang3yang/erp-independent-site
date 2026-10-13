package com.ruoyi.station.console.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.mapper.SitePageViewMapper;

/**
 * 管理台那一侧的浏览量查询。喂 {@code GET /api/stat/pv}。
 *
 * <h3>这个服务要回答的问题</h3>
 * <p>不是「有多少人来」——那个问题接 GA4 之类的第三方统计答得更好（地域、
 * 搜索词、停留时长、渠道识别，自己写要写很久还写不全）。</p>
 *
 * <p>这里回答的是<b>第三方答不了的那个</b>：「这个商品被看了 200 次却一个询盘都没有，
 * 是不是价格有问题」。因为它需要把浏览量和 {@code u_s_inquiry}、{@code u_s_order}、
 * {@code u_s_sitegoods.price} 放在<b>同一句 SQL</b> 里，而第三方统计的数据在别人的
 * 服务器上，只能两边各导一份表格再手工拼。</p>
 *
 * <p>所以 {@link #topGoods} 那部分是这个功能的核心，趋势和来源分布是附带的。</p>
 *
 * <h3>⚠ 今天 / 以前，两个数据源</h3>
 * <p>PV 一律读聚合表（实时累加的，今天的也准）。<b>UV 分两条路</b>：
 * 今天的从明细现算，昨天及以前的读 {@code u_s_pv_daily.uv}（凌晨回填）。
 * 原因见 {@link PageViewMaintenance} 的类注释。</p>
 *
 * <p>这个类是那条边界<b>唯一</b>的落点——别在别处再写一遍「今天的 UV 怎么算」。</p>
 */
@Service
public class PageViewStatService
{
    /** 趋势图默认看多少天 */
    private static final int TREND_DAYS = 14;

    /** 商品排行默认看多少天。30 天是一个采购周期，比 7 天稳、比 90 天灵敏 */
    private static final int TOP_DAYS = 30;

    /** 商品排行取前几名 */
    private static final int TOP_LIMIT = 20;

    /** 来源分布取前几名 */
    private static final int REFERRER_LIMIT = 15;

    /** 国家分布取前几名。20 个够看出主力市场，再多就是长尾噪音 */
    private static final int COUNTRY_LIMIT = 20;

    @Autowired
    private SitePageViewMapper mapper;

    @Autowired
    private StationProperties properties;

    @Autowired
    private SiteInfoService siteInfoService;

    /**
     * 概览页要的一整包数据。一次请求全给，不让前端发五个请求。
     *
     * <p>返回的键：{@code enabled / today / yesterday / trend / topGoods /
     * typeBreakdown / referrers / topDays / retainDays}。</p>
     */
    public Map<String, Object> overview()
    {
        Map<String, Object> result = new LinkedHashMap<>();

        // ⚠ 统计关掉时也要回一个完整的结构，只是 enabled=false。
        //   回 null 或者报错的话，管理台那个卡片会显示成「加载失败」，
        //   而它其实是被有意关掉的——这两种状态必须能分开
        boolean enabled = properties.getPageview().isEnabled();
        result.put("enabled", enabled);
        int retainDays = properties.getPageview().getRetainDays();
        result.put("retainDays", retainDays);
        if (!enabled)
        {
            result.put("today", zeroDay(LocalDate.now(PageViewService.ZONE)));
            result.put("yesterday", zeroDay(LocalDate.now(PageViewService.ZONE).minusDays(1)));
            result.put("trend", List.of());
            result.put("topGoods", List.of());
            result.put("typeBreakdown", List.of());
            result.put("referrers", List.of());
            result.put("devices", List.of());
            result.put("browsers", List.of());
            result.put("countries", List.of());
            result.put("engagement", Map.of("sessions", 0, "views", 0, "bounces", 0,
                    "sessionMinutes", properties.getPageview().getSessionMinutes()));
            result.put("topDays", TOP_DAYS);
            return result;
        }

        String siteCode = siteInfoService.require().getSiteCode();
        LocalDate today = LocalDate.now(PageViewService.ZONE);
        LocalDate yesterday = today.minusDays(1);

        result.put("today", dayStat(siteCode, today, true));
        result.put("yesterday", dayStat(siteCode, yesterday, false));
        result.put("trend", trend(siteCode, today));

        // ⚠ 窗口压到保留期以内。排行里的 pv 来自聚合表（永久保留）、
        //   uv 来自明细（只留 retainDays 天）：窗口超过保留期时 pv 是全的、
        //   uv 少一截，两个数字并排放着看起来只像「回头客特别多」，不像数据缺了
        int topDays = retainDays > 0 ? Math.min(TOP_DAYS, retainDays) : TOP_DAYS;
        result.put("topDays", topDays);
        LocalDate topFrom = today.minusDays(topDays - 1L);
        result.put("topGoods", mapper.selectTopGoods(siteCode, topFrom,
                topFrom.atStartOfDay(), TOP_LIMIT));
        result.put("typeBreakdown", mapper.selectTypeBreakdown(siteCode, topFrom));

        LocalDateTime topFromTime = topFrom.atStartOfDay();
        result.put("referrers", mapper.selectReferrers(siteCode, topFromTime, REFERRER_LIMIT));
        result.put("devices", mapper.selectDeviceBreakdown(siteCode, topFromTime));
        result.put("browsers", mapper.selectBrowserBreakdown(siteCode, topFromTime));
        result.put("countries", mapper.selectCountryBreakdown(siteCode, topFromTime, COUNTRY_LIMIT));
        result.put("engagement", engagement(siteCode, topFromTime));
        return result;
    }

    /**
     * 来访质量：几次来访、页均浏览、跳出率。
     *
     * <p>⚠ 口径在 {@link SitePageViewMapper#selectEngagement} 的注释里，
     * 两条要紧的：只统计已经结束的来访（否则跳出率虚高）、排除 {@code sessionid}
     * 为空的行（否则页均虚高）。</p>
     *
     * <p>「已经结束」的界线是<b>一个会话超时之前</b>——和 {@code SITE_SESSION}
     * Cookie 的 Max-Age 用同一个配置值。两处必须是同一个数：这里用 30 分钟而
     * Cookie 是 60 分钟的话，会有一批「其实还在浏览」的来访被当成结束的算进去。</p>
     */
    private Map<String, Object> engagement(String siteCode, LocalDateTime from)
    {
        int minutes = properties.getPageview().getSessionMinutes();
        LocalDateTime endedBefore = LocalDateTime.now(PageViewService.ZONE)
                .minusMinutes(minutes);

        Map<String, Object> row = mapper.selectEngagement(siteCode, from, endedBefore);
        int sessions = row == null ? 0 : toInt(row.get("sessions"));
        int views = row == null ? 0 : toInt(row.get("views"));
        int bounces = row == null ? 0 : toInt(row.get("bounces"));

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("sessions", sessions);
        stat.put("views", views);
        stat.put("bounces", bounces);
        stat.put("sessionMinutes", minutes);
        // ⚠ 分母为 0 时回 null，不回 0。「没有数据」和「跳出率是 0%」是两件事，
        //   而 0% 是个极不寻常的好数字——报表上显示成 0% 会让人以为统计坏了。
        //   前端看到 null 显示成「—」
        if (sessions > 0)
        {
            stat.put("pagesPerSession", Math.round((double) views / sessions * 10) / 10.0);
            stat.put("bounceRate", Math.round((double) bounces / sessions * 1000) / 10.0);
        }
        else
        {
            stat.put("pagesPerSession", null);
            stat.put("bounceRate", null);
        }
        return stat;
    }

    /**
     * 某一天的 PV / UV。
     *
     * @param live true = 这一天是今天，UV 从明细现算。⚠ 对历史某天传 true 也能跑，
     *             但那是白扫一遍明细去得到一个聚合表里已经有的数字
     */
    private Map<String, Object> dayStat(String siteCode, LocalDate day, boolean live)
    {
        Map<String, Object> row = mapper.selectTotalOfDate(siteCode, day);
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("date", day.toString());
        stat.put("pv", row == null ? 0 : toInt(row.get("pv")));
        stat.put("pvHuman", row == null ? 0 : toInt(row.get("pvhuman")));
        if (live)
        {
            // 见类注释：今天的 UV 聚合表里还是 0，只能现算
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.plusDays(1).atStartOfDay();
            stat.put("uv", mapper.countVisitorsBetween(siteCode, from, to));
        }
        else
        {
            stat.put("uv", row == null ? 0 : toInt(row.get("uv")));
        }
        return stat;
    }

    /**
     * 最近 {@value #TREND_DAYS} 天的趋势，一天一行。
     *
     * <p>⚠ <b>没有数据的日子要补零</b>，不能只回库里有的那几行。不补的话折线图会把
     * 「周末没人来」画成一条直接连过去的斜线——那看起来像「量在稳步上升」，
     * 而实际是中间两天为零。这类图上的错觉比数字上的错更难被发现。</p>
     */
    private List<Map<String, Object>> trend(String siteCode, LocalDate today)
    {
        LocalDate from = today.minusDays(TREND_DAYS - 1L);
        List<Map<String, Object>> rows = mapper.selectTrend(siteCode, from, today);

        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            byDate.put(String.valueOf(row.get("statdate")), row);
        }

        // 今天的 UV 现算一次，下面循环里用
        int todayUv = mapper.countVisitorsBetween(siteCode, today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());

        List<Map<String, Object>> trend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++)
        {
            LocalDate day = from.plusDays(i);
            Map<String, Object> row = byDate.get(day.toString());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("pv", row == null ? 0 : toInt(row.get("pv")));
            item.put("pvHuman", row == null ? 0 : toInt(row.get("pvhuman")));
            item.put("uv", day.equals(today) ? todayUv : (row == null ? 0 : toInt(row.get("uv"))));
            trend.add(item);
        }
        return trend;
    }

    private Map<String, Object> zeroDay(LocalDate day)
    {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("date", day.toString());
        stat.put("pv", 0);
        stat.put("pvHuman", 0);
        stat.put("uv", 0);
        return stat;
    }

    /**
     * ⚠ 聚合查询回来的数字类型不确定：{@code SUM()} 在 MySQL 里是 DECIMAL，
     * 到 JDBC 是 {@code BigDecimal}；普通的 int 列是 {@code Integer}；
     * {@code COUNT()} 是 {@code Long}。直接强转成 Integer 会在某一个查询上
     * 抛 ClassCastException，而另外几个一直是好的——这种错很难联想到类型。
     */
    private static int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }
}
