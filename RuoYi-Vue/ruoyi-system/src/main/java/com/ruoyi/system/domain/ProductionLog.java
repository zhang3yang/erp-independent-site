package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/** 生产执行日志月表实体。 */
public class ProductionLog implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long id;
    private String shopCode;
    private String logCode;
    private String productionCode;
    private String operation;
    private String docCode;
    private String docType;
    private String docDetailCode;
    private String goodsCode;
    private BigDecimal qty;
    @PriceField(PriceScope.COST)
    private BigDecimal costAmount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operationTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getLogCode() { return logCode; }
    public void setLogCode(String logCode) { this.logCode = logCode; }
    public String getProductionCode() { return productionCode; }
    public void setProductionCode(String productionCode) { this.productionCode = productionCode; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocDetailCode() { return docDetailCode; }
    public void setDocDetailCode(String docDetailCode) { this.docDetailCode = docDetailCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    public Date getOperationTime() { return operationTime; }
    public void setOperationTime(Date operationTime) { this.operationTime = operationTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
