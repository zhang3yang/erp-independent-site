package com.ruoyi.station.console.service;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.station.console.domain.SiteSeo;
import com.ruoyi.station.console.mapper.SiteSeoMapper;

/**
 * 站长工具配置的读写。
 *
 * <h3>缓存</h3>
 * <p>和 {@link SiteInfoService} 同一套路：前台每渲染一个页面都要读一次（验证码、
 * 统计 ID 要进 head），而这张表只有一行、几乎不变。写走这个类，写完清缓存，
 * 不存在「改了没生效」。</p>
 *
 * <p><b>缓存是本进程内的。</b>一台机器一个站一个进程，这一点成立。真要多开实例时，
 * 这里要改成读 Redis 或者干脆不缓存。</p>
 *
 * <h3>⚠ 查不到那一行不是错误</h3>
 * <p>老库没跑 {@code 2026-09-08-seo-tools.sql} 就查不到。这时候返回
 * {@link SiteSeo#defaults()}，而那份默认值<b>等于这张表不存在时前台的原有行为</b>。
 * 所以「没跑迁移」和「跑了但没配」表现完全一致，不多出一种故障形态。</p>
 *
 * <p>反过来说：不要在这里抛异常。抛了会让一个还没跑迁移的老库整个管理台打不开，
 * 而它本来只是少一个可选功能。</p>
 */
@Service
public class SiteSeoService
{
    private static final Logger log = LoggerFactory.getLogger(SiteSeoService.class);

    /**
     * {@code robotsExtra} 的长度上限。
     *
     * <p>比库里那一列（2000）小一点，为的是让越界在<b>保存时</b>被一句话拒掉，
     * 而不是到数据库那一层变成一个 {@code Data too long} 的 500。</p>
     */
    private static final int ROBOTS_EXTRA_MAX = 1800;

    /** {@code disallowPaths} 的长度上限，同上，库里那一列是 1000 */
    private static final int DISALLOW_MAX = 900;

    /**
     * robots.txt 里不能出现的字符。
     *
     * <p>robots.txt 是<b>按行</b>解析的纯文本，所以要防的只有一件事：有人在
     * {@code robotsExtra} 里塞进 {@code \r} 或别的控制字符，把一行拆成两行、
     * 造出一条自己没打算写的指令（比如在 {@code Crawl-delay: 1} 后面接一个
     * {@code Disallow: /}）。</p>
     *
     * <p>换行本身是合法的（那一列就是要写多行），所以 {@code \n} 放过，
     * {@code \r} 和其余控制字符一律拒。</p>
     */
    private static final Pattern ROBOTS_FORBIDDEN = Pattern.compile("[\\x00-\\x09\\x0B-\\x1F\\x7F]");

    /**
     * Disallow 路径的合法字符。
     *
     * <p>URL 路径字符加通配符 {@code *} 和行尾锚 {@code $}（robots.txt 支持这两个）。
     * 空格和换行不在里面：带空格的那一条在 robots.txt 里<b>不报错、只是不生效</b>，
     * 在保存时挡下来比让人几周后才发现要好。</p>
     */
    private static final Pattern DISALLOW_PATH = Pattern.compile("^/[A-Za-z0-9\\-._~!$&'()+,;=:@/%*?\\[\\]]*\\$?$");

    @Autowired
    private SiteSeoMapper siteSeoMapper;

    /**
     * 配置缓存。
     *
     * <p>{@code volatile}：写它的是管理台线程，读它的是前台的请求线程。没有这个关键字，
     * 某个线程可能长期读到旧引用——表现是「有的请求生效了、有的没生效」，
     * 最难查的那一类。</p>
     */
    private volatile SiteSeo cached;

