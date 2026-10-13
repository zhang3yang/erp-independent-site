package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.DashboardFollowUp;
import com.ruoyi.system.domain.vo.DashboardPendingDoc;
import com.ruoyi.system.domain.vo.DashboardSalesAmount;
import com.ruoyi.system.domain.vo.DashboardSummary;
import com.ruoyi.system.domain.vo.DashboardTrendPoint;

/**
 * 首页经营看板的聚合查询。
 *
 * <p>这里只做「按商家汇总」，不承担任何明细查询——明细各模块自己的 mapper 已经有了，
 * 看板不该成为第二条读库存和流水的路径。</p>
 *
 * <p>月表后缀会拼进表名（{@code ${}}），所有入参必须先过
 * {@link com.ruoyi.system.utils.MonthTableSuffix}，并且确认表真的存在，
 * 否则整条 union 报 1146。</p>
 */
public interface DashboardMapper
{
    /**
     * 在库货值和 SKU 数。
     *
     * <p>只填 {@code stockAmount} 和 {@code skuCount} 两项，其余字段保持 VO 的初值，
     * 由服务层补齐。</p>
     */
    DashboardSummary selectStockStat(@Param("shopCode") String shopCode);

    /**
     * 待审核单据，按单据类型拆开，张数多的在前。
     *
     * <p>不另外提供「只要总数」的查询：总数由服务层把这里的结果加起来，
     * 两个数出自同一次查询就不会对不上。</p>
     *
     * @param suffixes     要扫的单据月表后缀，约束见类注释。空集合不要调用。
     * @param ownedDocType <b>只有这一种</b>单据类型要按创建人收窄（销售退货入库）。
     *                     {@code u_f_docmain} 是一张共享表，库存的入库/出库/调拨/生产都在里面，
     *                     那些是仓库的活儿、不归任何业务员，所以收窄不能一刀切给全表
     * @param owner        该类型的归属过滤值；{@code null} 表示能看全部，整个条件不加
     */
    List<DashboardPendingDoc> selectPendingDocumentsByType(@Param("shopCode") String shopCode,
            @Param("suffixes") List<String> suffixes, @Param("ownedDocType") String ownedDocType,
            @Param("owner") String owner);

    /**
     * 按月汇总出入库金额，一个月一行，只填 {@code month/inboundAmount/outboundAmount}。
     *
     * @param suffixes     要扫的库存流水月表后缀，约束见类注释。空集合不要调用。
     * @param excludeTypes 不计入的单据类型，传
     *                     {@link com.ruoyi.system.constant.InventoryDocumentTypes#INTERNAL_MOVE_TYPES}。
     *                     为空则不排除，出入库额会被调拨虚增。
     */
    List<DashboardTrendPoint> selectLedgerAmountByMonths(@Param("shopCode") String shopCode,
            @Param("suffixes") List<String> suffixes,
            @Param("excludeTypes") List<String> excludeTypes);

    /**
     * 按月汇总已完成收支，一个月一行，只填 {@code month/incomeAmount/expenseAmount}。
     *
     * @param suffixes 要扫的财务流水月表后缀，约束见类注释。空集合不要调用。
     */
    List<DashboardTrendPoint> selectFinanceAmountByMonths(@Param("shopCode") String shopCode,
            @Param("suffixes") List<String> suffixes);

    /**
     * 待审核的销售订单，按月表分行，只填 {@code docMonth} 和 {@code docCount}，月份新的在前。
     *
     * <p>分月给而不是直接给总数，是因为销售订单列表按月查：前端要靠这个月份把用户送到
     * 真有待审核单的那个月上，详见 {@link DashboardPendingDoc#getDocMonth()}。</p>
     *
     * @param suffixes 要扫的订单月表后缀，约束见类注释。空集合不要调用。
     * @param owner    只数这个业务员名下的单（{@code staffcode}）；{@code null} 表示不加这个
     *                 条件。取值来自 {@code DashboardScope.getSalesOwner()}，与订单列表同一道
     *                 横向权限——不跟着收窄的话，卡片上的数会比用户能翻到的单多
     */
    List<DashboardPendingDoc> selectPendingSalesOrdersByMonth(@Param("shopCode") String shopCode,
            @Param("suffixes") List<String> suffixes, @Param("owner") String owner);

    /**
     * 待审核的采购订单张数。
     *
     * <p>采购订单是<b>单表</b>（{@code sdsshop.u_p_purchase}），不分月，所以不需要后缀，
     * 也不存在「待审核的单在上个月表里」这个问题。</p>
     */
    int selectPendingPurchaseOrderCount(@Param("shopCode") String shopCode);

    /**
     * 某个月的销售额与订单张数，<b>按币种分组</b>，金额大的在前。
     *
     * <p>为什么必须分组：订单各带各的币种而系统里没有汇率，合成一个数就是造假，
     * 详见 {@link DashboardSalesAmount}。</p>
     *
     * @param suffix 订单月表后缀，调用前必须确认这张表存在
     * @param owner  口径同 {@link #selectPendingSalesOrdersByMonth}
     */
    List<DashboardSalesAmount> selectSalesAmountByCurrency(@Param("shopCode") String shopCode,
            @Param("suffix") String suffix, @Param("owner") String owner);

    /**
     * 待收货的采购单：未了结张数，以及其中过了预计到货日的张数。
     *
     * @param today 业务时区的今天。不用 {@code curdate()}：那是数据库所在时区的今天，
     *              和服务层其余按 Asia/Shanghai 算的日期会在跨日那几个小时里打架
     */
    DashboardFollowUp selectPurchaseFollowUp(@Param("shopCode") String shopCode,
            @Param("today") Date today);

    /**
     * 在产工单：未完工张数，以及其中过了计划生产日的张数。
     *
     * @param today 口径同 {@link #selectPurchaseFollowUp}
     */
    DashboardFollowUp selectProductionFollowUp(@Param("shopCode") String shopCode,
            @Param("today") Date today);
}
