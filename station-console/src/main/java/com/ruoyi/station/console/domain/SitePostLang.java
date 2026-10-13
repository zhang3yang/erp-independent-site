package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * 博客文章的一种语言的文案。对应表 {@code u_s_post_lang}。
 *
 * <p>一篇文章有几种语言的译文就有几行。分语言存的好处是能做到
 * <b>「日语还没翻译好就先不发日语版」</b>——这是博客最常见的工作方式，
 * 内容是一篇一篇、一种语言一种语言攒出来的。</p>
 *
 * <h3>⚠⚠ 和 {@link SiteGoodsLang} 的关键区别：不回退</h3>
 * <p>商品文案按「当前语言 → 站点默认语言 → 内部品名」回退，某种语言没填不会让
 * 页面空着。文章<b>不回退</b>：{@link #title} 为空就是「这个语言版本还没有」，
 * 前台列表里不出现、详情 404、sitemap 不列、hreflang 也不列。</p>
 *
 * <p>理由见 {@link SitePost} 的类注释——一篇英文长文摆在日语站上对读者没价值，
 * 而且会被 Google 判成「hreflang 说是日语、内容是英文」。</p>
 */
public class SitePostLang implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String postCode;
    private String lang;

    /**
     * 标题。进 {@code <title>} 和 {@code og:title}。
     *
     * <p>⚠ <b>这一列是「这个语言版本存不存在」的判据。</b>为空则整篇文章在这个语言下
     * 不可见——不是显示一个空标题，是根本不出现。</p>
     */
    private String title;

    /**
     * 摘要。列表页卡片的正文 + {@code meta description}。
     *
     * <p>留空则由正文剥标签截前 160 字兜底（同商品详情页的做法，见
     * {@code products/[code].vue}）。截出来的句子通常是断的，所以 SEO 体检里
     * 「没填摘要」是一条「中」级问题。</p>
     */
    private String summary;

    /**
     * 正文富文本。
     *
     * <p>⚠ <b>对外输出前必须过 {@code RichTextCleaner} 的白名单</b>——和商品详情
     * 同一条规矩，不因为换了张表就放宽。管理台那个输入框是纯文本域（手写 HTML），
     * 不是所见即所得编辑器，这也是 {@code RichTextCleaner} 敢不放行 {@code style}
     * 属性的前提。</p>
     */
    private String content;

    /**
     * SEO 关键词，逗号分隔。
     *
     * <p>⚠ Google 早就不用 {@code meta keywords} 了，前台也不输出它。留这一列是为了
     * 和 {@code u_s_sitegoods_lang} 结构一致，以及给将来的站内检索留余地。</p>
     */
    private String keywords;

    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    /** 这个语言版本算存在吗。⚠ 判据只有 title，见它的字段注释 */
    public boolean present()
    {
        return title != null && !title.trim().isEmpty();
    }
}
