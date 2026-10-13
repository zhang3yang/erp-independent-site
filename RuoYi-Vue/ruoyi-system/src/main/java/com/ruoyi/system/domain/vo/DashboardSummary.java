package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页经营看板的卡片数据，一个商家一份。
 *
 * <p>全部字段都给了非空初值：{@code selectStockStat} 只填 {@link #stockAmount} 和
 * {@link #skuCount} 两项，其余由服务层分别补齐；商家还没有任何月表时也不会补——
 * 让前端拿到 null 去做 toFixed 是最容易出的那种低级故障，所以这里兜住。</p>
 *
 * <h3>那一串 xxxVisible</h3>
 *
 * <p>看板横跨六个模块，而这几块本来就是分开授权的，判据见 {@link DashboardScope}。
 * 每块配一个 visible 标记，为 false 时对应的数<b>根本没去查</b>，字段留在初值上。
 * 前端照着标记决定整张卡片画不画——不是画一张写着 0 的卡片：0 的意思是「这个月一单都没有」，
 * 和「不给你看」是两件完全不同的事，糊在一起就是在告诉用户一件假事。</p>
 */
public class DashboardSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 统计口径的月份，yyyyMM。前端标题上要显示，不能让用户以为是「最近30天」 */
    private String month;

    /** 在库货值：良品和不良品都算，只要还在仓库里就占着钱 */
    private BigDecimal stockAmount = BigDecimal.ZERO;

    /** 有正库存的商品数（按商品去重，不按仓库） */
    private int skuCount;

    /** 未处理的库存预警条数 */
    private int warningCount;

    /** 待审核单据数，口径见 IDashboardService#selectSummary */
    private int pendingDocCount;

    /**
     * 待审核单据按类型拆分，供卡片点开后按类型分流到各自的页面。
     *
     * <p>各项 docCount 之和等于 {@link #pendingDocCount}，两者同源。</p>
     */
    private List<DashboardPendingDoc> pendingDocs = new ArrayList<>();

    /** 本月入库金额（流水 cost_amount 为正的部分） */
    private BigDecimal inboundAmount = BigDecimal.ZERO;

    /** 本月出库金额（取绝对值，出库流水的 cost_amount 存的是负数） */
    private BigDecimal outboundAmount = BigDecimal.ZERO;

    /**
     * 在库货值与本月出入库金额能不能看。
     *
     * <p>这三个数存的都是<b>成本额</b>，归 {@code price:cost} 管，
     * 见 {@code docs/金额按岗位可见.md}。销售岗位看得到订单卖多少钱，
     * 但不该从首页反推出这批货的进价。</p>
     */
    private boolean costVisible;

    /** 销售订单的张数能不能看（{@code order:sales:list}） */
    private boolean salesVisible;

    /** 本月销售订单张数，口径见 IDashboardService#selectSummary */
    private int salesOrderCount;

    /** 本月销售额能不能看：在 {@link #salesVisible} 之上还要有 {@code price:sale} */
    private boolean saleAmountVisible;

    /** 本月销售额，一个币种一行，每行带今天的汇率和折合人民币，见 {@link DashboardSalesAmount} */
    private List<DashboardSalesAmount> salesAmounts = new ArrayList<>();

    /**
     * 本月销售额折合人民币合计：只加得到汇率的那些币种。
     * 有币种没录汇率时它会偏小，前端要配合 {@link #salesRateMissing} 提示。
     */
    private BigDecimal salesAmountBase;

    /** 本月有订单、但「汇率设置」里还没录汇率的币种 */
    private List<String> salesRateMissing = new ArrayList<>();

    public BigDecimal getSalesAmountBase() { return salesAmountBase; }
    public void setSalesAmountBase(BigDecimal salesAmountBase) { this.salesAmountBase = salesAmountBase; }

    public List<String> getSalesRateMissing() { return salesRateMissing; }
    public void setSalesRateMissing(List<String> salesRateMissing) { this.salesRateMissing = salesRateMissing; }

    /** 采购那两个数能不能看（{@code purchase:order:list}） */
    private boolean purchaseVisible;

    /** 待收货的采购单张数（已审核 + 部分到货） */
    private int purchaseReceivingCount;

    /** 其中已过预计到货日的张数，是上面那个数的子集，两者不要相加 */
    private int purchaseOverdueCount;

    /** 生产那两个数能不能看（{@code production:order:list}） */
    private boolean productionVisible;

    /** 未完工的工单张数（待生产 + 待领料 + 生产中） */
    private int productionActiveCount;

    /** 其中已过计划生产日的张数，是上面那个数的子集，两者不要相加 */
    private int productionOverdueCount;

    /**
     * 收支两项能不能看。
     *
     * <p>看板权限只有一个点，但「本月收入/支出」是财务数据，不该因为某人被授权看首页
     * 就一起给出去。没有财务权限时后端根本不去查这两个数，这个标记告诉前端别画那两张卡
     * 和那张图——只靠前端不画是遮不住的，数据已经在响应体里了。</p>
     */
    private boolean financeVisible;

    /** 本月已完成收入 */
    private BigDecimal incomeAmount = BigDecimal.ZERO;

    /** 本月已完成支出（取绝对值，支出金额存的是负数） */
    private BigDecimal expenseAmount = BigDecimal.ZERO;

    public boolean isFinanceVisible() { return financeVisible; }
    public void setFinanceVisible(boolean financeVisible) { this.financeVisible = financeVisible; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public BigDecimal getStockAmount() { return stockAmount; }
    public void setStockAmount(BigDecimal stockAmount)
    {
        this.stockAmount = stockAmount == null ? BigDecimal.ZERO : stockAmount;
    }

    public int getSkuCount() { return skuCount; }
    public void setSkuCount(int skuCount) { this.skuCount = skuCount; }

    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

    public int getPendingDocCount() { return pendingDocCount; }
    public void setPendingDocCount(int pendingDocCount) { this.pendingDocCount = pendingDocCount; }

    public List<DashboardPendingDoc> getPendingDocs() { return pendingDocs; }
    public void setPendingDocs(List<DashboardPendingDoc> pendingDocs)
    {
        this.pendingDocs = pendingDocs == null ? new ArrayList<>() : pendingDocs;
    }

    public BigDecimal getInboundAmount() { return inboundAmount; }
    public void setInboundAmount(BigDecimal inboundAmount)
    {
        this.inboundAmount = inboundAmount == null ? BigDecimal.ZERO : inboundAmount;
    }

    public BigDecimal getOutboundAmount() { return outboundAmount; }
    public void setOutboundAmount(BigDecimal outboundAmount)
    {
        this.outboundAmount = outboundAmount == null ? BigDecimal.ZERO : outboundAmount;
    }

    public BigDecimal getIncomeAmount() { return incomeAmount; }
    public void setIncomeAmount(BigDecimal incomeAmount)
    {
        this.incomeAmount = incomeAmount == null ? BigDecimal.ZERO : incomeAmount;
    }

    public BigDecimal getExpenseAmount() { return expenseAmount; }
    public void setExpenseAmount(BigDecimal expenseAmount)
    {
        this.expenseAmount = expenseAmount == null ? BigDecimal.ZERO : expenseAmount;
    }

    public boolean isCostVisible() { return costVisible; }
    public void setCostVisible(boolean costVisible) { this.costVisible = costVisible; }

    public boolean isSalesVisible() { return salesVisible; }
    public void setSalesVisible(boolean salesVisible) { this.salesVisible = salesVisible; }

    public int getSalesOrderCount() { return salesOrderCount; }
    public void setSalesOrderCount(int salesOrderCount) { this.salesOrderCount = salesOrderCount; }

    public boolean isSaleAmountVisible() { return saleAmountVisible; }
    public void setSaleAmountVisible(boolean saleAmountVisible) { this.saleAmountVisible = saleAmountVisible; }

    public List<DashboardSalesAmount> getSalesAmounts() { return salesAmounts; }
    public void setSalesAmounts(List<DashboardSalesAmount> salesAmounts)
    {
        this.salesAmounts = salesAmounts == null ? new ArrayList<>() : salesAmounts;
    }

    public boolean isPurchaseVisible() { return purchaseVisible; }
    public void setPurchaseVisible(boolean purchaseVisible) { this.purchaseVisible = purchaseVisible; }

    public int getPurchaseReceivingCount() { return purchaseReceivingCount; }
    public void setPurchaseReceivingCount(int purchaseReceivingCount)
    {
        this.purchaseReceivingCount = purchaseReceivingCount;
    }

    public int getPurchaseOverdueCount() { return purchaseOverdueCount; }
    public void setPurchaseOverdueCount(int purchaseOverdueCount)
    {
        this.purchaseOverdueCount = purchaseOverdueCount;
    }

    public boolean isProductionVisible() { return productionVisible; }
    public void setProductionVisible(boolean productionVisible) { this.productionVisible = productionVisible; }

    public int getProductionActiveCount() { return productionActiveCount; }
    public void setProductionActiveCount(int productionActiveCount)
    {
        this.productionActiveCount = productionActiveCount;
    }

    public int getProductionOverdueCount() { return productionOverdueCount; }
    public void setProductionOverdueCount(int productionOverdueCount)
    {
        this.productionOverdueCount = productionOverdueCount;
    }
}
