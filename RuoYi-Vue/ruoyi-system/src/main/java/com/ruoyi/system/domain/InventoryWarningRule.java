package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 库存低库存预警规则。 */
public class InventoryWarningRule implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long ruleId;
    private String shopCode;
    
    @NotBlank(message = "商品编码不能为空")
    @Size(max = 50, message = "商品编码不能超过50个字符")
    private String goodsCode;
    private String goodsName;
    @NotBlank(message = "仓库编码不能为空")
    @Size(max = 50, message = "仓库编码不能超过50个字符")
    private String wareCode;
    private String wareName;
    @NotNull(message = "预警阈值不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "预警阈值不能小于0")
    private BigDecimal thresholdQty;
    private Integer enabled;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public String getWareCode() { return wareCode; }
    public void setWareCode(String wareCode) { this.wareCode = wareCode; }
    public String getWareName() { return wareName; }
    public void setWareName(String wareName) { this.wareName = wareName; }
    public BigDecimal getThresholdQty() { return thresholdQty; }
    public void setThresholdQty(BigDecimal thresholdQty) { this.thresholdQty = thresholdQty; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
