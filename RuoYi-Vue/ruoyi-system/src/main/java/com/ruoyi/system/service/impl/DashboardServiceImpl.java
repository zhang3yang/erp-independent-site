package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.vo.DashboardFollowUp;
import com.ruoyi.system.domain.vo.DashboardPendingDoc;
import com.ruoyi.system.domain.vo.DashboardSalesAmount;
import com.ruoyi.system.domain.vo.DashboardScope;
import com.ruoyi.system.domain.vo.DashboardSummary;
import com.ruoyi.system.domain.vo.DashboardTrendPoint;
import com.ruoyi.system.mapper.DashboardMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.FinanceTransactionMapper;
import com.ruoyi.system.mapper.InventoryLedgerMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.service.IDashboardService;
import com.ruoyi.system.service.IExchangeRateService;
import com.ruoyi.system.service.IInventoryWarningService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 首页经营看板实现。
 *
 * <p>月表后缀一律走 {@link MonthTableSuffix#queryRange}：它把「想查的月份」和
 * 「库里真有的月表」取交集。少了这一步，查一个还没开张的月份会让整条 union 报 1146，
 * 而首页是每个人登录后看到的第一屏，挂在这里最难看。</p>
 *
 * <p>每一段查询前都先问一句 {@link DashboardScope}：无权的那块<b>不查</b>，
 * 既是权限边界，也省掉一次跨月表的 union。</p>
 */
@Service
public class DashboardServiceImpl implements IDashboardService
{
    /** 与 MonthTableSuffix 内部保持一致，月份边界必须按同一个时区算 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 待审核单据回看的月数，理由见 IDashboardService#selectSummary */
    private static final int PENDING_LOOKBACK_MONTHS = 12;

    /** 趋势最多看两年：再长的区间 union 的月表数量就不好控了，看板也没人这么用 */
    private static final int MAX_TREND_MONTHS = 24;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private InventoryLedgerMapper ledgerMapper;

    @Autowired
    private DocumentMainMapper documentMainMapper;

    @Autowired
    private FinanceTransactionMapper transactionMapper;

    @Autowired
    private SalesOrderMapper orderMapper;

    @Autowired
    private IInventoryWarningService warningService;

    @Autowired
    private IExchangeRateService rateService;

    @Override
    public DashboardSummary selectSummary(String shopCode, DashboardScope scope)
    {
        DashboardSummary summary = dashboardMapper.selectStockStat(shopCode);
        if (summary == null)
        {
            // 这个商家一条库存记录都没有，聚合返回空行。走到这里说明是新开的商家，
            // 不是错误，给一份全 0 的卡片。
            summary = new DashboardSummary();
        }
        summary.setMonth(MonthTableSuffix.current());
        summary.setCostVisible(scope.isCost());
        if (!scope.isCost())
        {
            /*
             * 在库货值是成本额，归 price:cost 管。
             *
             * 它和 SKU 数出自同一条 SQL（一个 case、一个 count），没法只要后者，
             * 所以这里是查回来再丢掉，而不是像收支那样整段跳过。丢在服务层而不是
             * 让前端不画：前端不画遮不住任何东西，值已经在响应体里了。
             */
            summary.setStockAmount(BigDecimal.ZERO);
        }
        summary.setFinanceVisible(scope.isFinance());
        summary.setWarningCount(warningService.countActiveRecords(shopCode));

        /*
         * 订单月表的清单查一次，待审核队列和本月销售额都用它。
         *
         * 这是一条 information_schema 查询，库里表一多就不便宜，而首页是每个人登录后
         * 打开的第一屏——同一次请求里查两遍纯属浪费。
         */
        List<String> orderSuffixes = scope.isSales()
                ? MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes())
                : Collections.<String>emptyList();

        /*
         * 订单模块那道行级权限，看板上的销售数字要跟着一起收窄。
         *
         * 没有 order:sales:all 的业务员在订单列表里只看得到自己的单（按 staffcode）。
         * 看板不收窄的话，首页写着「销售订单 6 单」，点进去列表只有他自己的 1 张——
         * 同一块业务的两个出口对不上，用户只会以为列表坏了。
         *
         * 复用订单模块自己的常量，不在看板这边抄一份权限串：抄了就迟早有一处改漏，
         * 而改漏的后果是看板悄悄退回「谁都看全部」，没有任何报错。
         * 见 docs/订单只看自己的单（业务员权限）.md。
         */
        String salesOwner = scope.isSales()
                ? OwnershipScope.ownerFilter(SalesOrderServiceImpl.PERMI_VIEW_ALL)
                : null;

        // 总数由拆分结果加出来，不另查一次：两个数出自同一次查询，
        // 卡片上的总数和点开后各行之和永远对得上。
        List<DashboardPendingDoc> pendingDocs =
                selectPendingQueues(shopCode, scope, orderSuffixes, salesOwner);
        int pendingTotal = 0;
        for (DashboardPendingDoc pendingDoc : pendingDocs)
        {
            pendingTotal += pendingDoc.getDocCount();
        }
        summary.setPendingDocs(pendingDocs);
        summary.setPendingDocCount(pendingTotal);

        /*
         * 本月那四个金额直接借趋势那条路算，不另写一份 SQL。
         *
         * 这样「本月出库额」这张卡片和折线最右端那个点永远是同一个数。两套口径
         * 各算各的，迟早会因为某一边改了排除规则而对不上，而卡片和图就在同一屏上，
         * 对不上会被一眼看见。
         */
        List<DashboardTrendPoint> thisMonth = selectTrend(shopCode, 1, scope);
        if (!thisMonth.isEmpty())
        {
            DashboardTrendPoint point = thisMonth.get(thisMonth.size() - 1);
            summary.setInboundAmount(point.getInboundAmount());
            summary.setOutboundAmount(point.getOutboundAmount());
            summary.setIncomeAmount(point.getIncomeAmount());
            summary.setExpenseAmount(point.getExpenseAmount());
        }

        applySales(summary, shopCode, scope, orderSuffixes, salesOwner);
        applyFollowUps(summary, shopCode, scope);
        return summary;
    }

    @Override
    public List<DashboardTrendPoint> selectTrend(String shopCode, int months, DashboardScope scope)
    {
        int span = Math.min(Math.max(months, 1), MAX_TREND_MONTHS);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Date begin = startOfDay(today.withDayOfMonth(1).minusMonths(span - 1L));
        Date end = startOfDay(today);

        /*
         * 横轴用「区间里的全部月份」，不是「有月表的月份」。
         *
         * 缺月不给点的话，折线会把 7 月直接连到 9 月，视觉上 8 月成了一段斜线，
         * 看着像那个月有业务在爬坡——而真相是那个月一条流水都没有。
         */
        Map<String, DashboardTrendPoint> byMonth = new LinkedHashMap<>();
        List<String> axis = MonthTableSuffix.between(begin, end);
        // between 是倒序的，这里翻成正序：图表从左到右就是时间从早到晚
        for (int i = axis.size() - 1; i >= 0; i--)
        {
            byMonth.put(axis.get(i), new DashboardTrendPoint(axis.get(i)));
        }

        // 出入库金额存的是成本额，没有 price:cost 就整段跳过，理由同下面的收支
        List<String> ledgerSuffixes = scope.isCost()
                ? MonthTableSuffix.queryRange(ledgerMapper.selectLedgerTableSuffixes(), begin, end)
                : Collections.<String>emptyList();
        if (!ledgerSuffixes.isEmpty())
        {
            List<DashboardTrendPoint> rows = dashboardMapper.selectLedgerAmountByMonths(
                    shopCode, ledgerSuffixes, InventoryDocumentTypes.INTERNAL_MOVE_TYPES);
            for (DashboardTrendPoint row : rows)
            {
                DashboardTrendPoint point = byMonth.get(row.getMonth());
                if (point == null)
                {
                    continue;
                }
                point.setInboundAmount(row.getInboundAmount());
                point.setOutboundAmount(row.getOutboundAmount());
            }
        }

        // 没有财务权限就整段跳过。只是不往响应里填是不够的，查出来的数已经在进程里了，
        // 少查一次既是权限边界也是少一次跨月 union。
        List<String> financeSuffixes = scope.isFinance()
                ? MonthTableSuffix.queryRange(transactionMapper.selectTransactionTableSuffixes(), begin, end)
                : Collections.<String>emptyList();
        if (!financeSuffixes.isEmpty())
        {
            List<DashboardTrendPoint> rows =
                    dashboardMapper.selectFinanceAmountByMonths(shopCode, financeSuffixes);
            for (DashboardTrendPoint row : rows)
            {
                DashboardTrendPoint point = byMonth.get(row.getMonth());
                if (point == null)
                {
                    continue;
                }
                point.setIncomeAmount(row.getIncomeAmount());
                point.setExpenseAmount(row.getExpenseAmount());
            }
        }
        return new ArrayList<>(byMonth.values());
    }

    /**
     * 「待我处理」的三个队列。
     *
     * <p>库存单据那一条不额外判权：它是看板一开始就有的内容，而且没有一个统一的
     * 「库存单据列表」权限可以当判据——入库、出库、调拨各是各的菜单。另外两条各自看
     * 所属模块的列表权限，没授权的人连这一行都不该看见。</p>
     *
     * @param orderSuffixes 订单月表清单，由调用方查好传进来，见 selectSummary 里的注释
     * @param salesOwner    销售订单的归属过滤值，null 表示不过滤，同上
     */
    private List<DashboardPendingDoc> selectPendingQueues(String shopCode, DashboardScope scope,
            List<String> orderSuffixes, String salesOwner)
    {
        List<DashboardPendingDoc> rows = new ArrayList<>(selectPendingDocuments(shopCode));
        if (scope.isSales())
        {
            DashboardPendingDoc sales = selectPendingSalesOrders(shopCode, orderSuffixes, salesOwner);
            if (sales != null)
            {
                rows.add(sales);
            }
        }
        if (scope.isPurchase())
        {
            int count = dashboardMapper.selectPendingPurchaseOrderCount(shopCode);
            if (count > 0)
            {
                rows.add(pendingRow(DashboardPendingDoc.PURCHASE_ORDER, "采购订单", count, null));
            }
        }
        // 张数多的在前，与库存那条 SQL 自己的 order by 同口径：最该先处理的排最上面
        rows.sort(Comparator.comparingInt(DashboardPendingDoc::getDocCount).reversed());
        return rows;
    }

    /**
     * 库存单据里待审核的那些，按类型拆开。
     *
     * <p>这堆单据里只有<b>销售退货入库</b>一种有归属：它由业务员建单，归
     * {@code order:salesreturn:all} 管（{@code u_f_docmain.createuser}）。其余的
     * 入库、出库、调拨、生产、借出归还都是仓库的活儿，不归任何人，一并收窄会把那几张卡
     * 直接清零。所以归属条件只挂在这一个类型上，判据下沉到 SQL 里。</p>
     */
    private List<DashboardPendingDoc> selectPendingDocuments(String shopCode)
    {
        List<String> suffixes = lookbackSuffixes(documentMainMapper.selectDocumentMainTableSuffixes());
        if (suffixes.isEmpty())
        {
            return Collections.emptyList();
        }
        return dashboardMapper.selectPendingDocumentsByType(shopCode, suffixes,
                InventoryDocumentTypes.SALES_RETURN_INBOUND,
                OwnershipScope.ownerFilter(SalesReturnServiceImpl.PERMI_VIEW_ALL));
    }

    /**
     * 待审核的销售订单合成一行；一张都没有时返回 {@code null}，由调用方跳过。
     *
     * <p>SQL 是按月给的，这里加成一个总数，并把<b>最近</b>那个还有待审核订单的月份带上——
     * 销售订单列表按月查，前端要靠它把用户送到真有单的那个月去。</p>
     */
    private DashboardPendingDoc selectPendingSalesOrders(String shopCode, List<String> orderSuffixes,
            String salesOwner)
    {
        List<String> suffixes = lookbackSuffixes(orderSuffixes);
        if (suffixes.isEmpty())
        {
            return null;
        }
        List<DashboardPendingDoc> byMonth =
                dashboardMapper.selectPendingSalesOrdersByMonth(shopCode, suffixes, salesOwner);
        int total = 0;
        for (DashboardPendingDoc row : byMonth)
        {
            total += row.getDocCount();
        }
        if (total == 0)
        {
            return null;
        }
        // SQL 已按月份倒序，第一行就是最近那个还有待审核订单的月份
        return pendingRow(DashboardPendingDoc.SALES_ORDER, "销售订单", total, byMonth.get(0).getDocMonth());
    }

    private static DashboardPendingDoc pendingRow(String docType, String docTypeName, int count, String month)
    {
        DashboardPendingDoc row = new DashboardPendingDoc();
        row.setDocType(docType);
        row.setDocTypeName(docTypeName);
        row.setDocCount(count);
        row.setDocMonth(month);
        return row;
    }

    /**
     * 本月销售额与订单张数。金额按币种分组，理由见 {@link DashboardSalesAmount}。
     *
     * @param orderSuffixes 订单月表清单，由调用方查好传进来，见 selectSummary 里的注释
     * @param salesOwner    销售订单的归属过滤值，null 表示不过滤，同上
     */
    private void applySales(DashboardSummary summary, String shopCode, DashboardScope scope,
            List<String> orderSuffixes, String salesOwner)
    {
        summary.setSalesVisible(scope.isSales());
        summary.setSaleAmountVisible(scope.isSaleAmount());
        if (!scope.isSales())
        {
            return;
        }
        String suffix = MonthTableSuffix.current();
        if (!orderSuffixes.contains(suffix))
        {
            // 本月还没建过订单，月表压根不存在。拼进去是 1146，不是「0 单」。
            return;
        }
        List<DashboardSalesAmount> rows = dashboardMapper.selectSalesAmountByCurrency(shopCode, suffix, salesOwner);
        int orderCount = 0;
        for (DashboardSalesAmount row : rows)
        {
            orderCount += row.getOrderCount();
        }
        // 张数不涉及金额，看得到订单这块就给；金额那一份还要再有 price:sale
        summary.setSalesOrderCount(orderCount);
        if (scope.isSaleAmount())
        {
            // 按「汇率设置」里今天生效的汇率折人民币。看板看的是「本月到目前大概卖了多少」，
            // 用今天一个汇率足够；按每笔发货当天汇率精算的是销售利润报表。
            // 没录汇率的币种不进合计，单独列出来，前端提示——不能拿 1:1 糊弄。
            IExchangeRateService.RateBook rates = rateService.loadRateBook(shopCode);
            Date today = new Date();
            BigDecimal base = BigDecimal.ZERO;
            List<String> missing = new ArrayList<>();
            for (DashboardSalesAmount row : rows)
            {
                BigDecimal rate = rates.rateOn(row.getCurrency(), today);
                row.setRate(rate);
                if (rate == null)
                {
                    missing.add(row.getCurrency());
                    continue;
                }
                BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
                row.setAmountBase(amount.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
                base = base.add(row.getAmountBase());
            }
            summary.setSalesAmounts(rows);
            summary.setSalesAmountBase(base);
            summary.setSalesRateMissing(missing);
        }
    }

    /** 采购待收货与在产工单。两块都是纯张数，不涉及金额，所以只看各自模块的列表权限。 */
    private void applyFollowUps(DashboardSummary summary, String shopCode, DashboardScope scope)
    {
        summary.setPurchaseVisible(scope.isPurchase());
        summary.setProductionVisible(scope.isProduction());
        Date today = startOfDay(LocalDate.now(BUSINESS_ZONE));
        if (scope.isPurchase())
        {
            DashboardFollowUp followUp = dashboardMapper.selectPurchaseFollowUp(shopCode, today);
            if (followUp != null)
            {
                summary.setPurchaseReceivingCount(followUp.getActiveCount());
                summary.setPurchaseOverdueCount(followUp.getOverdueCount());
            }
        }
        if (scope.isProduction())
        {
            DashboardFollowUp followUp = dashboardMapper.selectProductionFollowUp(shopCode, today);
            if (followUp != null)
            {
                summary.setProductionActiveCount(followUp.getActiveCount());
                summary.setProductionOverdueCount(followUp.getOverdueCount());
            }
        }
    }

    /**
     * 待审核队列要扫的月表：最近 {@link #PENDING_LOOKBACK_MONTHS} 个月 ∩ 库里真有的月表。
     *
     * @param existing 从 information_schema 查出来的后缀，未必合法，queryRange 会先过滤
     */
    private List<String> lookbackSuffixes(List<String> existing)
    {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Date begin = startOfDay(today.withDayOfMonth(1).minusMonths(PENDING_LOOKBACK_MONTHS - 1L));
        return MonthTableSuffix.queryRange(existing, begin, startOfDay(today));
    }

    private static Date startOfDay(LocalDate date)
    {
        return Date.from(date.atStartOfDay(BUSINESS_ZONE).toInstant());
    }
}
