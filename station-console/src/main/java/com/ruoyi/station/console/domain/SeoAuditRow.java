package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * SEO 体检的取数行：一个商品 × 一种语言。
 *
 * <p>只给 {@code SeoAuditService} 用，不对外。它是一个<b>投影</b>，不是
 * {@link SiteGoods} 或 {@link SiteGoodsLang} 的子集——刻意只取体检要判的那几个值。</p>
 *
 * <h3>⚠ 为什么不直接查 SiteGoodsLang</h3>
 * <p>那个对象带 {@code description}，是 {@code mediumtext}。体检要一次看全站
 * 所有商品的所有语言，把富文本全读出来只为了数一下长度，几百个商品就是几十兆的
 * 无用传输。这里在 SQL 里就用 {@code char_length()} 算完，只回一个整数。</p>
 *
 * <h3>⚠ 只体检「已上架」的商品</h3>
 * <p>草稿和已下架的商品对外不可见（{@code SiteGoodsMapper} 里对外那几条查询永远带
 * {@code publishstatus = '已上架'}），它们的 SEO 问题不是问题。把它们算进来只会让
 * 体检表里堆满「还在做的商品缺日语标题」这种噪音，然后没人再看这张表。</p>
 */
public class SeoAuditRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String goodsCode;

    /** 商品内部名称。体检表上要显示得出「是哪个商品」，而标题本身可能正是缺失的那个值 */
    private String goodsName;

    /**
     * 这个商品有没有图。
     *
     * <p>在 SQL 里算：{@code siteimgs} / {@code imgurl} / {@code mainimgs} 任一非空即为有
     * ——这三列的回退顺序和前台取图的顺序一致（见 {@code u_s_sitegoods} 的列注释）。
     * 在 Java 里判就要把三个 2000 字符的列都读回来。</p>
     */
    private Integer hasImage;

    /** 语言代码。⚠ 可能是站点没开的语言：库里留着旧译文是常态，体检时要按站点当前开的语言过滤 */
    private String lang;

    private String title;

    /** 一句话卖点。⚠ 商品详情页的 meta description 首选它（见 products/[code].vue） */
    private String subtitle;

    /** 详情富文本的字符数。subtitle 为空时，meta description 从这里截前 160 字 */
    private Integer descLen;

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public Integer getHasImage() { return hasImage; }
    public void setHasImage(Integer hasImage) { this.hasImage = hasImage; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public Integer getDescLen() { return descLen; }
    public void setDescLen(Integer descLen) { this.descLen = descLen; }

    /** 有图吗 */
    public boolean imagePresent() { return hasImage != null && hasImage == 1; }

    /** 详情富文本有内容吗 */
    public boolean descPresent() { return descLen != null && descLen > 0; }
}
