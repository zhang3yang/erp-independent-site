package com.ruoyi.common.utils.html;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 富文本白名单过滤：留下排版标签，剥掉一切能执行脚本的东西。
 *
 * <h3>为什么不用 {@link EscapeUtil#clean}</h3>
 * <p>{@code EscapeUtil.clean} 用的是 {@link HTMLFilter} 的默认白名单，只放行
 * {@code a / img / b / strong / i / em} 六个标签。商品详情里的段落、列表、表格会被整段吃掉，
 * 商家会以为「保存没生效」。这里换一份面向展示的白名单，机制还是 {@code HTMLFilter}
 * 那一套，不额外引 jsoup。</p>
 *
 * <h3>挡住了什么</h3>
 * <ul>
 *   <li><b>{@code <script>} / {@code <iframe>} / {@code <style>}</b> —— 不在白名单里，整段剥掉；</li>
 *   <li><b>事件属性</b>（{@code onclick}、{@code onerror} …）—— 属性是按标签逐个白名单放行的，
 *       没列进去的一律不输出；</li>
 *   <li><b>{@code javascript:} 协议</b> —— {@code href} / {@code src} 会过协议白名单，
 *       只剩 http / https / mailto；</li>
 *   <li><b>{@code style} 属性</b> —— 故意不放行。它是 XSS 的常见载体，而商品详情是纯文本域
 *       录入的（不是所见即所得编辑器），不需要行内样式。</li>
 * </ul>
 *
 * <h3>为什么每次都 new 一个 HTMLFilter</h3>
 * <p>{@link HTMLFilter#filter} 第一件事就是 {@code reset()}，改的是实例上的
 * {@code vTagCounts}。两个线程共用一个实例会互相踩标签计数，输出是坏的 HTML。
 * 复用不了，只能每次新建——它只是几个数组的引用拷贝，代价可以忽略。</p>
 *
 * @see HTMLFilter
 */
public class RichTextCleaner
{
    /** 允许的标签，以及每个标签允许保留的属性 */
    private static final Map<String, List<String>> ALLOWED = new LinkedHashMap<>();

    /** 必须自闭合的标签 */
    private static final String[] SELF_CLOSING = { "img", "br", "hr" };

    /** 必须成对出现的标签。空的会被 vRemoveBlanks 清掉，所以只列内联的那几个 */
    private static final String[] NEED_CLOSING = { "a", "b", "strong", "i", "em", "u", "span" };

    /** {@code href} / {@code src} 只认这三种协议，{@code javascript:} 在这里被挡掉 */
    private static final String[] PROTOCOLS = { "http", "https", "mailto" };

    private static final String[] PROTOCOL_ATTS = { "href", "src" };

    /** 空标签直接删。只删内联的：空的 {@code <p></p>} 是商家用来空行的，删了排版会变 */
    private static final String[] REMOVE_BLANKS = { "a", "b", "strong", "i", "em", "u", "span" };

    private static final String[] ENTITIES = { "amp", "gt", "lt", "quot" };

    static
    {
        // 纯排版标签，不带任何属性
        for (String tag : Arrays.asList(
                "p", "br", "hr", "div", "span", "blockquote", "pre", "code",
                "b", "strong", "i", "em", "u", "s", "sub", "sup",
                "h1", "h2", "h3", "h4", "h5", "h6",
                "ul", "ol", "li", "dl", "dt", "dd",
                "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption"))
        {
            ALLOWED.put(tag, List.of());
        }
        // 表格的跨行跨列要留，不然商家画的规格表会散架
        ALLOWED.put("td", Arrays.asList("colspan", "rowspan"));
        ALLOWED.put("th", Arrays.asList("colspan", "rowspan"));
        ALLOWED.put("a", Arrays.asList("href", "target", "title"));
        ALLOWED.put("img", Arrays.asList("src", "alt", "title", "width", "height"));
    }

    private RichTextCleaner()
    {
    }

    /**
     * 过滤一段富文本。
     *
     * @param html 商家录入的原文，可以为 null
     * @return 过滤后的 HTML；入参为 null 或空白时原样返回，不把 null 变成空串
     */
    public static String clean(String html)
    {
        if (html == null || html.trim().isEmpty())
        {
            return html;
        }
        return newFilter().filter(html);
    }

    /** 见类注释：HTMLFilter 有可变状态，不能共用实例 */
    private static HTMLFilter newFilter()
    {
        Map<String, Object> conf = new HashMap<>();
        conf.put("vAllowed", new HashMap<>(ALLOWED));
        conf.put("vSelfClosingTags", SELF_CLOSING);
        conf.put("vNeedClosingTags", NEED_CLOSING);
        conf.put("vDisallowed", new String[] {});
        conf.put("vAllowedProtocols", PROTOCOLS);
        conf.put("vProtocolAtts", PROTOCOL_ATTS);
        conf.put("vRemoveBlanks", REMOVE_BLANKS);
        conf.put("vAllowedEntities", ENTITIES);
        conf.put("stripComment", Boolean.TRUE);
        conf.put("encodeQuotes", Boolean.TRUE);
        // false = 落单的尖括号按文本转义掉，而不是猜一个标签出来。
        // 猜出来的标签可能正好凑成一个能执行的东西，这里宁可显示成字面量。
        conf.put("alwaysMakeTags", Boolean.FALSE);
        return new HTMLFilter(conf);
    }
}
