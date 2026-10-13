package com.ruoyi.station.console.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.mapper.SitePageViewMapper;

/**
 * 浏览量的两个定时任务：回填 UV、清理过期明细。
 *
 * <h3>⚠ 依赖 {@code @EnableScheduling}</h3>
 * <p>那一行在 {@code StationConsoleApplication} 上。少了它这两个任务<b>什么都不会发生</b>，
 * 也不会报错——那个类的注释里专门写了这件事，加任何 {@code @Scheduled} 之前先确认它还在。</p>
 *
 * <h3>为什么 UV 要回填、不能实时累加</h3>
 * <p>PV 每来一次 {@code +1} 就行。UV 是 {@code COUNT(DISTINCT visitorid)}——
 * 要 {@code +1} 之前得先知道「这个访客今天来过没有」，那就是一次查询，
 * 每次上报多一次查库换一个当天就能看到的数字，不值得。</p>
 *
 * <p>所以：<b>今天的 UV 由管理台从明细现算</b>（{@code PageViewStatService}），
 * 昨天及以前的由这里回填进 {@code u_s_pv_daily.uv}。
 * 两个数据源，边界是「今天 / 以前」，别把这两条路混起来用。</p>
 */
@Component
public class PageViewMaintenance
{
    private static final Logger log = LoggerFactory.getLogger(PageViewMaintenance.class);

    /**
     * 一批删多少行。
     *
     * <p>5000 是在「批次别太多」和「单批别锁太久」之间取的。⚠ 别调到几十万：
     * 那一句 DELETE 会把这些行全部锁住并写进一个巨大的 undo log，
     * 期间前台的埋点写入全都等在那里——而表现是「深夜那几分钟浏览量为零」。</p>
     */
    private static final int DELETE_BATCH = 5000;

    /**
     * 一次任务最多删几批，也就是最多 {@code 5000 × 200 = 100 万}行。
     *
     * <p>⚠ 有上限是为了让这个任务<b>一定会结束</b>。没有上限的话，
     * 一旦保留期被调小（90 → 7），这个任务会试图在一次执行里删掉几个月的数据，
     * 把库压住几十分钟。分几个晚上删完完全可以接受。</p>
     */
    private static final int MAX_BATCHES = 200;

    @Autowired
    private SitePageViewMapper mapper;

    @Autowired
    private StationProperties properties;

    @Autowired
    private SiteInfoService siteInfoService;

    /**
     * 回填昨天的 UV。每天 01:50 东八区。
     *
     * <p>时间点是错开的：{@code RateFetcher} 那两个任务在 01:30，
     * 这个在 01:50，清理在 03:20。都挤在同一分钟的话，
     * 那一刻的慢查询日志里会看不出是谁引起的。</p>
     *
     * <p>⚠ 只回填<b>昨天</b>一天。前天没回填成（服务当时没在跑）就永远是 0 了——
     * 这是有意的取舍：补历史要遍历「哪几天缺」，而缺一天 UV 的代价，
     * 远小于为它写一套补数逻辑并且维护它。真要补就手工跑那两句 UPDATE，
     * SQL 在 {@code SitePageViewMapper.xml} 里。</p>
     */
    @Scheduled(cron = "0 50 1 * * *", zone = "Asia/Shanghai")
    public void backfillYesterdayUv()
    {
        if (!properties.getPageview().isEnabled())
        {
            return;
        }
        try
        {
            String siteCode = siteInfoService.require().getSiteCode();
            LocalDate day = LocalDate.now(PageViewService.ZONE).minusDays(1);
            // ⚠ 半开区间 [当天00:00, 次日00:00)。用 between 会把次日零点整那一毫秒
            //   算进昨天，同时它在今天那一批里也算一次——同一条明细被数两遍
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.plusDays(1).atStartOfDay();

            int dims = mapper.backfillDimensionUv(siteCode, day, from, to);
            int total = mapper.backfillTotalUv(siteCode, day, from, to);
            log.info("浏览量 UV 回填完成：{}，维度行 {} 行，合计行 {} 行", day, dims, total);
        }
        catch (Exception e)
        {
            // ⚠ 不往上抛：Spring 的调度器碰到异常会跳过这次执行，但如果异常一直抛，
            //   日志里只有一堆栈，看不出「哪一天没回填」。这里把日期和原因写清楚
            log.warn("浏览量 UV 回填失败，昨天那一行的 uv 会停在 0：{}", e.getMessage());
        }
    }

    /**
     * 清理过期明细。每天 03:20 东八区。
     *
     * <p>⚠ 只删 {@code u_s_pageview}，<b>永远不动</b> {@code u_s_pv_daily}——
     * 那张表是 90 天之后唯一还剩下的历史，而且它很小（一天几十行）。</p>
     *
     * <p>⚠ 顺序上必须在 UV 回填之后：先删明细再回填的话，
     * 回填拿到的是被删剩下的数据，昨天的 UV 会偏小。
     * 默认保留 90 天的情况下这两件事不会撞上（回填只看昨天），
     * 但保留期被调到 1 天时就会——所以时间点上也拉开了 90 分钟。</p>
     */
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
    public void cleanExpired()
    {
        if (!properties.getPageview().isEnabled())
        {
            return;
        }
        int retainDays = properties.getPageview().getRetainDays();
        if (retainDays <= 0)
        {
            // 配成 0 = 不清理。生产上别这么配，见 StationProperties 里那段注释
            log.info("浏览明细清理已关闭（station.pageview.retain-days={}）", retainDays);
            return;
        }

        LocalDateTime before = LocalDate.now(PageViewService.ZONE)
                .minusDays(retainDays).atStartOfDay();
        long deleted = 0;
        try
        {
            for (int batch = 0; batch < MAX_BATCHES; batch++)
            {
                int rows = mapper.deleteBefore(before, DELETE_BATCH);
                deleted += rows;
                if (rows < DELETE_BATCH)
                {
                    // 没删满一批就是删完了
                    break;
                }
            }
            if (deleted > 0)
            {
                log.info("浏览明细清理完成：删掉 {} 行（{} 之前，保留 {} 天）",
                        deleted, before, retainDays);
            }
            if (deleted >= (long) DELETE_BATCH * MAX_BATCHES)
            {
                // 撞到了批次上限，说明还有没删完的。这不是错误，但要说出来——
                // 否则「表一直在长」会看起来像清理没生效
                log.warn("浏览明细清理撞到批次上限（{} 行），还有更老的数据没删完，"
                        + "明晚会接着删。如果这条日志天天出现，说明写入量已经超过清理速度",
                        deleted);
            }
        }
        catch (Exception e)
        {
            log.warn("浏览明细清理失败，已删 {} 行：{}", deleted, e.getMessage());
        }
    }
}
