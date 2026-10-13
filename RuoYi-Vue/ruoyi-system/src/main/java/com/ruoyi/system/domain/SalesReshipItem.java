package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/** 销售补发明细。数量在建单时一次确定，发货时整单全部出库。 */
public class SalesReshipItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String detailCode;
    private String reshipCode;
    private String originalOrderDetailCode;
    private Integer lineNo;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }
    public String getReshipCode() { return reshipCode; }
    public void setReshipCode(String reshipCode) { this.reshipCode = reshipCode; }
    public String getOriginalOrderDetailCode() { return originalOrderDetailCode; }
    public void setOriginalOrderDetailCode(String originalOrderDetailCode) { this.originalOrderDetailCode = originalOrderDetailCode; }
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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
