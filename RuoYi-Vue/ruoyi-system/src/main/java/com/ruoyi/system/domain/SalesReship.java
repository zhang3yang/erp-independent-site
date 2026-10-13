package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 销售补发单。
 *
 * <p>补发单独立于销售订单和发货批次：一张补发单必须对应一张原订单；
 * 同一原订单最多一张补发单；整张补发单只能一次性发完。</p>
 */
public class SalesReship implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "待发货";
    public static final String STATUS_SHIPPED = "已发货";

    private Long id;
    private String shopCode;
    private String reshipCode;
    private String originalOrderCode;
    private String originalOrderSuffix;
    private String platOrderId;
    private String platCode;
    private String platName;
    private String shopStoreCode;
    private String shopStoreName;
    private String receivePerson;
    private String receiveAddress;
    private String receiveTelephone;
    private String receiveZipCode;
    private String warehouseCode;
    private String warehouseName;
    private String carrier;
    private String logisticsNo;
    private String status;
    private String outboundDocCode;
    /**
     * 查询用：把补发单限定在这个创建人名下。<b>不是数据库列</b>。
     * 横向权限，一律由服务层 selectList 说了算，前端传什么都会被覆盖。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String ownerScope;

    private String createUser;
    private String createUserName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String shipUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shipTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private String remark;
    private BigDecimal totalQty;
    private List<SalesReshipItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getReshipCode() { return reshipCode; }
    public void setReshipCode(String reshipCode) { this.reshipCode = reshipCode; }
    public String getOriginalOrderCode() { return originalOrderCode; }
    public void setOriginalOrderCode(String originalOrderCode) { this.originalOrderCode = originalOrderCode; }
    public String getOriginalOrderSuffix() { return originalOrderSuffix; }
    public void setOriginalOrderSuffix(String originalOrderSuffix) { this.originalOrderSuffix = originalOrderSuffix; }
    public String getPlatOrderId() { return platOrderId; }
    public void setPlatOrderId(String platOrderId) { this.platOrderId = platOrderId; }
    public String getPlatCode() { return platCode; }
    public void setPlatCode(String platCode) { this.platCode = platCode; }
    public String getPlatName() { return platName; }
    public void setPlatName(String platName) { this.platName = platName; }
    public String getShopStoreCode() { return shopStoreCode; }
    public void setShopStoreCode(String shopStoreCode) { this.shopStoreCode = shopStoreCode; }
    public String getShopStoreName() { return shopStoreName; }
    public void setShopStoreName(String shopStoreName) { this.shopStoreName = shopStoreName; }
    public String getReceivePerson() { return receivePerson; }
    public void setReceivePerson(String receivePerson) { this.receivePerson = receivePerson; }
    public String getReceiveAddress() { return receiveAddress; }
    public void setReceiveAddress(String receiveAddress) { this.receiveAddress = receiveAddress; }
    public String getReceiveTelephone() { return receiveTelephone; }
    public void setReceiveTelephone(String receiveTelephone) { this.receiveTelephone = receiveTelephone; }
    public String getReceiveZipCode() { return receiveZipCode; }
    public void setReceiveZipCode(String receiveZipCode) { this.receiveZipCode = receiveZipCode; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutboundDocCode() { return outboundDocCode; }
    public void setOutboundDocCode(String outboundDocCode) { this.outboundDocCode = outboundDocCode; }
    public String getOwnerScope() { return ownerScope; }
    public void setOwnerScope(String ownerScope) { this.ownerScope = ownerScope; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getShipUser() { return shipUser; }
    public void setShipUser(String shipUser) { this.shipUser = shipUser; }
    public Date getShipTime() { return shipTime; }
    public void setShipTime(Date shipTime) { this.shipTime = shipTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }
    public List<SalesReshipItem> getItems() { return items; }
    public void setItems(List<SalesReshipItem> items) { this.items = items; }
}
