package com.ruoyi.station.console.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.CurrencyConvertLog;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteInfoMapper;

/**
 * 本站档案。<b>这个服务同时是「我是谁」的唯一出处</b>。
 *
 * <h3>它替代了什么</h3>
 * <p>以前站点身份来自 ERP：验票时 ERP 把 siteCode / siteName / langs / currency
 * 一起塞进票里，管理台存进会话。那条路没有了（docs/独立站与ERP的边界.md 要点 1），
 * 站点必须能<b>独立说出自己是谁</b>——尤其是验跳转令牌那一步：
 * 令牌里的 {@code site} 声明要和 {@link SiteInfo#getSiteCode()} 对上才放行（§4.3），
 * 而这一步发生在任何网络请求之前。</p>
 *
 * <h3>为什么缓存</h3>
 * <p>前台每渲染一个页面都要读一次站点信息（语言、币种、装修内容）。这张表只有一行、
 * 几乎不变，每次去数据库查一遍纯属浪费。写操作走这个类，写完就把缓存清掉，
 * 不存在「改了没生效」。</p>
 *
 * <p><b>缓存是本进程内的。</b>一台机器一个站、一个进程，这一点成立。真要多开实例时，
 * 这里要改成读 Redis 或者干脆不缓存——那时候「改了别的实例没生效」会是个真问题。</p>
 */
