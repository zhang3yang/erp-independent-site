package com.ruoyi.station.console.mapper;

import java.util.List;
import com.ruoyi.station.console.domain.SeoAuditRow;
import com.ruoyi.station.console.domain.SiteSeo;

/**
 * 站长工具。对应表 {@code u_s_seo}（只有一行）。
 *
 * <h3>⚠ 这个 mapper 里有一条不查自己表的语句</h3>
 * <p>{@link #selectAuditRows()} 查的是 {@code u_s_sitegoods} 和
 * {@code u_s_sitegoods_lang}。这打破了本项目「一张表一个 mapper」的惯例，是有意的：</p>
 *
 * <ul>
 *   <li>它是<b>只读投影</b>，只为 SEO 体检存在，别的地方不会调；</li>
 *   <li>放进 {@code SiteGoodsMapper} 的话，那个 mapper 的类注释里
 *       「对外和后台的语句刻意分开写」那条规矩就多出一个既不对外、也不是后台
 *       增删改的第三类，比放在这里更难说清；</li>
 *   <li>体检的取数口径（只算已上架、图看三列回退、描述只要长度）是<b>体检规则的一部分</b>，
 *       跟着规则一起改比跟着商品管理一起改更对。</li>
 * </ul>
 *
 * <p>换句话说：它按<b>模块</b>归属，不按表归属。真要挪走的时候记得它和
 * {@code SeoAuditService} 是一对。</p>
 */
public interface SiteSeoMapper
{
    // ==================== 配置（u_s_seo） ====================

    /**
     * 取那唯一一行。
     *
     * <p>⚠ <b>查不到时返回 null 是正常的</b>，不是错误：老库没跑
     * {@code 2026-09-08-seo-tools.sql} 就是这个结果。调用方
     * （{@code SiteSeoService#current}）负责换成 {@link SiteSeo#defaults()}，
     * 而那份默认值等于这张表不存在时前台的原有行为。</p>
     */
    SiteSeo selectSeo();

    /**
     * 存配置。有则更新无则插入。
     *
     * <p>用 upsert 而不是 update：迁移脚本里那条 {@code INSERT IGNORE} 有可能没跑
     * （老库、或者有人只建了表），那时候 update 会<b>影响 0 行然后静默成功</b>——
     * 界面提示「已保存」，刷新一下全没了。</p>
     */
    int upsert(SiteSeo seo);

    // ==================== SEO 体检取数（u_s_sitegoods + _lang） ====================

    /**
     * 体检要判的全部数据：每个<b>已上架</b>商品 × 每一条已有译文。
     *
     * <p>一条语句拿全，不做 N+1。⚠ 用的是 {@code left join}：一个商品如果<b>一条译文
     * 都没有</b>，也必须出现在结果里（{@code lang} 为 null）——那正是最严重的一种问题，
     * 用 {@code inner join} 的话它会安静地消失，体检报「一切正常」。</p>
     */
    List<SeoAuditRow> selectAuditRows();

    /** 已上架商品总数。体检页上那句「扫了 N 个商品」用它，比在 Java 里 distinct 一遍清楚 */
    int countPublishedGoods();
}
