package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * 站点分类在某种语言下的显示名。对应表 {@code u_s_sitecategory_lang}。
 *
 * <h3>它不是「分类表」</h3>
 * <p>这个站**没有**分类表。分类的身份仍然是 {@code u_s_sitegoods.sitecategory} 那一列
 * 自由文本，它同时兼着筛选键（URL 的 {@code ?cat=}）、两级层次的载体（靠分隔符）、
 * 以及没有译名时的兜底文字。本类只接管「显示文字」这一件事。</p>
 *
 * <p>这么分的好处是 <b>URL 永远是原串，切语言不断链</b>——否则日文页的分类链接带着
 * 日文名字，切回中文就筛不出东西了。</p>
 *
 * <h3>⚠ {@link #catKey} 是「路径的一级」，不是整串</h3>
 * <p>分类 {@code 加压疗法/腿部系统} 拆成两行：{@code 加压疗法} 一行、
 * {@code 加压疗法/腿部系统} 一行。整串译一次的话，大组「加压疗法」下面挂 5 个子分类
 * 就要翻 5 遍、存 5 份，很可能翻出 5 种写法，侧栏的组标题就飘了。</p>
 *
 * <p>用完整前缀而不是光秃秃的一段，是因为 {@code 腿部系统} 可能同时挂在两个大组下，
 * 语义未必相同。</p>
 *
 * <h3>只存非默认语言</h3>
 * <p>默认语言的显示名就是 {@code catKey} 的最后一段原文，存一份一样的进来没有意义，
 * 还会多出一份可能和原文不同步的数据。</p>
 */
public class SiteCategoryLang implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String siteCode;

    /** 分类路径的一级，取完整前缀。如 {@code 加压疗法}、{@code 加压疗法/腿部系统}。见类注释 */
    private String catKey;

    /** 语言代码。⚠ 不存站点默认语言 */
    private String lang;

    /** 该语言下的显示名。空 = 前台显示 {@code catKey} 最后一段原文 */
    private String name;

    private String createTime;
    private String updateTime;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getCatKey() { return catKey; }
    public void setCatKey(String catKey) { this.catKey = catKey; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
