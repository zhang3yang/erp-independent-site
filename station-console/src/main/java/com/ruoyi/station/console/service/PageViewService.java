package com.ruoyi.station.console.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SitePageView;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;
import com.ruoyi.station.console.mapper.SitePageViewMapper;

/**
 * 收前台的浏览上报，落 {@code u_s_pageview} + 累加 {@code u_s_pv_daily}。
 *
 * <p>表结构和全部设计决定在 {@code sql/station/migration/2026-09-08-pageview.sql}
 * 的文件头，改这个类之前先读它。</p>
 *
 * <h3>⚠ 为什么不在现有的取数接口里顺手记一笔</h3>
 * <p>最省事的做法看起来是在 {@code OpenApiController.productDetail} 里加一行。<b>不行</b>，
 * 而且失败得很隐蔽——前台是 SSR：首屏那次「取商品详情」是 <b>Nuxt 服务端</b>
 * 直接打 {@code 127.0.0.1:8081} 的（{@code useSite.ts} 里 {@code import.meta.server} 那一支，
 * 不走 {@code server/routes/openapi/site/[...path].ts} 那个会转发真实 IP 的代理）。
 * 结果是：</p>
 * <ul>
 *   <li>每个访客的 IP 都是 {@code 127.0.0.1}，UA 是 node，referrer 一律为空；</li>
 *   <li>Cookie 发给了 Node 进程而不是浏览器，于是 UV 永远等于 PV；</li>
 *   <li>而 PV 这个数字看起来完全正常——<b>这是最坏的一点</b>，
 *       整套统计会安安静静地给出一份错的报表。</li>
 * </ul>
 *
 * <p>所以浏览量走一个<b>浏览器直接调</b>的埋点接口（{@code POST /openapi/site/pv}，
 * 前台用 {@code navigator.sendBeacon} 发）。顺带白拿一个好处：
 * <b>绝大多数爬虫不执行 JS，那个 beacon 压根不会发</b>——「新站的量九成是爬虫」
 * 这个问题在选择埋点方式的那一刻就解决了大半，{@link BotSniffer} 只是第二道。</p>
 *
 * <h3>⚠ 写库是异步的，队列满了就丢</h3>
 * <p>这是这个功能最重要的一条取舍：<b>统计数据的价值远低于页面可用性</b>。
 * 库慢了、锁住了、连接池满了，正确的行为是丢掉浏览记录，
 * 而不是让每个访客的页面都卡在一次埋点上。</p>
 *
 * <p>线程池的写法照 {@link InquiryNotifier}：自己一个单线程 executor，
 * 不复用 ERP 的 {@code ThreadPoolConfig}（那个 Bean 在 {@code ruoyi-framework} 里，
 * 这个工程刻意不依赖 ERP 的任何一个包）。</p>
 *
 * <h3>⚠ 前端传上来的东西一个都不直接信</h3>
 * <p>公网匿名接口。{@code pagetype} 按白名单校验，{@code goodscode} 要在
 * {@code u_s_sitegoods} 里真的存在，所有字符串按库里的列宽截断。
 * {@code visitorid} / {@code clientip} / {@code isbot} / {@code sitecode}
 * <b>根本不从请求体读</b>——它们分别来自 Cookie、转发头、UA 判定和站点档案。</p>
 */
@Service
public class PageViewService
{
    private static final Logger log = LoggerFactory.getLogger(PageViewService.class);

    /**
     * ⚠ 「一天」按东八区切，和 {@code u_s_pv_daily.statdate} 那一列的注释是同一个约定。
     *
     * <p>访客在境外，按 UTC 切也说得通——但必须<b>只选一个</b>：落库用一种、
     * 查询用另一种的话，跨零点那几个小时的数字会对不上，而且极难发现。
     * 选东八区的理由：看这个数字的是国内的运营，他说「昨天」指的是他的昨天。</p>
     */
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 页面类型白名单。
     *
     * <p>⚠ 必须和前台 {@code app/plugins/track.client.ts} 里那份映射<b>一致</b>。
     * 前台报了个不在这里的值，这边会记成 {@code other}——不报错、不丢数据，
     * 但那个页面的浏览量会消失在 {@code other} 里，而没有任何迹象。
     * 加页面类型时两边一起改。</p>
     *
     * <p>⚠ {@code _all} 不在这里：它是 {@code u_s_pv_daily} 的合计行专用值，
     * 不是一种页面类型。前台报 {@code _all} 上来会被当成不认识的值记成 {@code other}，
     * 这正是想要的——不然任何人都能污染合计行。</p>
     */
    private static final Set<String> PAGE_TYPES =
            Set.of("home", "list", "detail", "cart", "checkout", "account", "other");

