package com.ruoyi.station.console.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 浏览量的读写。{@code u_s_pageview}（明细）+ {@code u_s_pv_daily}（日聚合）。
 *
 * <p>表结构和设计决定见 {@code sql/station/migration/2026-09-08-pageview.sql} 的文件头。
 * SQL 在 {@code resources/mapper/SitePageViewMapper.xml} 里，那个文件的注释里有
 * 几条关于「哪个数字能加、哪个不能加」的硬规矩，写查询之前必须读。</p>
 */
public interface SitePageViewMapper
{
    // ==================== 写入 ====================

    /** 插一条明细。⚠ 由 PageViewService 的异步线程调，不在请求线程里 */
    int insertPageView(com.ruoyi.station.console.domain.SitePageView pageView);

    /**
     * 日聚合累加一次（{@code pv + 1}，真人再 {@code pvhuman + 1}）。
     *
     * @param human 1 = 不是爬虫。⚠ 传 0 时只加 pv 不加 pvhuman，
     *              这是「爬虫的行也入库、只在统计时排除」那条规矩在聚合层的落点
     */
    int upsertDaily(@Param("siteCode") String siteCode,
                    @Param("statDate") LocalDate statDate,
                    @Param("pageType") String pageType,
                    @Param("goodsCode") String goodsCode,
                    @Param("human") int human);

    // ==================== 定时任务 ====================

