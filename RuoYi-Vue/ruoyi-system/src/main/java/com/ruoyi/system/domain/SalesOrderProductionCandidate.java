package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 可排产的销售订单明细，供生产建工单时选单。
 *
 * <p>一行 = 一条<b>还没发完</b>的订单明细（{@code qty > 已发}）。曾经叫 SalesOrderShortage、
 * 只列「缺口」大于 0 的行，即库存占不上的部分才准排产。缺口随订单预占一起取消了：
 * 不占库存之后它恒等于未发量，两个数说的是同一件事。</p>
 *
 * <p>单独建 DTO 而不是复用 {@link SalesOrderItem}，是因为选单界面要的是「订单 + 明细」的
 * 合并视图——订单号、状态、下单时间在主表上，数量在明细表上，塞回明细实体会让那个实体
 * 多出一堆只在这一个查询里有值的列。</p>
 *
 * <p>{@code tableSuffix} 随行返回：订单分月表，后续按明细定位、回写来源都要靠它。</p>
 */
public class SalesOrderProductionCandidate implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String orderCode;
    private String tableSuffix;
    private String orderState;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date orderTime;
    private String buyerShop;

    private String detailCode;
    private Integer lineNo;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal shippedQty;
    /** 未发量 = 订单量 − 已发量，这一行还欠客户多少。<b>按订单排产的上限就是它。</b> */
    private BigDecimal pendingQty;
    /**
     * 专门生产（定制品）：下单时人工标的，见 {@link SalesOrderItem#getCustomMade()}。
     *
     * <p>决定这一行做出来的货归谁：定制件完工入库时批次会绑到这条订单明细上，别的订单
     * 出库挑不到；普通件进通用库存，谁先发货谁拿到。</p>
     */
    private Boolean customMade;
    /** 正在负责这条明细的生产工单号。指向未关闭工单时不能再建第二张，界面据此提示 */
    private String sourceCode;

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }
    public String getOrderState() { return orderState; }
    public void setOrderState(String orderState) { this.orderState = orderState; }
    public Date getOrderTime() { return orderTime; }
    public void setOrderTime(Date orderTime) { this.orderTime = orderTime; }
    public String getBuyerShop() { return buyerShop; }
    public void setBuyerShop(String buyerShop) { this.buyerShop = buyerShop; }
    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getShippedQty() { return shippedQty; }
    public void setShippedQty(BigDecimal shippedQty) { this.shippedQty = shippedQty; }
    public BigDecimal getPendingQty() { return pendingQty; }
    public void setPendingQty(BigDecimal pendingQty) { this.pendingQty = pendingQty; }
    public Boolean getCustomMade() { return customMade; }
    public void setCustomMade(Boolean customMade) { this.customMade = customMade; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
}
