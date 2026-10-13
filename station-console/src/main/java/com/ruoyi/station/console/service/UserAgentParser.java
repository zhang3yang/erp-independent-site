package com.ruoyi.station.console.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 从 User-Agent 里解析设备类型和浏览器。填 {@code u_s_pageview.device} / {@code browser}。
 *
 * <p>⚠ <b>不采集任何新数据。</b>原始 UA 从 2026-09-08 建表起就一直在存
 * （当时写的理由是「判定规则会改，原始 UA 不会」），这个类只是把它解析出来。
 * 所以规则改了可以拿存量 UA 重新跑一遍回填。</p>
 *
 * <h3>⚠ 判定顺序就是这个类的全部内容</h3>
 * <p>UA 这套东西是三十年兼容包袱堆出来的，每个浏览器都在自己的 UA 里假装是别人：</p>
 * <ul>
 *   <li>Edge 的 UA 里有 {@code Chrome}，也有 {@code Safari}；</li>
 *   <li>Chrome 的 UA 里有 {@code Safari}；</li>
 *   <li>Opera 的 UA 里有 {@code Chrome} 和 {@code Safari}；</li>
 *   <li>三星浏览器的 UA 里有 {@code Chrome} 和 {@code Safari}。</li>
 * </ul>
 * <p>也就是说 <b>Safari 必须最后判，Chrome 必须在 Safari 之前、在所有 Chrome 套壳之后</b>。
 * 调换任何两个分支都<b>不会报错</b>，只会让数字偏——比如把 Chrome 提到 Edge 前面，
 * 报表上 Edge 会归零而 Chrome 多一块。这种错没有任何迹象，
 * 只能靠「Edge 怎么一个都没有」这种直觉发现。<b>改这个方法之前先读完这段。</b></p>
 *
 * <h3>能力边界</h3>
 * <p>这是个够用的粗分类，不是 UA 解析库（那种东西要跟着几千条正则规则一起维护）。
 * 它回答的是「买家用手机还是电脑看货」——这个问题只需要三档，
 * 而且答错个别冷门浏览器不影响任何决策。</p>
 *
 * <p>不做的事：不认版本号、不认操作系统、不认具体机型。真要那些就该上
 * 一个真的 UA 库，而不是把这里越写越长。</p>
 */
@Component
public class UserAgentParser
{
    /** 手机 */
    public static final String MOBILE = "mobile";
    /** 平板 */
    public static final String TABLET = "tablet";
    /** 桌面 */
    public static final String DESKTOP = "desktop";

    /**
     * 设备类型。UA 为空返回 null。
     *
     * <p>⚠ {@code ipad} 必须在 {@code iphone} 之前判吗？不必——两者互斥。
     * 但 <b>android 的两个分支顺序是要紧的</b>：安卓手机的 UA 里同时有
     * {@code Android} 和 {@code Mobile}，安卓平板只有 {@code Android}。
     * 所以先判「android 且 mobile」= 手机，剩下的 android = 平板。
     * 反过来写的话所有安卓手机都会被算成平板。</p>
     */
    public String device(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
        {
            // 空 UA 不猜。这种行本来就会被 BotSniffer 判成爬虫（isbot=1），
            // 统计时排除掉了，给它编一个 desktop 只会污染数据
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);

        // iPadOS 13 之后，Safari 的 UA 里**不再有 ipad**，而是伪装成 Macintosh。
        // 那种 iPad 会被算成 desktop——这是已知且接受的偏差：想区分要靠触摸点数
        // 之类的浏览器侧探测，而那要往埋点里加字段，为这一档不值得
        if (ua.contains("ipad"))
        {
            return TABLET;
        }
        if (ua.contains("iphone") || ua.contains("ipod") || ua.contains("windows phone"))
        {
            return MOBILE;
        }
        if (ua.contains("android"))
        {
            // 见方法注释：这两个分支的顺序不能换
            return ua.contains("mobile") ? MOBILE : TABLET;
        }
        if (ua.contains("tablet") || ua.contains("kindle") || ua.contains("silk"))
        {
            return TABLET;
        }
        // "mobi" 兜底，覆盖一批小众移动浏览器。放在最后：桌面 UA 里不会有它
        if (ua.contains("mobi"))
        {
            return MOBILE;
        }
        return DESKTOP;
    }

    /**
     * 浏览器。UA 为空返回 null。
     *
     * <p>⚠ <b>这些 if 的顺序不能动</b>，理由见类注释。从最会假装别人的往最老实的排：
     * Edge → Opera → 三星 → Firefox → Chrome → Safari。</p>
     */
    public String browser(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
        {
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);

        // ⚠ 用 "edg" 不是 "edge"：新版 Edge 的标识是 `Edg/`（没有 e），
        //   而老的 EdgeHTML 版是 `Edge/`。"edg" 两个都能盖住。
        //   反过来只写 "edge" 会漏掉现在绝大多数的 Edge 用户
        if (ua.contains("edg"))
        {
            return "Edge";
        }
        // Opera 现在的标识是 `OPR/`，老版是 `Opera`
        if (ua.contains("opr/") || ua.contains("opera"))
        {
            return "Opera";
        }
        // 三星浏览器在东南亚和中东有相当占比，而它的 UA 里有 Chrome——
        // 不单独判就全被算成 Chrome
        if (ua.contains("samsungbrowser"))
        {
            return "Samsung";
        }
        if (ua.contains("firefox") || ua.contains("fxios"))
        {
            return "Firefox";
        }
        // ⚠ iOS 上的 Chrome 标识是 `CriOS`，不是 `Chrome`。只判 chrome 会把
        //   iPhone 上的 Chrome 用户算成 Safari
        if (ua.contains("chrome") || ua.contains("crios") || ua.contains("chromium"))
        {
            return "Chrome";
        }
        // Safari 必须最后：上面每一种的 UA 里都有 safari
        if (ua.contains("safari"))
        {
            return "Safari";
        }
        // IE。放在 Safari 后面无所谓——IE 的 UA 里没有 safari
        if (ua.contains("msie") || ua.contains("trident"))
        {
            return "IE";
        }
        return "Other";
    }
}
