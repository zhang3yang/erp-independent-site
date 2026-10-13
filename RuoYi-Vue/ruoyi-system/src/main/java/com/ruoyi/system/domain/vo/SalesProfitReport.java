package com.ruoyi.system.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 销售利润报表。金额一律是人民币（外币收入按发货当天的汇率折算过）。
 */
public class SalesProfitReport
{
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date begin;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date end;

    /** customer / staff / country / goods / store / month / order */
    private String dimension;

    private Row summary = new Row();

    private List<Row> rows = new ArrayList<>();

    /** 没有汇率、没算进报表的外币单 */
    private List<MissingRate> missingRates = new ArrayList<>();

    /** 找不到对应订单的单据明细行数（订单被删、单号对不上），没算进报表 */
    private int unmatchedLines;

    /** 出库成本为 0 的发货明细行数：多半是零成本入库的货，利润会虚高，提醒核对 */
    private int zeroCostLines;

    public static class Row
    {
        private String key;
        private String label;
        /** 辅助说明：商品编码、订单的客户等 */
        private String subLabel;
        private int orderCount;
        /** 净发货数量 = 发出 − 退回；补发不算 */
        private BigDecimal qty = BigDecimal.ZERO;
        /** 销售收入 = 发货数量 × 订单单价 − 退货，折人民币 */
        private BigDecimal revenue = BigDecimal.ZERO;
        /** 出库成本（含补发成本，已扣退货成本） */
        private BigDecimal cost = BigDecimal.ZERO;
        /** 其中补发成本 */
        private BigDecimal reshipCost = BigDecimal.ZERO;
        /** 向客户收的运费，按发货进度分摊，折人民币 */
        private BigDecimal freightIncome = BigDecimal.ZERO;
        /** 付给承运商的运费 */
        private BigDecimal freightCost = BigDecimal.ZERO;
        /** 按订单维度时的订单币种与原币收入 */
        private String currency;
        private BigDecimal revenueOriginal;

        @JsonIgnore
        private final Set<String> orderCodes = new LinkedHashSet<>();

        public BigDecimal getGrossProfit()
        {
            return revenue.subtract(cost);
        }

        /** 毛利率 %，收入为 0 时为空 */
        public BigDecimal getGrossMargin()
        {
            return percent(getGrossProfit(), revenue);
        }

        /** 含运费毛利 = 毛利 + 收的运费 − 付的运费 */
        public BigDecimal getNetProfit()
        {
            return getGrossProfit().add(freightIncome).subtract(freightCost);
        }

        public BigDecimal getNetMargin()
        {
            return percent(getNetProfit(), revenue.add(freightIncome));
        }

        private static BigDecimal percent(BigDecimal part, BigDecimal whole)
        {
            if (whole == null || whole.signum() == 0)
            {
                return null;
            }
            return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
        }

        public Set<String> orderCodes() { return orderCodes; }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getSubLabel() { return subLabel; }
        public void setSubLabel(String subLabel) { this.subLabel = subLabel; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public BigDecimal getCost() { return cost; }
        public void setCost(BigDecimal cost) { this.cost = cost; }
        public BigDecimal getReshipCost() { return reshipCost; }
        public void setReshipCost(BigDecimal reshipCost) { this.reshipCost = reshipCost; }
        public BigDecimal getFreightIncome() { return freightIncome; }
        public void setFreightIncome(BigDecimal freightIncome) { this.freightIncome = freightIncome; }
        public BigDecimal getFreightCost() { return freightCost; }
        public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public BigDecimal getRevenueOriginal() { return revenueOriginal; }
        public void setRevenueOriginal(BigDecimal revenueOriginal) { this.revenueOriginal = revenueOriginal; }
    }

    public static class MissingRate
    {
        private String currency;
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        private Date earliestDate;
        private int lineCount;
        @JsonIgnore
        private final Set<String> orderCodes = new LinkedHashSet<>();

        public Set<String> orderCodes() { return orderCodes; }
        public int getOrderCount() { return orderCodes.size(); }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Date getEarliestDate() { return earliestDate; }
        public void setEarliestDate(Date earliestDate) { this.earliestDate = earliestDate; }
        public int getLineCount() { return lineCount; }
        public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    }

    public Date getBegin() { return begin; }
    public void setBegin(Date begin) { this.begin = begin; }
    public Date getEnd() { return end; }
    public void setEnd(Date end) { this.end = end; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public Row getSummary() { return summary; }
    public void setSummary(Row summary) { this.summary = summary; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
    public List<MissingRate> getMissingRates() { return missingRates; }
    public void setMissingRates(List<MissingRate> missingRates) { this.missingRates = missingRates; }
    public int getUnmatchedLines() { return unmatchedLines; }
    public void setUnmatchedLines(int unmatchedLines) { this.unmatchedLines = unmatchedLines; }
    public int getZeroCostLines() { return zeroCostLines; }
    public void setZeroCostLines(int zeroCostLines) { this.zeroCostLines = zeroCostLines; }
}
