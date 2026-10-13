package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 发货批次明细 u_f_shipbatchdetail_{yyyyMM}。跟随所属批次的月份。
 */
public class ShipBatchItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String detailCode;
    private String shipCode;
    /** 关联的订单明细代码，回写已发量与核销预占都靠它定位 */
    private String orderDetailCode;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    /** 本批次发货数量 */
    private BigDecimal qty;
    /**
     * 来源单号：这批货对应的生产工单号，建批次时从订单明细带过来。
     *
     * <p>原先旁边还有个「实发来源」（现货/采购/生产），它是订单明细「备货来源」的镜像；
     * 备货来源这个概念去掉之后它也没了依据，一并删掉。这批货实际是从哪个批次扣的，
     * 出库单和库存流水上记得比这里准。</p>
     */
    private String sourceCode;
    private String remark;

    /** 展示用：本行对应的订单明细数量，查询时带出 */
    private BigDecimal orderQty;

    /**
     * 商品档案上的批次策略：none / batch / serial。不落库，查询时从 u_m_waregoods 带出。
     *
     * <p>发货那一刻要开销售出库单，序列号管理的商品必须逐个指定序列号，个别计价的批次管理
     * 商品必须指定出库批次——前端得先知道这一行属于哪种，才知道要不要让人去选。</p>
     */
    private String batchStrategy;

    /** 商品档案上的计价方式：fifo / weighted / individual。与 batchStrategy 同批带出，用途见上 */
    private String costCalculation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

    public String getShipCode() { return shipCode; }
    public void setShipCode(String shipCode) { this.shipCode = shipCode; }

    public String getOrderDetailCode() { return orderDetailCode; }
    public void setOrderDetailCode(String orderDetailCode) { this.orderDetailCode = orderDetailCode; }

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

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public BigDecimal getOrderQty() { return orderQty; }
    public void setOrderQty(BigDecimal orderQty) { this.orderQty = orderQty; }

    public String getBatchStrategy() { return batchStrategy; }
    public void setBatchStrategy(String batchStrategy) { this.batchStrategy = batchStrategy; }

    public String getCostCalculation() { return costCalculation; }
    public void setCostCalculation(String costCalculation) { this.costCalculation = costCalculation; }
}
