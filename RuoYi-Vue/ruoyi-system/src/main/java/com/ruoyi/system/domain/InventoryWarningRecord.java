package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.Size;

/** 一次低库存预警周期的记录。 */
public class InventoryWarningRecord implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long recordId;
    private String shopCode;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private String wareCode;
    private String wareName;
    private String warningType;
    private BigDecimal availableQty;
    private BigDecimal thresholdQty;
    private String status;
    private String statusName;
    private Date triggerTime;
    private Date lastCheckedTime;
    private String acknowledgedBy;
    private Date acknowledgedTime;
    @Size(max = 500, message = "处理备注不能超过500个字符")
    private String handleRemark;
    private Date recoveredTime;
    private String closedBy;
    private Date closedTime;
    private String activeKey;
    private Date createTime;
    private Date updateTime;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getWareCode() { return wareCode; }
    public void setWareCode(String wareCode) { this.wareCode = wareCode; }
    public String getWareName() { return wareName; }
    public void setWareName(String wareName) { this.wareName = wareName; }
    public String getWarningType() { return warningType; }
    public void setWarningType(String warningType) { this.warningType = warningType; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getThresholdQty() { return thresholdQty; }
    public void setThresholdQty(BigDecimal thresholdQty) { this.thresholdQty = thresholdQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public Date getTriggerTime() { return triggerTime; }
    public void setTriggerTime(Date triggerTime) { this.triggerTime = triggerTime; }
    public Date getLastCheckedTime() { return lastCheckedTime; }
    public void setLastCheckedTime(Date lastCheckedTime) { this.lastCheckedTime = lastCheckedTime; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public Date getAcknowledgedTime() { return acknowledgedTime; }
    public void setAcknowledgedTime(Date acknowledgedTime) { this.acknowledgedTime = acknowledgedTime; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public Date getRecoveredTime() { return recoveredTime; }
    public void setRecoveredTime(Date recoveredTime) { this.recoveredTime = recoveredTime; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public Date getClosedTime() { return closedTime; }
    public void setClosedTime(Date closedTime) { this.closedTime = closedTime; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
