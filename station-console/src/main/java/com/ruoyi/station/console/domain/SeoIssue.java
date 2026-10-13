package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * SEO 体检查出来的一条问题。
 *
 * <h3>⚠ 体检结果不落库</h3>
 * <p>这个对象只在一次请求里活着。落库的话就会出现「已经改好了但体检表还在报警」
 * ——一份需要人去手动刷新才准的检查清单，比没有清单更糟，因为它会被信。</p>
 *
 * <p>代价是每次打开都要扫一遍全站商品。可以接受：商品在几百量级，两条查询就够
 * （见 {@code SiteSeoMapper#selectAuditRows}），而这是个按需打开的后台页面。</p>
 *
 * <h3>严重程度怎么分</h3>
 * <ul>
 *   <li>{@link #LEVEL_HIGH}——<b>那个页面现在就是坏的</b>：标题是另一种语言、
 *       meta description 整个空。搜索结果里长什么样已经错了。</li>
 *   <li>{@link #LEVEL_MEDIUM}——<b>能用但会被打折</b>：标题过长被截断、
 *       多个商品标题重复互相抢排名。</li>
 *   <li>{@link #LEVEL_LOW}——<b>可以慢慢补</b>：没有图，og:image 回落到 logo。</li>
 * </ul>
 *
 * <p>分级不是为了好看，是为了这张表能被用完：一个不分级的清单上，
 * 「缺日语标题」和「标题多了 3 个字符」长得一样重，于是两条都不会被处理。</p>
 */
public class SeoIssue implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String LEVEL_HIGH = "高";
    public static final String LEVEL_MEDIUM = "中";
    public static final String LEVEL_LOW = "低";

    /** 体检对象：商品 */
    public static final String TARGET_GOODS = "商品";
    /** 体检对象：首页装修文案（contentjson 里的 seoTitle / seoDescription） */
    public static final String TARGET_HOME = "首页";
    /** 体检对象：站点级（语言开了但没有内容之类） */
    public static final String TARGET_SITE = "站点";

    private String level;

    /** 规则标识，如 {@code goods.title.missing}。给前端做分组和排序用，不显示给人看 */
    private String rule;

    private String target;

    /** 商品编码；非商品类问题为空 */
    private String targetCode;

    /** 显示名。商品是内部品名，首页/站点是一句话说明 */
    private String targetName;

    /** 哪种语言的问题；不分语言的问题为空 */
    private String lang;

    /** 给人看的一句话。⚠ 要说清「哪里、什么问题、什么后果」，不要只说「标题缺失」 */
    private String message;

    public SeoIssue() {}

    public SeoIssue(String level, String rule, String target, String targetCode,
            String targetName, String lang, String message)
    {
        this.level = level;
        this.rule = rule;
        this.target = target;
        this.targetCode = targetCode;
        this.targetName = targetName;
        this.lang = lang;
        this.message = message;
    }

    /** 商品类问题 */
    public static SeoIssue goods(String level, String rule, String code, String name,
            String lang, String message)
    {
        return new SeoIssue(level, rule, TARGET_GOODS, code, name, lang, message);
    }

    /** 首页装修文案类问题 */
    public static SeoIssue home(String level, String rule, String lang, String message)
    {
        return new SeoIssue(level, rule, TARGET_HOME, null, "首页装修文案", lang, message);
    }

    /** 站点级问题 */
    public static SeoIssue site(String level, String rule, String lang, String message)
    {
        return new SeoIssue(level, rule, TARGET_SITE, null, "站点设置", lang, message);
    }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** 排序权重，高在前。前端也能自己排，但让后端出一个确定的顺序省得两处不一致 */
    public int levelWeight()
    {
        if (LEVEL_HIGH.equals(level)) return 0;
        if (LEVEL_MEDIUM.equals(level)) return 1;
        return 2;
    }
}
