package com.ruoyi.system.domain.vo;

import com.ruoyi.common.utils.PriceMaskUtils;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 首页看板对当前登录人可见到哪一步。
 *
 * <p>看板横跨库存、单据、订单、采购、生产和财务，而这几块在系统里本来就是分开授权的。
 * 拿到 {@code dashboard:overview:query} 只说明这个人能看见看板这块地方，
 * <b>不等于</b>他能看见上面每一个数——所以每一块再各自把一道。</p>
 *
 * <h3>为什么判据取各模块的「列表」权限</h3>
 *
 * <p>能翻某个模块的明细列表，看它的合计数就不算越界；反过来不成立。取列表权限当判据，
 * 看板就永远不会比模块本身更宽松，也不用为看板另立一套权限点让商家再配一遍。</p>
 *
 * <h3>金额另外两道</h3>
 *
 * <p>{@link #isCost()} 和 {@link #isSaleAmount()} 走的是价格可见性那套
 * （{@code price:cost} / {@code price:sale}，见 {@code docs/金额按岗位可见.md}）。
 * 「能看采购单」和「能看采购单上的钱」是两件事，仓库岗位典型地只有前者。</p>
 *
 * <h3>无权时是不查，不是查了不给</h3>
 *
 * <p>各处拿到 false 一律跳过那段查询：既是权限边界，也省掉跨月表的 union。
 * 前端拿到对应的 {@code xxxVisible=false} 就整张卡片不渲染——不是显示一个 0。
 * 显示 0 等于告诉用户「这个月一单都没有」，那是一件假事。</p>
 *
 * <h3>还有一道横向的，不在这个类里</h3>
 *
 * <p>这里管的都是纵向权限（这块给不给看）。订单模块另有一道行级的：没有
 * {@code order:sales:all} 的业务员在订单列表里只看得到自己的单，看板上的销售数字必须
 * 跟着一起收窄，否则首页说「本月 37 单」、点进列表只有 5 张，用户会以为列表坏了。</p>
 *
 * <p>那一道留在 {@code DashboardServiceImpl} 里算，用的是
 * {@code OwnershipScope.ownerFilter(SalesOrderServiceImpl.PERMI_VIEW_ALL)}——
 * 直接复用订单模块自己的常量，不在这里抄一份字面量。
 * 见 {@code docs/订单只看自己的单（业务员权限）.md}。</p>
 *
 * @author wczl
 */
public class DashboardScope
{
    /**
     * 看得到收支的判据。
     *
     * <p>看板权限是一个点，但钱的数字要单独把一道：授权某人看首页，不等于授权他看
     * 这家公司这个月收了多少钱。</p>
     */
    private static final String FINANCE_PERMISSION = "finance:transaction:list";

    private static final String SALES_PERMISSION = "order:sales:list";

    private static final String PURCHASE_PERMISSION = "purchase:order:list";

    private static final String PRODUCTION_PERMISSION = "production:order:list";

    private final boolean finance;

    private final boolean cost;

    private final boolean sales;

    private final boolean saleAmount;

    private final boolean purchase;

    private final boolean production;

    public DashboardScope(boolean finance, boolean cost, boolean sales, boolean saleAmount,
            boolean purchase, boolean production)
    {
        this.finance = finance;
        this.cost = cost;
        this.sales = sales;
        this.saleAmount = saleAmount;
        this.purchase = purchase;
        this.production = production;
    }

    /**
     * 按当前登录人的权限算一份。
     *
     * <p>只在请求线程上调用。没有登录上下文时 {@link PriceMaskUtils} 一律按遮蔽算，
     * 于是金额那两项是 false——读侧宁可少显示，见它的类注释。</p>
     */
    public static DashboardScope current()
    {
        boolean sales = SecurityUtils.hasPermi(SALES_PERMISSION);
        return new DashboardScope(
                SecurityUtils.hasPermi(FINANCE_PERMISSION),
                PriceMaskUtils.isCostVisible(),
                sales,
                // 销售额要两个条件同时成立：看得到订单这块，且看得到销售侧金额。
                // 只有前者的人（比如客服）该看到「本月 37 单」，不该看到那 37 单卖了多少钱。
                sales && PriceMaskUtils.isSaleVisible(),
                SecurityUtils.hasPermi(PURCHASE_PERMISSION),
                SecurityUtils.hasPermi(PRODUCTION_PERMISSION));
    }

    /** 收入、支出与收支趋势。 */
    public boolean isFinance()
    {
        return finance;
    }

    /** 在库货值、本月出入库金额与出入库趋势——这三样存的都是成本额。 */
    public boolean isCost()
    {
        return cost;
    }

    /** 销售订单的张数：本月订单数，以及待审核队列里的销售订单那一行。 */
    public boolean isSales()
    {
        return sales;
    }

    /** 本月销售额。 */
    public boolean isSaleAmount()
    {
        return saleAmount;
    }

    /** 待收货采购单、逾期未到货，以及待审核队列里的采购订单那一行。 */
    public boolean isPurchase()
    {
        return purchase;
    }

    /** 在产工单与超期未完工。 */
    public boolean isProduction()
    {
        return production;
    }
}