    /**
     * 删掉 {@code before} 之前的明细，一次最多 {@code limit} 行。
     *
     * <p>⚠ 分批是必须的，不是优化：90 天的明细可能是几十万行，
     * 一句 {@code DELETE} 会把这些行全部锁住并写进一个巨大的 undo log，
     * 期间前台的埋点写入会等在那里。</p>
     *
     * @return 实际删掉几行。调用方靠它判断要不要再来一批
     */
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 回填某一天各维度的 UV（{@code pagetype} + {@code goodscode} 那一层）。
     *
     * <p>⚠ 回填出来的这些 {@code uv} <b>不能跨行相加</b>。理由见 XML 里那段注释。</p>
     */
    int backfillDimensionUv(@Param("siteCode") String siteCode,
                            @Param("statDate") LocalDate statDate,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /** 回填某一天的全站 UV（{@code pagetype='_all'} 那一行） */
    int backfillTotalUv(@Param("siteCode") String siteCode,
                        @Param("statDate") LocalDate statDate,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    // ==================== 管理台查询 ====================

    /**
     * 最近若干天的全站趋势，一天一行。读的是 {@code pagetype='_all'} 那些行。
     *
     * <p>返回的每个 Map：{@code statdate / pv / pvhuman / uv}。
     * ⚠ 今天那一行的 {@code uv} 是 0（次日凌晨才回填），调用方要自己用
     * {@link #countVisitorsBetween} 现算的值替掉它。</p>
     */
    List<Map<String, Object>> selectTrend(@Param("siteCode") String siteCode,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    /** 某个时间段里的独立访客数（排除爬虫）。今天的 UV 靠它现算 */
    int countVisitorsBetween(@Param("siteCode") String siteCode,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    /** 某一天的全站合计（{@code pagetype='_all'} 那一行）。取不到返回 null */
    Map<String, Object> selectTotalOfDate(@Param("siteCode") String siteCode,
                                          @Param("statDate") LocalDate statDate);

    /**
     * 页面类型分布。⚠ 排除 {@code _all}——不排除的话总数正好翻倍。
     */
    List<Map<String, Object>> selectTypeBreakdown(@Param("siteCode") String siteCode,
                                                  @Param("fromDate") LocalDate fromDate);

    /**
     * <b>商品排行，这是整个功能的核心查询。</b>
     *
     * <p>它把浏览量和询盘数、成单数、标价放在同一行里——这正是接第三方统计
     * （GA4 之类）做不到的事：那边的浏览数据在别人的服务器上，
     * 没法和这个库里的 {@code u_s_inquiry} / {@code u_s_order} join。</p>
     *
     * <p>回答的问题是「这个商品被看了 200 次却一个询盘都没有，是不是价格有问题」。</p>
     *
     * <p>返回的每个 Map：{@code goodscode / goodsname / price / publishstatus /
     * sitecategory / pv / uv / inquirycount / ordercount}。</p>
     *
     * @param days 统计窗口天数。⚠ <b>调用方必须把它压到 retainDays 以内</b>——
     *             {@code uv} 是从明细现算的，而明细只留 90 天，
     *             窗口超过保留期时 {@code pv} 是全的、{@code uv} 却少了一截，
     *             两个数字并排放着，看起来只像「回头客特别多」
     */
    List<Map<String, Object>> selectTopGoods(@Param("siteCode") String siteCode,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("fromTime") LocalDateTime fromTime,
                                             @Param("limit") int limit);

    /**
     * 来源分布。按 referrer 的域名归类，最近若干天。
     *
     * <p>⚠ 这个查询扫的是<b>明细</b>（referrer 没进日聚合——它的取值几乎无穷多，
     * 进聚合表会让那张表爆掉）。所以它只能查保留期内的数据。</p>
     */
    List<Map<String, Object>> selectReferrers(@Param("siteCode") String siteCode,
                                              @Param("from") LocalDateTime from,
                                              @Param("limit") int limit);

    /**
     * 设备分布：手机 / 平板 / 桌面，各多少浏览、多少访客。
     *
     * <p>⚠ 同样扫明细（{@code device} 没进日聚合），只能查保留期以内。</p>
     *
     * <p>返回：{@code device / pv / uv}。</p>
     */
    List<Map<String, Object>> selectDeviceBreakdown(@Param("siteCode") String siteCode,
                                                    @Param("from") LocalDateTime from);

    /** 浏览器分布。返回 {@code browser / pv / uv} */
    List<Map<String, Object>> selectBrowserBreakdown(@Param("siteCode") String siteCode,
                                                     @Param("from") LocalDateTime from);

    /** 国家分布。返回 {@code country / pv / uv}。⚠ 同样扫明细，只能查保留期以内 */
    List<Map<String, Object>> selectCountryBreakdown(@Param("siteCode") String siteCode,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("limit") int limit);

    /**
     * 查一个 IP 属于哪个国家。查不到返回 null。
     *
     * <p>数据在 {@code u_s_ip_country}，离线表，不调任何外部服务
     * （见 {@code sql/station/migration/2026-09-09-ip-country.sql} 的文件头）。</p>
     *
     * @param ipVer 4 或 6。⚠ <b>必须传对</b>：那张表里 IPv4 存 4 字节、IPv6 存 16 字节，
     *              两种长度混在一列里比大小是无意义的，而且不会报错
     * @param ip    完整 IP 字符串。畸形输入时 {@code INET6_ATON} 返回 NULL，
     *              查询自然查不到，安全
     */
    String selectCountryByIp(@Param("ipVer") int ipVer, @Param("ip") String ip);

    /**
     * 来访质量：一共几次来访、平均每次看几页、看一页就走的比例。
     *
     * <p>返回一行：{@code sessions / views / bounces}。跳出率和页均由调用方算——
     * 除法放在 Java 里，SQL 只出三个整数（SQL 里做除法要处理分母为 0，
     * 而且返回类型会变成 DECIMAL，前端拿到 {@code "1.0000"} 这种字符串）。</p>
     *
     * <h3>⚠ 两个必须知道的口径</h3>
     * <ul>
     *   <li><b>只统计已经结束的来访。</b>判据是「最后一次浏览距今超过一个会话超时」。
     *       不排除进行中的来访，跳出率会虚高——一个十秒钟前刚落地、正在看第一页的人，
     *       在数据里和「看一眼就走了」长得完全一样。这个偏差在流量小的时候特别明显。</li>
     *   <li><b>{@code sessionid} 为空的行整个排除。</b>它可以为空（Cookie 没回来，
     *       比如浏览器禁了 Cookie）。不排除的话 {@code COUNT(DISTINCT sessionid)} 会
     *       忽略 NULL、而 {@code COUNT(*)} 不会——页均浏览数直接被抬高，
     *       而这个数字看起来只像「访客很投入」。</li>
     * </ul>
     */
    Map<String, Object> selectEngagement(@Param("siteCode") String siteCode,
                                         @Param("from") LocalDateTime from,
                                         @Param("endedBefore") LocalDateTime endedBefore);
}
