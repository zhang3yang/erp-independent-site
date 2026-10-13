package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteInquiry;

/**
 * 站点客户询盘。对应表 {@code u_s_inquiry}。
 *
 * <h3>⚠ bindBuyer 的含义变了，别按 ERP 那版理解</h3>
 * <p>这里以前写着「比 ERP 那版少一个方法：{@code bindBuyer}（转成 ERP 客户后回填客户编码）。
 * 那个动作往 ERP 的 {@code sdsshop.u_m_buyer} 里写数据，是一条互通，整个删掉」。</p>
 *
 * <p><b>那句话的前半仍然对：往 ERP 写的那个动作永远不会回来。</b>
 * 但方法本身回来了——它现在挂的是<b>本站自己的</b>客户档案
 * {@code u_s_buyer}（docs/独立站与ERP的边界.md §6c）。建库脚本里
 * {@code buyercode} 那一列的注释写的就是这件事：「本站客户编码……⚠ 不再指向 ERP 的
 * u_m_buyer」。</p>
 *
 * <p>判断标准很简单：这个 mapper 里的 SQL 不许出现 {@code sdsshop} / {@code sdssys} /
 * {@code sdsorder} 任何一个库名。带库名的 join 在本机（ERP 和站点共用一个实例）
 * 照样能跑，上线才报 Unknown database。</p>
 */
public interface SiteInquiryMapper
{
    /** 前台表单提交，唯一的写入口 */
    int insert(SiteInquiry inquiry);

    /**
     * 后台列表。字段为空即不过滤。
     *
     * <p>分页参数单独传，不塞进 query 对象：那个对象同时是前台提交询盘用的入参，
     * 往上加 offset/limit 就等于给公网接口开了两个可以塞任意值的字段。</p>
     */
    List<SiteInquiry> selectList(@Param("query") SiteInquiry query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteInquiry query);

    SiteInquiry selectById(@Param("id") Long id);

    /** 改处理状态和内部备注。客户填的内容一个字都不许改——那是原始凭据 */
    int updateHandle(SiteInquiry inquiry);

    /**
     * 只把状态往前推一格，别的列一个都不碰。
     *
     * <p>⚠ <b>不要用 {@link #updateHandle} 代替它。</b>那一条里 {@code remark} /
     * {@code owner} / {@code nextfollow} 都是<b>直赋值</b>（不是 coalesce），
     * 传一个只填了 id 和 status 的对象进去，会把内部备注、负责人、下次跟进日期
     * 一起清空——而那三样正是跟进模块攒出来的东西。</p>
     *
     * <p>调用方：{@code SiteFollowupService#addForInquiry}，写完一条跟进之后把
     * 「待处理」推成「已联系」。</p>
     */
    int bumpStatus(@Param("id") Long id, @Param("status") String status,
            @Param("handleUser") String handleUser);

    /**
     * 把这条询盘挂到<b>本站</b>客户档案上（{@code u_s_buyer.buyercode}）。
     *
     * <p>见类注释：这不是 ERP 那个「转成 ERP 客户」。</p>
     */
    int bindBuyer(@Param("id") Long id, @Param("buyerCode") String buyerCode);

    /**
     * 某个邮箱的历史询盘，客户详情页里列出来。
     *
     * <p>按<b>邮箱</b>查而不是按 buyercode：客户管理是后于询盘上线的，
     * 历史询盘的 buyercode 大多还是空的。</p>
     */
    List<SiteInquiry> selectByEmail(@Param("email") String email, @Param("limit") int limit);
}
