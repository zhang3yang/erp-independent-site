package com.ruoyi.system.domain.vo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售利润报表的原始数据行，SalesProfitMapper 的几条查询共用这一个壳子，各查各的字段：
 *
 * <ul>
 *   <li>单据明细：销售发货出库 CK/XSFH、补发出库 CK/XSBF、销售退货 XSTHRK/SO 的已审核明细；</li>
 *   <li>发货批次：shipCode → orderCode + 付给承运商的运费；</li>
 *   <li>补发单：reshipCode → 原订单号；</li>
 *   <li>订单头：客户、业务员、国家、平台店铺、币种、货款、收客户的运费；</li>
 *   <li>订单明细：订单号 + 商品 → 单价（一张订单里一个商品只有一行）。</li>
 * </ul>
 */
public class SalesProfitSource
{
    // ---- 单据明细 ----
    private String docCode;
    private Date docDate;
    private String docType;
    private String sourceDocType;
    private String sourceDocCode;
    private String goodsCode;
    private String goodsName;
    private BigDecimal qty;
    private BigDecimal costAmount;
    private BigDecimal saleAmount;

    // ---- 发货批次 / 补发单 ----
    private String shipCode;
    private String reshipCode;
    private String orderCode;
    /** 发货批次：付给承运商的运费（本位币） */
    private BigDecimal shipFreight;

    // ---- 订单头 ----
    private String currency;
    private BigDecimal goodsAmount;
    /** 订单：向客户收的运费（订单币种） */
    private BigDecimal orderFreight;
    private String staffCode;
    private String buyerCode;
    private String buyerName;
    private String buyerUser;
    private String country;
    private String platCode;
    private String platName;
    private String storeCode;
    private String storeName;

    // ---- 订单明细 ----
    private BigDecimal price;

    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }
    public Date getDocDate() { return docDate; }
    public void setDocDate(Date docDate) { this.docDate = docDate; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSourceDocType() { return sourceDocType; }
    public void setSourceDocType(String sourceDocType) { this.sourceDocType = sourceDocType; }
    public String getSourceDocCode() { return sourceDocCode; }
    public void setSourceDocCode(String sourceDocCode) { this.sourceDocCode = sourceDocCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    public BigDecimal getSaleAmount() { return saleAmount; }
    public void setSaleAmount(BigDecimal saleAmount) { this.saleAmount = saleAmount; }
    public String getShipCode() { return shipCode; }
    public void setShipCode(String shipCode) { this.shipCode = shipCode; }
    public String getReshipCode() { return reshipCode; }
    public void setReshipCode(String reshipCode) { this.reshipCode = reshipCode; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public BigDecimal getShipFreight() { return shipFreight; }
    public void setShipFreight(BigDecimal shipFreight) { this.shipFreight = shipFreight; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(BigDecimal goodsAmount) { this.goodsAmount = goodsAmount; }
    public BigDecimal getOrderFreight() { return orderFreight; }
    public void setOrderFreight(BigDecimal orderFreight) { this.orderFreight = orderFreight; }
    public String getStaffCode() { return staffCode; }
    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }
    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerUser() { return buyerUser; }
    public void setBuyerUser(String buyerUser) { this.buyerUser = buyerUser; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPlatCode() { return platCode; }
    public void setPlatCode(String platCode) { this.platCode = platCode; }
    public String getPlatName() { return platName; }
    public void setPlatName(String platName) { this.platName = platName; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