    /**
     * 当前配置。<b>永不返回 null。</b>
     *
     * <p>调用方（管理台接口、{@code SiteOpenService}）不必判空，也不必自己补默认值——
     * 补默认值这件事只在这里做一次，多处各补一份早晚会补得不一样。</p>
     */
    public SiteSeo current()
    {
        SiteSeo seo = cached;
        if (seo != null) return seo;

        SiteSeo loaded;
        try
        {
            loaded = siteSeoMapper.selectSeo();
        }
        catch (Exception e)
        {
            // ⚠ 表不存在（老库没跑迁移，连 CREATE TABLE 都没执行）会走到这里。
            //
            //   这不能变成一个 500：站长工具是个可选功能，而 current() 在
            //   /openapi/site/info 的路径上——那是**前台每个页面**都要打的接口。
            //   在这里抛异常等于「没跑迁移的老库整个前台白屏」。
            //
            //   ⚠ 不缓存这种失败：表建起来之后要能自己恢复，不必重启进程。
            //   代价是表真不存在时每次请求都查一次并打一行日志，那是应该被看见的。
            log.warn("读不到站长工具配置（u_s_seo 还没建？跑一下 "
                    + "sql/station/migration/2026-09-08-seo-tools.sql）：{}。"
                    + "本次按默认值走：允许收录、生成 sitemap、不注入统计", e.getMessage());
            return SiteSeo.defaults();
        }

        if (loaded == null)
        {
            // 表在、行不在。同样按默认值走，而且**缓存它**——这是个稳定状态
            // （建表脚本里那条 INSERT IGNORE 没跑），不是瞬时故障。
            loaded = SiteSeo.defaults();
        }
        loaded.normalized();
        cached = loaded;
        return loaded;
    }

    /**
     * 存配置。
     *
     * <p>校验全部在这里做完再落库。⚠ 但这<b>不是</b>「只存 ID」那条安全前提的依靠——
     * 库里的值可能是别人用 SQL 直接改的（部署时手工补配置是常态），所以
     * {@code seo-head.ts} 在<b>拼进 HTML 之前还会再校验一次</b>。两处都要有。</p>
     */
    public void save(SiteSeo form, String operator)
    {
        if (form == null) throw new ServiceException("没有收到要保存的内容");

        form.setId(1);
        form.setUpdateUser(operator);

        // ---- 收录 ----
        form.setNoindexAll(form.noindexAllOn() ? 1 : 0);
        form.setDisallowPaths(checkDisallow(form.getDisallowPaths()));
        form.setRobotsExtra(checkRobotsExtra(form.getRobotsExtra()));

        // ---- sitemap ----
        form.setSitemapOn(form.getSitemapOn() != null && form.getSitemapOn() == 1 ? 1 : 0);
        if (form.getSitemapMax() == null || form.getSitemapMax() <= 0)
        {
            throw new ServiceException("sitemap 商品条数上限要是一个正整数");
        }
        if (form.getSitemapMax() > SiteSeo.SITEMAP_MAX_CEILING)
        {
            // ⚠ 说清为什么是这个数，否则下一个人只会把常量改大。
            throw new ServiceException("sitemap 商品条数上限最多 " + SiteSeo.SITEMAP_MAX_CEILING
                    + "。这一项是**商品条数**不是 URL 条数——每个商品会乘以语言数，"
                    + "8 种语言时 " + SiteSeo.SITEMAP_MAX_CEILING + " 个商品约 4.8 万条 URL，"
                    + "已经接近 sitemap 单文件 5 万条的规范上限。真要更多得改成分片 sitemap");
        }
        if (!SiteSeo.validFreq(form.getHomeFreq()) || !SiteSeo.validFreq(form.getGoodsFreq()))
        {
            throw new ServiceException("changefreq 只能是 always/hourly/daily/weekly/monthly/yearly/never");
        }

        // ---- 验证码 ----
        form.setGoogleVerify(checkToken(form.getGoogleVerify(), "Google 验证码"));
        form.setBingVerify(checkToken(form.getBingVerify(), "Bing 验证码"));
        form.setBaiduVerify(checkToken(form.getBaiduVerify(), "百度验证码"));

        // ---- 统计 ID ----
        form.setGa4Id(checkId(form.getGa4Id(), SiteSeo.GA4_ID, "GA4 测量 ID", "G-XXXXXXXXXX"));
        form.setGtmId(checkId(form.getGtmId(), SiteSeo.GTM_ID, "GTM 容器 ID", "GTM-XXXXXXX"));
        form.setBaiduHmId(checkId(form.getBaiduHmId(), SiteSeo.BAIDU_HM_ID,
                "百度统计 ID", "32 位小写十六进制"));

        siteSeoMapper.upsert(form);

        // ⚠ 清缓存，不是把 form 塞进去：form 是前端传上来的对象，没有 createTime，
        //   而且 normalized() 之后的值要以库里为准。下一次 current() 重新查一遍。
        cached = null;

        log.info("站长工具配置已更新（操作人 {}）：禁收录={} sitemap={} GA4={} GTM={}",
                operator, form.noindexAllOn(), form.getSitemapOn() == 1,
                blankToDash(form.getGa4Id()), blankToDash(form.getGtmId()));

        // ⚠ 这一条要在日志里喊出来：整站禁收录没有任何前台症状——页面照常显示，
        //   只有几周之后流量掉了才发现。管理台上有二次确认，但改这个值也可能是
        //   走 SQL 改的，那时候日志是唯一的痕迹。
        if (form.noindexAllOn())
        {
            log.warn("⚠ 整站禁收录已打开：robots.txt 输出 Disallow: /，每个页面带 noindex，"
                    + "sitemap.xml 返回 404。站对访客照常开放，但搜索引擎会在几周内"
                    + "把已收录的页面撤下。软上线结束后记得关掉");
        }
    }

