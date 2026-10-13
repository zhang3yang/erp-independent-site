package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * 独立站商品的一种语言的文案。对应表 {@code u_s_sitegoods_lang}。
 *
 * <p>一个商品有几种语言就有几行。取文案时按「当前语言 → 站点默认语言 →
 * {@code u_s_sitegoods.goodsname}」回退，所以某种语言没填不会让页面空着。</p>
 */
public class SiteGoodsLang implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String siteCode;
    private String goodsCode;
    private String lang;
    /** 站点标题。给国外客户看的，写法和内部品名差别很大 */
    private String title;
    private String subtitle;
    /** 详情富文本 */
    private String description;
    private String keywords;
    /** 规格译文 JSON，按默认语言的键当身份：{"默认键":{"k":"译键","v":"译值"}} */
    private String specsJson;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getSpecsJson() { return specsJson; }
    public void setSpecsJson(String specsJson) { this.specsJson = specsJson; }
}
