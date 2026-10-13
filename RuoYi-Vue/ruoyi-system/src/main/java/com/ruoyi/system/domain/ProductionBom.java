package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 产品配方（BOM）主数据。
 *
 * <p>一个成品一套配方，改配方就是改这一套，不做多版本。建生产工单时按产出商品自动带出，
 * 带出后落到工单上的是一份独立快照（{@link ProductionMaterial}），所以事后改配方不会
 * 影响已经建好的工单，历史成本仍然可追。</p>
 *
 * <p>单耗口径与工单一致：每产出 1 个成品的用量，带入时不做换算。</p>
 */
public class ProductionBom implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long id;
    private String shopCode;
    private String bomCode;
    private String productCode;
    private String productName;
    private String spec;
    private String unit;
    /** 启用 / 停用。停用的配方不再自动带入工单，但已建工单不受影响 */
    private String status;
    private String createUser;
    private Date createTime;
    private String updateUser;
    private Date updateTime;
    private String remark;

    /** 列表页展示用：这套配方有几行物料，由 SQL 统计出来 */
    private Integer itemCount;

    private List<ProductionBomItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public List<ProductionBomItem> getItems() { return items; }
    public void setItems(List<ProductionBomItem> items) { this.items = items; }
}