    /** {@code u_s_pv_daily} 里表示「当天全站合计」的那一行。见 mapper XML 文件头规矩一 */
    private static final String TYPE_ALL = "_all";

    @Autowired
    private StationProperties properties;

    @Autowired
    private SitePageViewMapper pageViewMapper;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private BotSniffer botSniffer;

    @Autowired
    private UserAgentParser userAgentParser;

    /**
     * ⚠ 用 {@code TransactionTemplate} 而不是在写入方法上挂 {@code @Transactional}。
     *
     * <p>因为写入是<b>本类的线程</b>自己调本类的方法。{@code @Transactional} 靠代理生效，
     * 类内部直接调用不经过代理——注解会被<b>静默忽略</b>，三条写入各自一个事务，
     * 明细成功而聚合失败时两张表就永久对不上了。而现象是「数字差了一点」，
     * 没人会去查是不是事务没生效。</p>
     */
    private TransactionTemplate tx;

    private BlockingQueue<SitePageView> queue;

    private Thread writer;

    /** 队列满时丢掉了多少条。按批打日志，不是每条一行——那会把日志刷爆 */
    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean running = true;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager txManager)
    {
        this.tx = new TransactionTemplate(txManager);
    }

    @PostConstruct
    public void start()
    {
        if (!properties.getPageview().isEnabled())
        {
            log.info("浏览量统计已关闭（station.pageview.enabled=false）：埋点接口照样回 204，但不落库");
            return;
        }
        queue = new ArrayBlockingQueue<>(Math.max(100, properties.getPageview().getQueueCapacity()));
        writer = new Thread(this::drainLoop, "pageview-writer");
        // 守护线程：它拿不住 JVM 的退出。真正的收尾在 shutdown() 里
        writer.setDaemon(true);
        writer.start();
        log.info("浏览量统计已启用：队列 {}，明细保留 {} 天，限流 {}/分钟 {}/天",
                properties.getPageview().getQueueCapacity(),
                properties.getPageview().getRetainDays(),
                properties.getPageview().getRatePerMinute(),
                properties.getPageview().getRatePerDay());
    }

    /**
     * 收一条上报。<b>不写库，只入队</b>，立刻返回。
     *
     * <p>⚠ 调用方必须<b>先</b>过限流（{@link PageViewRateLimiter}）再调这里。
     * 顺序反了的话，被限流的那条已经进队列了，限流就只剩一个假象——
     * 和询盘、聊天那两处同一条规矩。</p>
     *
     * @param path      页面路径。⚠ 唯一的必填项，空的直接丢
     * @param type      前端报的页面类型，不在白名单里一律记 {@code other}
     * @param goodsCode 商品编码，只有详情页有。校验推迟到写线程做（那是一次查库）
     * @param clientIp  <b>完整</b>客户端 IP。落库前会被截断，见 {@link #truncateIp}
     */
    public void report(String path, String type, String goodsCode, String lang, String referrer,
            String clientIp, String userAgent, String visitorId, String sessionId)
    {
        if (queue == null)
        {
            // 统计关掉了。不打日志——关掉是正常状态，每条上报打一行等于自己刷日志
            return;
        }

        String cleanPath = normalizePath(path);
        if (cleanPath == null)
        {
            // 没有路径的上报没有任何用处（连「哪个页面」都不知道）。静默丢弃：
            // 这种请求只可能来自手工构造，不值得为它打日志
            return;
        }

        // ⚠ visitorid 在库里是 NOT NULL。调用方（SiteVisitor.ensureVisitorId）
        //   保证它有值，但那个保证一旦被改掉，报错会发生在**写线程**里——
        //   请求侧完全看不出来，表现是统计静默停止。所以在这里挡一次
        if (trimToNull(visitorId) == null)
        {
            log.warn("浏览上报没有访客标识，这一条丢弃。检查 SiteVisitor 有没有被绕过");
            return;
        }

        String ua = cut(trimToNull(userAgent), 500);
        // ⚠ 空串不是 null：它要进 u_s_pv_daily 的主键。见那张表 goodscode 列的注释
        String goods = cut(trimToNull(goodsCode), 50);

        SitePageView view = new SitePageView();
        view.setPagePath(cleanPath);
        view.setPageType(PAGE_TYPES.contains(type) ? type : "other");
        view.setGoodsCode(goods == null ? "" : goods);
        view.setLang(cut(trimToNull(lang), 10));
        view.setReferrer(cut(trimToNull(referrer), 500));
        view.setUserAgent(ua);
        // ⚠ 顺序要紧：**先用完整 IP 查国家，再把 IP 截断存进去**。
        //
        //   截断之后再查也能查对（一个 /24 段几乎不会跨国），但那是运气，
        //   不是设计。而且 country 的查询在写线程做（要查库），
        //   到那时手上只剩截断过的值了——所以这里先把完整 IP 留给下一步用。
        //
        //   这样既拿到了国家，库里又只留截断后的 IP：两个目标都达到了。
        view.setClientIp(truncateIp(clientIp));
        // 由写线程查库填上，见 writeOne()。查不到就留 null
        view.setCountry(null);
        // ⚠ 只用来查国家，不落库。见 SitePageView.lookupIp 的字段注释
        view.setLookupIp(firstIp(clientIp));
        view.setVisitorId(visitorId);
        view.setSessionId(sessionId);
        // 爬虫判定和设备解析都放在请求线程里，不放写线程：两者都是纯字符串运算，
        // 很便宜。放写线程的话，队列积压期间「判定用的是入队时的规则还是出队时的
        // 规则」就变成一个要想的问题——没必要给自己留这种含糊
        view.setIsBot(botSniffer.isBot(ua) ? 1 : 0);
        // ⚠ 不采集新数据：这两个都是从上面那个 ua 解析出来的。
        //   UA 为空时 parser 返回 null，不猜——见 UserAgentParser.device() 的注释
        view.setDevice(userAgentParser.device(ua));
        view.setBrowser(userAgentParser.browser(ua));

        // ⚠ offer 不是 put。put 会在队列满时**阻塞请求线程**，那正是这个设计要避免的事
        if (!queue.offer(view))
        {
            long total = dropped.incrementAndGet();
            // 按 1000 条打一次。队列满通常意味着库那边出了问题，
            // 那时候日志里本来就有一堆错误，不该再被埋点刷一遍
            if (total % 1000 == 1)
            {
                log.warn("浏览上报队列已满，累计丢弃 {} 条。通常意味着数据库写入变慢了——"
                        + "先看库，不要先调大 station.pageview.queue-capacity", total);
            }
        }
    }

    /**
     * 写线程主循环。
     *
     * <p>⚠ 整个循环体在一个 try/catch 里，而且<b>catch 的是 Throwable</b>。
     * 这个线程死了的后果是「统计静默停止」——接口继续回 204、队列继续堆、
     * 一条数据都不落，而没有任何报错。所以任何异常都只记日志、不退出循环。</p>
     */
    private void drainLoop()
    {
        while (running)
        {
            SitePageView view;
            try
            {
                // 带超时地取：不然 running 变 false 之后这个线程会一直卡在 take() 上
                view = queue.poll(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            if (view != null)
            {
                writeOne(view);
            }
        }
    }

    /** 一条上报的三次写入，一个事务。见 {@link #tx} 上那段注释 */
    private void writeOne(SitePageView view)
    {
        try
        {
            SiteInfo site = siteInfoService.require();
            view.setSiteCode(site.getSiteCode());
            view.setStatDate(LocalDate.now(ZONE));

            // 商品编码校验放在这里（写线程），不放在 report()：它是一次查库，
            // 而 report() 跑在请求线程上，那里一次都不该碰库
            if (!view.getGoodsCode().isEmpty() && goodsMapper.countByCode(view.getGoodsCode()) == 0)
            {
                // ⚠ 保留 pagetype='detail'、只清掉商品编码，不把整条降级成 other。
                //   理由：这确实是一次详情页浏览，只是那个商品已经不在库里了
                //   （被删了，或者有人手工构造了一个编码）。降级会让「详情页总浏览量」
                //   凭空少一块，而那个数字是判断「有多少人真的在看货」的依据。
                //
                //   商品排行的 SQL 里带了 goodscode <> ''，所以这种行不会变成幽灵商品。
                log.debug("浏览上报里的商品编码在库里不存在，已清空：{}", view.getGoodsCode());
                view.setGoodsCode("");
            }

            // 国家：离线表查，不调任何外部服务。查不到就留 null——
            // 「查不到」和「没接库」在报表上都表现为这一档不出现，可以接受
            view.setCountry(lookupCountry(view.getLookupIp()));

            int human = view.getIsBot() != null && view.getIsBot() == 1 ? 0 : 1;

            tx.executeWithoutResult(status -> {
                pageViewMapper.insertPageView(view);
                // 合计行。⚠ 必须和下面那行维度行**都写**，只写一行的话
                //   要么趋势没数据、要么排行没数据
                pageViewMapper.upsertDaily(view.getSiteCode(), view.getStatDate(),
                        TYPE_ALL, "", human);
                pageViewMapper.upsertDaily(view.getSiteCode(), view.getStatDate(),
                        view.getPageType(), view.getGoodsCode(), human);
            });
        }
        catch (Throwable t)
        {
            // 见 drainLoop 的注释：这个线程绝不能因为一条写不进去就死掉
            log.warn("浏览上报落库失败，这一条丢弃：{}（{}）", view.getPagePath(), t.getMessage());
        }
    }

    /**
     * 关服务时把队列里剩下的写完。
     *
     * <p>照 {@link InquiryNotifier#shutdown} 那一套。这里等的时间给得很短（3 秒）：
     * 剩几条浏览记录没写完完全可以接受，而让重启多等 30 秒不行——
     * nssm 停服务有超时，超了会直接 kill。</p>
     */
    @PreDestroy
    public void shutdown()
    {
        running = false;
        if (writer == null)
        {
            return;
        }
        int remaining = queue == null ? 0 : queue.size();
        if (remaining > 0)
        {
            log.info("关闭中：浏览上报队列里还有 {} 条，最多等 3 秒", remaining);
        }
        try
        {
            writer.join(3000);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        long lost = dropped.get();
        if (lost > 0)
        {
            log.info("本次运行期间因队列满丢弃了 {} 条浏览记录", lost);
        }
    }

    // ==================== 清洗 ====================

    /**
     * 路径规范化。
     *
     * <p>做四件事，每一件都有具体的理由：</p>
     * <ol>
     *   <li><b>只留路径，丢掉 query string</b>。{@code ?utm_source=x} 会让同一个页面
     *       裂成无穷多个「不同页面」，而 {@code u_s_pv_daily} 的主键里有维度列——
     *       裂开的直接后果是那张表的行数失控。</li>
     *   <li><b>必须以 {@code /} 开头</b>。挡的是有人报一个完整 URL
     *       （{@code https://evil.example/...}）上来——那会让报表里出现别人的域名。</li>
     *   <li><b>去掉换行和控制字符</b>。这一列会被管理台原样显示，
     *       换行能把日志和界面搅乱。</li>
     *   <li>截到 300，和库里那一列一样宽。</li>
     * </ol>
     */
    private String normalizePath(String path)
    {
        String value = trimToNull(path);
        if (value == null)
        {
            return null;
        }
        int cut = value.indexOf('?');
        if (cut >= 0)
        {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('#');
        if (cut >= 0)
        {
            value = value.substring(0, cut);
        }
        // 控制字符（含换行、制表）一律去掉
        value = value.replaceAll("[\\p{Cntrl}]", "");
        value = value.trim();
        if (value.isEmpty() || value.charAt(0) != '/')
        {
            return null;
        }
        return cut(value, 300);
    }

    /**
     * 查这个 IP 属于哪个国家。查不到、查出错、或者 IP 认不出来，一律返回 null。
     *
     * <p>⚠ <b>任何异常都吞掉</b>。国家只是报表上的一档，而这个方法跑在写线程里——
     * 让它抛出去会让整条浏览记录写不进去。「没有国家」比「没有记录」好得多。</p>
     *
     * <p>⚠ 这是一次查库。它在写线程、不在请求线程，所以不影响访客。
     * 表在 {@code u_s_ip_country}，走主键索引，单次查询是零点几毫秒。</p>
     */
    private String lookupCountry(String ip)
    {
        String value = trimToNull(ip);
        if (value == null)
        {
            return null;
        }
        // 和落库那一路同样的清洗：方括号和 zone id 会让 INET6_ATON 返回 NULL，
        // 而 NULL 查不到任何行、也不报错——国家就静默为空了
        if (value.startsWith("["))
        {
            int close = value.indexOf(']');
            value = close > 0 ? value.substring(1, close) : value.substring(1);
        }
        int pct = value.indexOf('%');
        if (pct > 0)
        {
            value = value.substring(0, pct);
        }

        try
        {
            // ⚠ ipver 必须传对：那张表里 IPv4 存 4 字节、IPv6 存 16 字节，
            //   混着比大小无意义且不报错。见 mapper 里那条查询的注释
            int ver = value.indexOf(':') >= 0 ? 6 : 4;
            String country = pageViewMapper.selectCountryByIp(ver, value);
            return country == null || country.isBlank() ? null : country;
        }
        catch (Exception e)
        {
            // 最常见的原因是 u_s_ip_country 还没建/没导数据。
            // 打 debug 不打 warn：那种情况下每条浏览都会走到这里，warn 会刷爆日志
            log.debug("查 IP 归属国家失败，这条留空：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 取转发链里的第一个 IP。
     *
     * <p>{@code X-Forwarded-For} 可能是 {@code "client, proxy1, proxy2"}，
     * 第一个才是访客。</p>
     */
    private static String firstIp(String ip)
    {
        String value = trimToNull(ip);
        if (value == null)
        {
            return null;
        }
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma).trim() : value;
    }

    /**
     * IP 截断。IPv4 去掉最后一段，IPv6 只留前 48 位（前三组）。
     *
     * <p>⚠ 落库<b>只存截断后的</b>。理由见 migration 脚本文件头那段：这张表记的是
     * 匿名浏览行为、量大一百倍、保留 90 天，而访客在境外——完整 IP 加一个长期 Cookie
     * 在 GDPR 下就是个人数据。截断到 /24 之后精度只到城市级，
     * 够看「哪个国家来的人多」，不再指向某一个人。</p>
     *
     * <p>⚠ 限流那一层用的是<b>完整</b> IP（{@link PageViewRateLimiter}）。
     * 不能共用这个方法的结果：一个 C 段的 254 台机器会挤在同一个额度里。</p>
     *
     * <p>⚠ 截断之后<b>不要</b>再想着「反正还能按 /24 定位到人」——它就是不能，
     * 这是这个设计想要的效果，不是一个待改进的粗糙实现。</p>
     */
    private String truncateIp(String ip)
    {
        String value = trimToNull(ip);
        if (value == null)
        {
            return null;
        }
        // 反代链路上可能是 "client, proxy1, proxy2"，取第一个
        int comma = value.indexOf(',');
        if (comma > 0)
        {
            value = value.substring(0, comma).trim();
        }
        if (value.indexOf(':') >= 0)
        {
            return cut(truncateIpv6(value), 64);
        }
        int dot = value.lastIndexOf('.');
        if (dot <= 0)
        {
            // 认不出形状（可能是 "unknown"）。原样留着，别猜
            return cut(value, 64);
        }
        return cut(value.substring(0, dot) + ".0", 64);
    }

    /**
     * IPv6 截断到 /48（前三组），其余省略成 {@code ::}。
     *
     * <h3>⚠ 为什么必须先展开 {@code ::}，不能直接 split</h3>
     * <p>这里原来写的是「含 {@code ::} 的原样保留」，理由是「压缩形式主要是 ::1
     * 这种，不是个人数据」。<b>那个判断是错的</b>，2026-09-09 本机实测当场暴露：
     * 真实的全球 IPv6 地址普遍就是压缩写法（{@code 2a00:1450:4001::1}），
     * 于是一个欧洲访客的<b>完整地址被原样存进了库</b>——
     * 而「这张表只存截断 IP」是整套设计的隐私前提。</p>
     *
     * <p>直接 {@code split(":")} 也不行：{@code "::1".split(":")} 得到
     * {@code ["", "", "1"]}，拼回去是 {@code "::1::"}，一个<b>非法</b>地址。
     * 它不会报错（列是 varchar），只会在库里躺着。</p>
     *
     * <p>所以：先把 {@code ::} 展开成完整的 8 组，取前 3 组，再拼回压缩形式。</p>
     *
     * <p>⚠ 认不出形状时<b>返回一个明确的占位值而不是原值</b>。
     * 返回原值等于「解析失败就把完整地址存下来」——那正是最不该发生的情况，
     * 而且它只在异常输入上发生，测试很难覆盖到。</p>
     */
    private static String truncateIpv6(String value)
    {
        String str = value;

        // ⚠ 去掉方括号和 zone id。不是防御性编程，是实测必需：
        //   生产上配了 SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK，Spring 重写
        //   getRemoteAddr() 时走 URI 解析，而 URI 里 IPv6 是带方括号的
        //   （"[2a00:1450:4001::1]"）。不剥掉的话第一组是 "[2a00"，不是十六进制，
        //   整个地址被判为认不出——库里就只剩一个 "::"。
        //   完整来龙去脉见 SsoController.clientIp() 里那段注释。
        if (str.startsWith("["))
        {
            int close = str.indexOf(']');
            str = close > 0 ? str.substring(1, close) : str.substring(1);
        }
        // %eth0 这种链路本地地址的 zone id，对统计没有意义
        int pct = str.indexOf('%');
        if (pct > 0)
        {
            str = str.substring(0, pct);
        }

        // 内嵌 IPv4 的写法（::ffff:1.2.3.4）：先把尾巴换成两组十六进制
        int lastColon = str.lastIndexOf(':');
        if (lastColon >= 0 && str.indexOf('.', lastColon) > 0)
        {
            String v4 = str.substring(lastColon + 1);
            String[] octets = v4.split("\\.");
            if (octets.length == 4)
            {
                try
                {
                    int a = Integer.parseInt(octets[0]), b = Integer.parseInt(octets[1]);
                    int c = Integer.parseInt(octets[2]), d = Integer.parseInt(octets[3]);
                    str = str.substring(0, lastColon + 1)
                            + String.format("%02x%02x:%02x%02x", a, b, c, d);
                }
                catch (NumberFormatException e)
                {
                    return "::";
                }
            }
        }

        String[] head;
        String[] tail;
        int dbl = str.indexOf("::");
        if (dbl >= 0)
        {
            String h = str.substring(0, dbl);
            String t = str.substring(dbl + 2);
            head = h.isEmpty() ? new String[0] : h.split(":");
            tail = t.isEmpty() ? new String[0] : t.split(":");
        }
        else
        {
            head = str.split(":");
            tail = new String[0];
        }

        int missing = 8 - head.length - tail.length;
        if (missing < 0 || (dbl < 0 && missing != 0))
        {
            // 形状不对。见方法注释：宁可返回占位值，也不能把原值存下去
            return "::";
        }

        String[] groups = new String[8];
        int i = 0;
        for (String g : head)
        {
            groups[i++] = g;
        }
        for (int k = 0; k < missing; k++)
        {
            groups[i++] = "0";
        }
        for (String g : tail)
        {
            groups[i++] = g;
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 3; k++)
        {
            String g = groups[k];
            // 每一组必须是 1~4 位十六进制，否则整个地址就不可信
            if (g == null || g.isEmpty() || g.length() > 4
                    || !g.chars().allMatch(ch -> Character.digit(ch, 16) >= 0))
            {
                return "::";
            }
            sb.append(g.toLowerCase(Locale.ROOT)).append(':');
        }
        // 前三组 + "::"，例如 2a00:1450:4001::
        return sb.append(':').toString();
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按库里的列宽截断。
     *
     * <p>⚠ 这不是「优化」，是必须的：超长的值会让 INSERT 报错，而那个错发生在
     * <b>写线程</b>里——请求侧看不出任何异常，表现是这个访客的浏览永远记不上。</p>
     */
    private static String cut(String value, int max)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