@Service
public class SiteInfoService
{
    private static final Logger log = LoggerFactory.getLogger(SiteInfoService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SiteInfoMapper siteInfoMapper;

    /**
     * 改币种时的金额换算。
     *
     * <p>⚠ 不会反过来依赖本类：它只认调用方传进来的新旧币种，
     * 自己不去查「站点现在是什么币种」——真去查了就会在这个事务里读到
     * 一个半新半旧的值。</p>
     */
    @Autowired
    private CurrencyConvertService currencyConvertService;

    /**
     * 站点档案缓存。
     *
     * <p>{@code volatile}：写它的是管理台线程，读它的是前台的请求线程。
     * 没有这个关键字，某个线程可能长期读到旧引用——而表现是「有的请求生效了、
     * 有的没生效」，最难查的那一类。</p>
     */
    private volatile SiteInfo cached;

    /**
     * 启动时把站点身份喊一嗓子。
     *
     * <p>建库脚本刻意<b>不插</b> {@code u_s_siteinfo} 那一行（见脚本末尾的说明：
     * 塞占位值会让某台机器带着一个进过 Git 的跳转密钥上线）。所以「表是空的」
     * 是一种正常的中间状态，不是错误——但必须让运维一眼看见，
     * 否则表现是前台 500、管理台进不去，光看现象查不到根因。</p>
     */
    @PostConstruct
    public void reportConfiguration()
    {
        SiteInfo site;
        try
        {
            site = siteInfoMapper.selectSite();
        }
        catch (Exception e)
        {
            // ⚠ 这一句不能让进程起不来。
            //
            // @PostConstruct 里抛异常 = Bean 创建失败 = Spring 启动中止。数据库比应用
            // 晚起来几秒（同一台机器上一起开机时很常见）就会变成「服务反复重启」，
            // 而 nssm 那边配的是崩了自动拉起——两边一叠加就是一个起不来也停不下的循环。
            //
            // 这个方法本来就只是「启动时喊一嗓子」，它失败不影响任何功能：
            // require() 会在第一次真正用到时再查一次。
            log.error("启动时读不到本站档案（数据库还没起来？）：{}。"
                    + "服务照常启动，第一次请求时会再试一次", e.getMessage());
            return;
        }
        if (site == null)
        {
            log.error("本站档案还没建：u_s_siteinfo 是空的。前台和管理台都起不来。"
                    + "按 docs/独立站部署手册（Windows）.md §4.3 插入那一行（sitecode / sitename / consolesecret），"
                    + "其中 consolesecret 必须和 ERP「独立站管理」里登记的那份一致");
            return;
        }
        cached = site;
        log.info("本站：{}（{}），语言 {}，币种 {}，状态 {}{}",
                site.getSiteName(), site.getSiteCode(), site.langList(), site.getCurrency(),
                site.getStatus(),
                site.getConsoleSecret() == null || site.getConsoleSecret().isBlank()
                        ? "。⚠ 未配置跳转密钥，从 ERP「进入管理」会被拒" : "");

        // ⚠ 被白名单丢掉的语言必须喊出来。静默丢掉的话，运营会一直以为法语站开着，
        //   而访客看到的一直是英文——那种不一致没有任何现象能提示它存在。
        //   白名单本身和「加一种语言要动哪 5 个地方」写在 SiteInfo.SUPPORTED_LANGS 上。
        List<String> unsupported = site.unsupportedLangList();
        if (!unsupported.isEmpty())
        {
            log.warn("langs 里有 {} 前台还不支持，已忽略。前台没有这些语言的界面文案，"
                    + "放出去会变成「语言切换器里有入口、点进去是英文页」，"
                    + "而且 hreflang 会向 Google 承诺一个不存在的语言版本。"
                    + "要支持请按 SiteInfo.SUPPORTED_LANGS 注释里的步骤补齐前台文案",
                    unsupported);
        }
    }

    /**
     * 取本站档案。查不到抛异常，不返回 null。
     *
     * <p>返回 null 的话，每一个调用点都要判一遍，而漏判的表现是一个空指针栈——
     * 比一句「本站档案还没建」难查得多。</p>
     */
    public SiteInfo require()
    {
        SiteInfo site = cached;
        if (site == null)
        {
            site = siteInfoMapper.selectSite();
            if (site == null)
            {
                throw new ServiceException("本站档案还没建，请先按部署手册初始化 u_s_siteinfo");
            }
            cached = site;
        }
        return site;
    }

    /**
     * 前台取数用：档案 + 「前台开着没有」这道闸。
     *
     * <p>停用和建设中一律报同一句话，不区分——不告诉调用方到底是哪一种，
     * 免得被人拿来探测站点状态。</p>
     */
    public SiteInfo requirePublic()
    {
        SiteInfo site = require();
        if (!site.isPublicOpen())
        {
            throw new ServiceException("站点不可用");
        }
        return site;
    }

    /**
     * 保存站点档案。<b>改结算币种时会顺带换算全站金额</b>。
     *
     * <p>在这之前，改币种只改 {@code currency} 这一个三字母码，商品价和订单金额的
     * 数字一个不动——把 GBP 改成 USD，标价 £100 的商品当场变成 $100，页面上
     * 没有任何提示。换算发生在这个事务里，见 {@link CurrencyConvertService}。</p>
     *
     * <p><b>⚠ 换算不可逆</b>（乘汇率 + 四舍五入，原值不留副本），所以入口只有这一个，
     * 而且要求调用方先在界面上把代价说清楚。想只改代码不动数字，请求里显式送
     * {@code convertAmounts = false}。</p>
     *
     * @return 这次换算的流水；<b>币种没变、或运营选了不换算时为 {@code null}</b>。
     *         不再返回受影响行数——那个值恒为 1（这张表只有一行），从来没有调用方用它
     */
    @Transactional(rollbackFor = Exception.class)
    public CurrencyConvertLog update(SiteInfo site, String operator)
    {
        if (site == null)
        {
            throw new ServiceException("参数不能为空");
        }

        /*
          ⚠ 换算必须排在 siteInfoMapper.update 前面。

          不是因为顺序影响结果——两者在同一个事务里，谁先谁后落库都一样。是因为
          换算要知道**旧币种**，而它的唯一出处是 require() 读的那份档案（走缓存）。
          先 update 再 evict 的话，这里 require() 到的已经是新值，
          换算就变成了 USD → USD：rate = 1，全站金额原地乘一遍 1，
          流水里留下一条看不出问题的记录，而运营以为已经换算过了。
        */
        CurrencyConvertLog converted = convertIfCurrencyChanged(site, operator);

        site.setUpdateUser(operator);
        siteInfoMapper.update(site);
        evict();
        return converted;
    }

    /**
     * 币种真的变了才换算。
     *
     * <p>三种情况直接返回 {@code null}，一次金额都不动：请求里没带币种
     * （SQL 那边是 coalesce，保持原值）、新旧一致、运营显式选了不换算。</p>
     */
    private CurrencyConvertLog convertIfCurrencyChanged(SiteInfo site, String operator)
    {
        String wanted = site.getCurrency() == null
                ? null : site.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (wanted == null || wanted.isEmpty())
        {
            return null;
        }
        // ⚠ 读的是**还没改**的那份，见上面那段。baseCurrency() 会把空值兜成 USD，
        //   和 SiteInfoMapper 里 insert 的默认值一致
        SiteInfo current = require();
        String from = current.baseCurrency();
        if (from.equals(wanted))
        {
            return null;
        }
        if (!site.shouldConvertAmounts())
        {
            // ⚠ 这条必须是 WARN 并且说清后果。「只改代码不换算」是给
            //   「当初币种就填错了」准备的退路；如果不是那种情况，
            //   这一下就是把全站价格按 1:1 重新标了一遍——
            //   而它不改任何数字，没有任何现象能提示它发生过
            log.warn("结算币种 {} → {}，本次**不换算**（请求里 convertAmounts=false）。"
                    + "商品价、型号价、订单金额的数字一个不动，只有符号变了。"
                    + "这条路是给「当初币种填错了」准备的；如果不是，"
                    + "全站价格刚刚被按 1:1 重新标价了一遍", from, wanted);
            return null;
        }
        return currencyConvertService.convert(current, from, wanted, operator);
    }

    /**
     * 保存首页装修<b>草稿</b>。前台看到的仍然是上一次发布的那份。
     *
     * <p>请求体是完整的装修 JSON 字符串，不是拆开的字段：这块内容整体存整体读，
     * 拆成字段传过来还得在服务端拼回去，中间少一个键就是首页少一块。</p>
     *
     * <p><b>空串的含义变了，看清楚：</b>在「保存即上线」那一版里，空串表示
     * 「清空装修内容、前台回退到内置默认」。现在空串表示的是<b>「草稿就是空的」</b>——
     * 也就是商家在管理台里点了「清空」，但那个清空要等他按「发布」才见客。
     * 所以这里把空串存成一个真正的空 JSON 对象 {@code "{}"}，而不是 NULL：
     * NULL 在 draftjson 这一列上已经被占用了，含义是「没有未发布的改动」，
     * 两者必须区分得开，否则「清空 + 发布」这条路走不通（存成 NULL 的话
     * 下一步发布会发现没有草稿，直接告诉商家没什么可发布的）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveDraft(String contentJson, String operator)
    {
        String text = contentJson == null || contentJson.trim().isEmpty() ? "{}" : contentJson.trim();
        // 存之前先解析一遍。存了坏 JSON，前台首页那几块就会空着，
        // 而人在后台看是有内容的——这种「后台有前台没有」最难查
        try
        {
            JSON.readTree(text);
        }
        catch (Exception e)
        {
            throw new ServiceException("装修内容不是合法的 JSON，请检查后重试");
        }
        int rows = siteInfoMapper.updateDraft(text, operator);
        evict();
        return rows;
    }

    /**
     * 发布：把草稿整份拷进已发布那份，草稿清空。
     *
     * <p>拷贝在 SQL 里做（{@code set contentjson = draftjson}），不经过 Java——
     * 少一次几百 KB 的搬运，也就不存在「读出来和写回去之间被另一个人改掉」那种窗口。</p>
     *
     * <p>发布之后 {@code draftjson} 为 NULL，管理台那个「未发布」标记随之消失。</p>
     *
     * @throws ServiceException 当时没有草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public int publishDraft(String operator)
    {
        int rows = siteInfoMapper.publishDraft(operator);
        if (rows == 0)
        {
            // 0 行只有一种原因：draftjson is not null 没命中（表里恒有 id=1 那一行）
            throw new ServiceException("没有待发布的改动");
        }
        evict();
        return rows;
    }

    /**
     * 放弃草稿，回到上一次发布的那份。
     *
     * <p>这就是这次「草稿/发布」顺带给到的回滚能力：改坏了不用记得原来填了什么，
     * 上一个已发布版本一直完整地躺在 {@code contentjson} 里。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int discardDraft()
    {
        int rows = siteInfoMapper.discardDraft();
        evict();
        return rows;
    }

    /**
     * 清一次缓存再读，拿到的必然是库里当下那一份。
     *
     * <p>给「刚写完、必须看到新值」的调用方用：{@code ConsoleApiController#saveSite}
     * 在 {@link #update} 返回之后（也就是事务已经提交）调一次。</p>
     *
     * <p><b>⚠ 这一步不是可选的洁癖。</b>{@code /me} 下发的 siteName / langs / currency
     * 现在直接读这份缓存（不再读会话快照）。缓存里要是留着旧值，表现就是
     * 「站点设置里明明是 USD，商品管理还画着 €」——和 2026-09-22 那次一模一样的现象，
     * 只不过触发条件从「必然」变成了「撞上那个窗口才会」，更难查。</p>
     *
     * <p><b>⚠ 为什么不能直接 {@link #require()}。</b>写操作的 {@link #evict()} 发生在
     * <b>事务提交之前</b>。在 evict 和 commit 之间有一个极短的窗口，这期间任何一个
     * 前台页面渲染（{@code requirePublic()} 每次都读）都会去查库、拿到<b>还没提交</b>
     * 的旧值、然后把它缓存起来。事务提交之后，缓存里躺着的仍然是旧值。</p>
     *
     * <p>那个窗口是微秒级的，但前台是每次渲染都读——赌不起。这个方法在提交之后
     * 再清一次，把它关掉。</p>
     */
    public SiteInfo reload()
    {
        evict();
        return require();
    }

    /** 清缓存。任何写操作之后都要调，漏一处就是「改了不生效」 */
    private void evict()
    {
        cached = null;
    }
}
