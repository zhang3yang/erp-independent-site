package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/** 预警检查时按商品和仓库聚合后的候选库存。 */
public class InventoryWarningCandidate implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String shopCode;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private String wareCode;
    private String wareName;
    private BigDecimal availableQty;
    private BigDecimal thresholdQty;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String value) { shopCode = value; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String value) { goodsCode = value; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String value) { goodsName = value; }
    public String getSpec() { return spec; }
    public void setSpec(String value) { spec = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getWareCode() { return wareCode; }
    public void setWareCode(String value) { wareCode = value; }
    public String getWareName() { return wareName; }
    public void setWareName(String value) { wareName = value; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal value) { availableQty = value; }
    public BigDecimal getThresholdQty() { return thresholdQty; }
    public void setThresholdQty(BigDecimal value) { thresholdQty = value; }
}
