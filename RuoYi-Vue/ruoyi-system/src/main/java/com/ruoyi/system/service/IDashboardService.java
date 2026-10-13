package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.vo.DashboardScope;
import com.ruoyi.system.domain.vo.DashboardSummary;
import com.ruoyi.system.domain.vo.DashboardTrendPoint;

/**
 * 首页经营看板。
 *
 * <p>只读聚合，不改任何业务数据。数据来自各模块已有的表，看板不维护自己的统计表——
 * 有了统计表就要考虑什么时候刷、刷失败了怎么办，而这几个数按需算一次就够快。</p>
 */
public interface IDashboardService
{
    /**
     * 卡片数据：待我处理、未处理预警、在库货值与 SKU 数、采购待收货、在产工单，
     * 以及本月的销售、出入库和收支。
     *
     * <p>「本月」按业务时区（Asia/Shanghai）的自然月，不是最近 30 天。</p>
     *
     * <h3>待我处理的三个队列</h3>
     *
     * <p>库存单据（{@code u_f_docmain_} 月表的待审核）、销售订单（{@code orderstate='待审核'}）、
     * 采购订单（{@code status='submitted'}）。这三条审批流在系统里各自独立、数据也不在一张表上，
     * 卡片把它们加在一起，点开的浮层再拆回三行各自跳转。</p>
     *
     * <p>三者都只回看 12 个月（采购订单是单表，不存在回看一说）。挂了一年还没审的单子是
     * 数据质量问题，不是今天的待办，放进来只会让这个数永远降不到 0；
     * 代价是真有这种单子时看板上看不见它。</p>
     *
     * <h3>本月销售的口径</h3>
     *
     * <p>只算审核通过且没作废的单（已备货/备货中/发货中/已完成），<b>按币种分组</b>给出金额——
     * 系统里没有汇率，跨币种相加是造假，见 {@code DashboardSalesAmount}。</p>
     *
     * @param scope 当前登录人看得到哪几块。无权的那几块压根不去查，对应字段留在初值上，
     *              并把对应的 {@code xxxVisible} 置为 false 让前端整张卡片不渲染
     */
    DashboardSummary selectSummary(String shopCode, DashboardScope scope);

    /**
     * 近 {@code months} 个月的出入库与收支趋势，按月份正序，最后一个元素是本月。
     *
     * <p>区间内没有月表的月份也会返回，四个金额都是 0。</p>
     *
     * @param months 月数，会被夹到 1..24
     * @param scope  出入库两条线看 {@code cost}、收支两条线看 {@code finance}；
     *               无权的那一侧压根不查，四个金额里对应的两个保持 0
     */
    List<DashboardTrendPoint> selectTrend(String shopCode, int months, DashboardScope scope);
}
