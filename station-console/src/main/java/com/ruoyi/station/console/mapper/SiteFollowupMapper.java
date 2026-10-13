package com.ruoyi.station.console.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteFollowup;

/**
 * 跟进记录。对应表 {@code u_s_followup}，另有两条查 {@code u_s_inquiry} 的看板语句。
 *
 * <h3>⚠ 这里没有 update，也没有按 id 的 delete</h3>
 * <p>跟进记录是<b>只增不改</b>的：它是一条线索的历史，改过的历史没有价值。
 * 写错了就再写一条更正——这也是销售自己记录时的习惯。</p>
 *
 * <p>唯一的删除是 {@link #deleteByTargets}，跟着询盘一起删（询盘没了，
 * 它的跟进记录留着就是孤儿行，而所有读都从询盘那边起手，永远不会被发现）。</p>
 *
 * <h3>方法签名里为什么没有 siteCode</h3>
 * <p>一个部署一个站（boundary §5.3）。写入时由 Service 从站点档案填。</p>
 */
public interface SiteFollowupMapper
{
    /** 一个对象的全部跟进记录，时间倒序（最新的在最上面，那是打开详情最想先看到的） */
    List<SiteFollowup> selectByTarget(@Param("targetType") String targetType,
            @Param("targetCode") String targetCode);

    int insert(SiteFollowup followup);

    /** 跟着询盘一起删。⚠ 询盘删了不删这个就是孤儿行，而没有任何查询会发现它们 */
    int deleteByTargets(@Param("targetType") String targetType,
            @Param("targetCodes") String[] targetCodes);

    // ==================== 跟进看板（查 u_s_inquiry，不是本表） ====================
    //
    // ⚠ 这两条按**模块**归属放在这里，不按表。它们服务的是「营销管理」那一页，
    //   而那一页的口径（什么叫「该跟了」、参照时间怎么算）是跟进规则的一部分，
    //   跟着规则一起改比跟着询盘管理一起改更对。同 SiteSeoMapper 里那条体检语句。

    /**
     * 跟进看板的清单。
     *
     * <p>一行一条<b>还没结案</b>的询盘（状态不是「已成交」也不是「无效」），带上：
     * 负责人、下次跟进日期、最后一次被碰的时间、已经放了多少天。</p>
     *
     * <p>⚠ 「最后被碰的时间」是
     * {@code COALESCE(最后一条跟进, handletime, createtime)} ——三级回退缺一不可：
     * 一条刚进来、没人跟过也没人处理过的询盘，参照时间只能是它<b>收到的时间</b>，
     * 否则它的「放了多少天」是 NULL，会从「最该被看见」变成排序垫底。</p>
     *
     * @param bucket 要哪一堆：{@code overdue} 逾期 / {@code today} 今天要跟 /
     *               {@code unowned} 没人认领 / {@code all} 全部未结案
     * @param staleDays 多少天没动算「躺太久」。只在 {@code bucket=overdue} 时参与判断
     */
    List<Map<String, Object>> selectBoard(@Param("bucket") String bucket,
            @Param("staleDays") int staleDays, @Param("owner") String owner);

    /**
     * 看板顶部那四个数字。
     *
     * <p>⚠ 和 {@link #selectBoard} <b>必须用同一套口径</b>，否则会出现
     * 「顶上说 3 条逾期、点进去列表是 5 条」——那种不一致会让人不再信这一页。
     * 两条语句共用同一段 {@code <sql id="boardBase">}，就是为了这个。</p>
     */
    Map<String, Object> selectBoardStat(@Param("staleDays") int staleDays);
}
