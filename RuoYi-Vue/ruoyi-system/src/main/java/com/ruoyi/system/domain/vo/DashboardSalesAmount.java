package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 本月销售额，一个币种一行。
 *
 * <h3>为什么不能只给一个数</h3>
 *
 * <p>销售订单每张都带自己的 {@code currency}（{@code CurrencySupport} 的白名单里有
 * CNY/USD/EUR/GBP/JPY/HKD 六种），而这套系统<b>没有汇率</b>——库里没存过一条汇率，
 * 任何地方都没有折算。把 100 USD 和 100 CNY 加成 200 是一个凭空造出来的数字，
 * 比不显示更糟。</p>
 *
 * <p>所以这里按币种分组原样给出去。2026-09-29 起有了「汇率设置」，折算就加在这一层之上：
 * 每行带上今天生效的汇率和折合人民币（{@link #rate} / {@link #amountBase}），合计放在
 * DashboardSummary#salesAmountBase；没录汇率的币种 rate 为空、不进合计，分组本身保留不动。</p>
 *
 * @author wczl
 */
public class DashboardSalesAmount implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** ISO 4217 代码，如 CNY。老数据可能为空，由前端按 CNY 兜底 */
    private String currency;

    /** 该币种本月的订单总额 */
    private BigDecimal amount = BigDecimal.ZERO;

    /** 该币种本月的订单张数。客单价由前端用这两个数相除，不在这里算 */
    private int orderCount;

    /**
     * 折算用的汇率（1 该币种 = 多少人民币），取「汇率设置」里今天生效的那条。
     * 人民币恒为 1；没录过汇率时为 null，这一行不进合计。
     */
    private BigDecimal rate;

    /** 折合人民币；rate 为 null 时也为 null */
    private BigDecimal amountBase;

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public BigDecimal getAmountBase() { return amountBase; }
    public void setAmountBase(BigDecimal amountBase) { this.amountBase = amountBase; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
}
