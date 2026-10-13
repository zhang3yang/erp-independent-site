package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 生产工单主表实体。
 */
public class ProductionOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String productionCode;
    private String sourceType;
    private String sourceOrderCodes;
    private String orderAllocation;
    private String productCode;
    private String productName;
    private BigDecimal planYQty;
    private BigDecimal actualYQty;
    /** 计划生产日期。只到天，与前端 el-date-picker 的 value-format="YYYY-MM-DD" 及采购模块保持一致。 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date planDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualDate;
    private String status;
    @PriceField(PriceScope.COST)
    private BigDecimal materialCost;
    @PriceField(PriceScope.COST)
    private BigDecimal processingCost;
    @PriceField(PriceScope.COST)
    private BigDecimal freightCost;
    @PriceField(PriceScope.COST)
    private BigDecimal otherCost;
    @PriceField(PriceScope.COST)
    private BigDecimal totalCost;
    @PriceField(PriceScope.COST)
    private BigDecimal unitCost;
    private String createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private String remark;
    private List<ProductionOrderDetail> details;
    private List<ProductionMaterial> materials;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getProductionCode() { return productionCode; }
    public void setProductionCode(String productionCode) { this.productionCode = productionCode; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceOrderCodes() { return sourceOrderCodes; }
    public void setSourceOrderCodes(String sourceOrderCodes) { this.sourceOrderCodes = sourceOrderCodes; }
    public String getOrderAllocation() { return orderAllocation; }
    public void setOrderAllocation(String orderAllocation) { this.orderAllocation = orderAllocation; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getPlanYQty() { return planYQty; }
    public void setPlanYQty(BigDecimal planYQty) { this.planYQty = planYQty; }
    public BigDecimal getActualYQty() { return actualYQty; }
    public void setActualYQty(BigDecimal actualYQty) { this.actualYQty = actualYQty; }
    public Date getPlanDate() { return planDate; }
    public void setPlanDate(Date planDate) { this.planDate = planDate; }
    public Date getActualDate() { return actualDate; }
    public void setActualDate(Date actualDate) { this.actualDate = actualDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public void setMaterialCost(BigDecimal materialCost) { this.materialCost = materialCost; }
    public BigDecimal getProcessingCost() { return processingCost; }
    public void setProcessingCost(BigDecimal processingCost) { this.processingCost = processingCost; }
    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }
    public BigDecimal getOtherCost() { return otherCost; }
    public void setOtherCost(BigDecimal otherCost) { this.otherCost = otherCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<ProductionOrderDetail> getDetails() { return details; }
    public void setDetails(List<ProductionOrderDetail> details) { this.details = details; }
    public List<ProductionMaterial> getMaterials() { return materials; }
    public void setMaterials(List<ProductionMaterial> materials) { this.materials = materials; }
}
