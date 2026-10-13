package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/** 产品配方里的一行物料。一套配方里同一个物料只能有一行。 */
public class ProductionBomItem implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long id;
    private String shopCode;
    private String bomDetailCode;
    private String bomCode;
    private Integer lineNo;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    /** 单耗：每产出 1 个成品的用量，与工单上的 qtyPerUnit 同口径 */
    private BigDecimal qtyPerUnit;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getBomDetailCode() { return bomDetailCode; }
    public void setBomDetailCode(String bomDetailCode) { this.bomDetailCode = bomDetailCode; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
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
    public BigDecimal getQtyPerUnit() { return qtyPerUnit; }
    public void setQtyPerUnit(BigDecimal qtyPerUnit) { this.qtyPerUnit = qtyPerUnit; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