    /** 强制下一次 current() 重新查库。给测试和「配置被 SQL 改了」的场合留一个出口 */
    public void evict()
    {
        cached = null;
    }

    // ==================== 校验 ====================

    /**
     * 空串一律归一成 null。
     *
     * <p>⚠ 为什么必须归一：前端清空一个输入框传上来的是 {@code ""}，而
     * {@code seo-head.ts} 那边判「有没有配」用的是真值判断。{@code ""} 在
     * JavaScript 里是假值，所以其实也不会注入——但 {@code null} 和 {@code ""}
     * 两种「空」并存会让每个判空的地方都要想一遍该判哪个，早晚有一处判错。</p>
     */
    private String blank(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String blankToDash(String s)
    {
        return s == null || s.isBlank() ? "-" : s;
    }

    private String checkToken(String raw, String label)
    {
        String v = blank(raw);
        if (v == null) return null;
        if (!SiteSeo.VERIFY_TOKEN.matcher(v).matches())
        {
            throw new ServiceException(label + "格式不对。这里只填验证码那一段字符串，"
                    + "不要把整个 <meta> 标签粘进来；允许的字符是字母、数字和 - _ = .");
        }
        return v;
    }

    private String checkId(String raw, Pattern pattern, String label, String example)
    {
        String v = blank(raw);
        if (v == null) return null;
        if (!pattern.matcher(v).matches())
        {
            throw new ServiceException(label + "格式不对，应形如 " + example
                    + "。⚠ 这里只填 ID，不要粘整段统计代码——脚本由前台自己拼，"
                    + "这是为了不给「往每个公开页面注入任意 JavaScript」留入口");
        }
        return v;
    }

    private String checkDisallow(String raw)
    {
        String v = blank(raw);
        if (v == null) return null;
        if (v.length() > DISALLOW_MAX)
        {
            throw new ServiceException("额外 Disallow 路径太长了（最多 " + DISALLOW_MAX + " 字符）");
        }
        for (String path : v.split(","))
        {
            String p = path.trim();
            if (p.isEmpty()) continue;
            if (!p.startsWith("/")) p = "/" + p;
            if (!DISALLOW_PATH.matcher(p).matches())
            {
                throw new ServiceException("Disallow 路径「" + path.trim() + "」不合法。"
                        + "要是一个以 / 开头的路径，可以带 * 通配和结尾的 $，"
                        + "但不能有空格或换行——带空格的那一条在 robots.txt 里不报错、只是不生效");
            }
        }
        return v;
    }

    private String checkRobotsExtra(String raw)
    {
        String v = blank(raw);
        if (v == null) return null;
        if (v.length() > ROBOTS_EXTRA_MAX)
        {
            throw new ServiceException("robots.txt 自定义内容太长了（最多 " + ROBOTS_EXTRA_MAX + " 字符）");
        }
        // ⚠ 先把 \r\n 统一成 \n 再判：Windows 上从记事本粘过来的内容一定带 \r，
        //   直接拒掉的话运营会看到一句「有非法字符」而完全不知道是哪个字符。
        v = v.replace("\r\n", "\n").replace("\r", "\n");
        if (ROBOTS_FORBIDDEN.matcher(v).find())
        {
            throw new ServiceException("robots.txt 自定义内容里有控制字符。"
                    + "这一项是按行解析的纯文本，只能有普通字符和换行");
        }
        return v;
    }
}
