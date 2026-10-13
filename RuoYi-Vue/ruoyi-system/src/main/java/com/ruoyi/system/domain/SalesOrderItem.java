package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 销售订单明细 u_o_orderdetail_{yyyyMM}。跟随所属订单的月份，不按自身时间落表。
 */
public class SalesOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String detailCode;
    private String orderCode;
    private Integer lineNo;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    /** 已发货数量（累计），在发货批次发货时回写 */
    private BigDecimal shippedQty;
    /** 未发货数量 = qty - shippedQty，落库冗余，与采购 u_p_purchasedetail.remainingqty 对齐 */
    private BigDecimal remainingQty;
    /**
     * 专门生产（定制品）：库存里的通用货顶不上，必须为这一行单独做一批。
     *
     * <p><b>下单时人工标记，之后不随备货过程变</b>，是订单的固有属性——这一行的货能不能
     * 拿通用库存顶，只有下单的人知道，从数量上推不出来。</p>
     */
    private Boolean customMade;

    /**
     * 正在负责这一行的生产工单号。
     *
     * <p>建工单把这条明细放进分配时回写，移出分配或工单关闭时清空（已发完的行不清，
     * 留作追溯线索）。一条明细同时只能挂一张在产工单，排产选单靠它提示「已挂工单」，
     * 保存时由 {@code ProductionOrderServiceImpl.requireDetailFree} 兜底拦截。</p>
     *
     * <p><b>单值，不能拿来做历史追溯</b>：先后被两张工单做只留最后一张，关闭还会清空。
     * 要看一张订单关联过的全部工单，走 {@code selectOrderProductions}——那边是按工单的
     * 分配 JSON 反查的。</p>
     */
    private String sourceCode;
    private String remark;

    /** 未发量 = qty - shippedQty，查询时现算，不落库。这一行还欠客户多少。 */
    private BigDecimal pendingQty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

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

    public BigDecimal getRemainingQty() { return remainingQty; }
    public void setRemainingQty(BigDecimal remainingQty) { this.remainingQty = remainingQty; }

    /**
     * 取值可能是 null——历史明细和没勾过这一项的行都是。判定一律用
     * {@code Boolean.TRUE.equals(getCustomMade())}，别拆箱。
     *
     * <p><b>不要再加一个 {@code isCustomMade()} 的便捷重载。</b>同一个属性同时有
     * {@code getCustomMade()}（Boolean）和 {@code isCustomMade()}（boolean）时，
     * MyBatis 的 Reflector 认为返回类型有歧义，直接抛
     * {@code Illegal overloaded getter method with ambiguous type}，
     * 这张表的每一次插入和查询都会炸。</p>
     */
    public Boolean getCustomMade() { return customMade; }
    public void setCustomMade(Boolean customMade) { this.customMade = customMade; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public BigDecimal getPendingQty() { return pendingQty; }
    public void setPendingQty(BigDecimal pendingQty) { this.pendingQty = pendingQty; }

    @PriceField(PriceScope.SALE)
    private BigDecimal price;
    @PriceField(PriceScope.SALE)
    private BigDecimal amount;
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;
    @PriceField(PriceScope.COST)
    private BigDecimal costAmount;

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
}
