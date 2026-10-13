package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 看板趋势图上的一个月。
 *
 * <p>出入库来自库存流水月表、收支来自财务流水月表，两张表分开查、在服务层按
 * {@link #month} 合并。没有对应月表的月份也会出现在结果里，四个金额都是 0——
 * 缺月直接不给点，折线会把 7 月和 9 月连成一条斜线，看上去像 8 月有业务。</p>
 */
public class DashboardTrendPoint implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 月份，yyyyMM */
    private String month;

    private BigDecimal inboundAmount = BigDecimal.ZERO;

    private BigDecimal outboundAmount = BigDecimal.ZERO;

    private BigDecimal incomeAmount = BigDecimal.ZERO;

    private BigDecimal expenseAmount = BigDecimal.ZERO;

    public DashboardTrendPoint()
    {
    }

    public DashboardTrendPoint(String month)
    {
        this.month = month;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

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
}
