package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * 商品组合的一种语言的名称。对应表 {@code u_s_sitegoods_combo_lang}。
 *
 * <p>只有 {@code title} 一个内容字段。价格、图片、排序都在
 * {@link SiteGoodsCombo} 上——那些改一次七种语言一起变，放在这里就要改七行，
 * 还会出现「六种语言 199、一种语言 299」这种没人发现的错。</p>
 */
public class SiteGoodsComboLang implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String siteCode;
    private String goodsCode;
    private String comboCode;
    private String lang;
    /** 该语言下的组合名。没填时接口退回站点默认语言那一条 */
    private String title;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getComboCode() { return comboCode; }
    public void setComboCode(String comboCode) { this.comboCode = comboCode; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
