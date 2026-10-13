package com.ruoyi.station.console.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一次结算币种换算的流水。对应表 {@code u_s_currency_convert_log}。
 *
 * <h3>它为什么存在</h3>
 * <p>换算是「乘一个汇率、四舍五入到 4 位」，<b>原值不留副本</b>。改回去只能除以
 * 同一个汇率——而那是<b>换算当天的</b>汇率，不是今天的。这两件事看着一样，
 * 结果不一样：隔一周再改回来，用今天的汇率除，数字回不到原处。</p>
 *
 * <p>所以每次换算在这里落一行，把当时用的汇率记死。这是唯一的还原依据。</p>
 *
 * <p><b>⚠ 即使如此也只是近似还原</b>：{@code round(x * rate, 4)} 丢掉的小数位追不回来。
 * 真要精确回滚，只有换算前的库备份——管理台那个确认框里「先备份」三个字不是客套话。</p>
 *
 * <h3>它同时是接口返回值</h3>
 * <p>保存站点设置的接口把这个对象原样回给管理台，运营当场看到这次动了多少行商品、
 * 多少行订单。不另做一个 DTO：那会变成两份要同步的字段清单，而它们表达的是同一件事。</p>
 */
public class CurrencyConvertLog
{
    private Long id;

    /** 站点编码，恒等于 {@code u_s_siteinfo.sitecode} */
    private String siteCode;

    /** 换算前的结算币种 */
    private String fromCode;

    /** 换算后的结算币种 */
    private String toCode;

    /**
     * 1 个 {@link #fromCode} 换多少个 {@link #toCode}，<b>换算当时</b>的值。
     *
     * <p>还原就是除以它。见类注释：不是除以今天的汇率。</p>
     */
    private BigDecimal rate;

    /** {@code u_s_sitegoods} 影响行数。⚠ 只含 {@code price > 0}：0 是面议，不参与换算 */
    private Integer goodsRows;

    /** {@code u_s_sitegoods_combo} 影响行数，同样只含 {@code price > 0} */
    private Integer comboRows;

    /**
     * {@code u_s_orderitem} 影响行数。
     *
     * <p>⚠ 这个数<b>含面议行</b>，和上面两个口径不同：面议行的金额是 0、换不换一样，
     * 但它的 {@code currency} 列必须跟着改，否则同一张单里标价行和面议行的币种不一致。</p>
     */
    private Integer itemRows;

    /** {@code u_s_order} 影响行数 */
    private Integer orderRows;

    /**
     * 换算<b>前</b>就已经 {@code subtotal != sum(linetotal)} 的订单数。
     *
     * <p>&gt; 0 说明这次换算顺手把它们改一致了。那是历史数据本来就不自洽
     * （手工改过、或早期版本算法不同），不是这次换算造成的——但它发生在一次
     * 「我只是改了个币种」的操作里，所以必须有个地方看得见。</p>
     */
    private Integer mismatch;

    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getFromCode() { return fromCode; }
    public void setFromCode(String fromCode) { this.fromCode = fromCode; }

    public String getToCode() { return toCode; }
    public void setToCode(String toCode) { this.toCode = toCode; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public Integer getGoodsRows() { return goodsRows; }
    public void setGoodsRows(Integer goodsRows) { this.goodsRows = goodsRows; }

    public Integer getComboRows() { return comboRows; }
    public void setComboRows(Integer comboRows) { this.comboRows = comboRows; }

    public Integer getItemRows() { return itemRows; }
    public void setItemRows(Integer itemRows) { this.itemRows = itemRows; }

    public Integer getOrderRows() { return orderRows; }
    public void setOrderRows(Integer orderRows) { this.orderRows = orderRows; }

    public Integer getMismatch() { return mismatch; }
    public void setMismatch(Integer mismatch) { this.mismatch = mismatch; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
