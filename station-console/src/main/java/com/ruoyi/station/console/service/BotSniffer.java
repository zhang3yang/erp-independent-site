package com.ruoyi.station.console.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 按 User-Agent 判断这次浏览是不是爬虫。
 *
 * <h3>⚠ 先说清这一层的地位：它是<b>第二道</b>，不是主力</h3>
 * <p>主力是埋点方式本身。浏览量走的是浏览器 {@code sendBeacon} 上报
 * （见 {@code station-site/app/plugins/track.client.ts}），而<b>绝大多数爬虫不执行
 * JavaScript</b>——它们抓走 HTML 就走，那个 beacon 压根不会发。所以「新站的量九成是爬虫」
 * 这个问题，在选择埋点方式的那一刻就已经解决了大半。</p>
 *
 * <p>这个类治的是剩下那小半：Googlebot 这类<b>会渲染 JS</b> 的爬虫，
 * 以及有人用 Puppeteer / Playwright 跑的无头浏览器。</p>
 *
 * <p>⚠ 所以别指望它能挡住「伪装成 Chrome 的爬虫」——那种东西 UA 和真人一模一样，
 * 靠 UA 判定原理上就做不到。真要治那个只能上行为分析，而对一个 B2B 站来说，
 * 为它引入一套行为分析是把成本花错了地方。这一层做到「不把已知爬虫算进真人」为止。</p>
 *
 * <h3>为什么判定结果落库、而不是判定完就丢掉那一行</h3>
 * <p>命中的行照样入库，{@code isbot=1}，统计时排除。理由是这份规则<b>一定会改</b>：
 * 下一个人会往下面那个列表里加词，而加错一个词（比如加了 {@code "mobile"}）
 * 就会把一大批真实访客判成爬虫。如果当初是直接丢掉，那批数据就永远找不回来了；
 * 落库的话，改完规则用一句 UPDATE 就能重判。</p>
 *
 * <p>这也是为什么 {@code useragent} 那一列要存原始值——规则会变，原始 UA 不会。</p>
 */
@Component
public class BotSniffer
{
    /**
     * UA 里出现这些词就算爬虫。全部小写匹配。
     *
     * <p>⚠ 加词之前想清楚<b>误杀</b>：这个列表宁可漏判，不要误判。
     * 漏判的后果是数字偏高一点；误判的后果是某一类真实客户在报表里凭空消失，
     * 而那件事没有任何迹象能看出来。</p>
     *
     * <p>几个刻意<b>没有</b>放进来的词，别加：</p>
     * <ul>
     *   <li>{@code java} / {@code go-http} / {@code okhttp} —— 这类 UA 的请求根本不会
     *       执行 JS，到不了这个接口。放进来只是给列表加长度；</li>
     *   <li>{@code python} —— 同上，而且 {@code python} 这个词太短，容易撞上别的东西。</li>
     * </ul>
     */
    private static final List<String> BOT_TOKENS = List.of(
            // 搜索引擎。这几个确实会渲染 JS，是这一层的主要目标
            "googlebot", "bingbot", "baiduspider", "yandexbot", "duckduckbot",
            "slurp", "sogou", "360spider", "bytespider", "petalbot", "applebot",
            // 社交平台抓 OG 卡片的
            "facebookexternalhit", "twitterbot", "linkedinbot", "whatsapp",
            "telegrambot", "slackbot", "discordbot", "pinterest",
            // SEO / 监控 / 安全扫描器。这些是新站流量里最大的一块噪音
            "ahrefsbot", "semrushbot", "mj12bot", "dotbot", "blexbot",
            "dataforseo", "serpstatbot", "megaindex", "seokicks", "seznambot",
            "uptimerobot", "pingdom", "statuscake", "site24x7",
            // 无头浏览器。它们会执行 JS，所以真的会打到这个接口
            "headlesschrome", "phantomjs", "puppeteer", "playwright", "selenium",
            // 兜底的通用词。⚠ "bot" 放在最后且**不做单词边界匹配**是有意的：
            //    绝大多数没见过的爬虫 UA 里都带这三个字母，宁可多判几个未知的爬虫。
            //    已知的误杀风险：UA 里带 "Cubot"（一个手机品牌）的会被判成爬虫。
            //    接受它——那个牌子的用户不会是这个站的客户。
            "bot", "spider", "crawler", "crawl", "scrapy", "curl", "wget",
            "http-client", "feedfetcher", "archive.org_bot", "ia_archiver");

    /**
     * @param userAgent 原始 UA，可以为 null
     * @return true = 判定为爬虫
     */
    public boolean isBot(String userAgent)
    {
        // ⚠ 空 UA 一律算爬虫。任何真实浏览器都会发 User-Agent；
        //   空的只可能是脚本，或者有人手工构造了请求
        if (userAgent == null || userAgent.isBlank())
        {
            return true;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String token : BOT_TOKENS)
        {
            if (ua.contains(token))
            {
                return true;
            }
        }
        return false;
    }
}
