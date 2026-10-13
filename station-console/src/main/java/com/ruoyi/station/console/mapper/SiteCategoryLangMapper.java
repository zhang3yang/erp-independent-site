package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteCategoryLang;

/**
 * 站点分类的多语言显示名。对应表 {@code u_s_sitecategory_lang}。
 *
 * <h3>为什么没有 delete / deleteMissing</h3>
 * <p>译名是<b>站点级共享</b>的：同一个分类挂在十个商品上，译名只有一份。
 * 所以这里<b>只有 upsert 没有整份替换</b>——保存一个商品时按「提交的就是全部、
 * 库里多出来的删掉」去做，会把别的分类的译名一起删掉。</p>
 *
 * <p>商家改了某个商品的分类之后，旧分类的译名行就没商品用了。也不删：那些行可能还被
 * 别的商品用着；就算真没人用也不碍事——前台侧栏是从 {@code select distinct sitecategory}
 * 出来的，没有商品挂着的分类根本不会出现，孤儿行只是躺着，改回去还能直接复用。</p>
 *
 * <h3>方法签名里为什么没有 shopCode / siteCode</h3>
 * <p>一个部署一个站，这两列不是筛选维度。写入时由 {@code SiteContext} 填，
 * 见 {@link SiteInfoMapper} 的类注释。</p>
 */
public interface SiteCategoryLangMapper
{
    /**
     * 某种语言下的全部译名。前台 {@code /openapi/site/info} 用它拼 {@code categoryNames} 字典。
     *
     * <p>没译名的分类不在结果里，前台据此回退显示原文。</p>
     */
    List<SiteCategoryLang> selectByLang(@Param("lang") String lang);

    /** 全站所有语言的译名。管理台「分类译名」那张表一次load 出来 */
    List<SiteCategoryLang> selectAll();

    /** 存一条。有则更新无则插入。⚠ 这是本表唯一的写入方式，见类注释 */
    int upsert(SiteCategoryLang item);
}
