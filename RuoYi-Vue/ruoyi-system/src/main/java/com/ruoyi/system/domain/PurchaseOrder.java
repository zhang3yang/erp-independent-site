package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 采购订单主表
 */
public class PurchaseOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 商家编码 */
    private String shopCode;

    /** 采购单号 */
    private String purchaseCode;

    /** 供应商编码 */
    private String partnerCode;

    /** 供应商名称 */
    private String partnerName;

    /**
     * 商品内容摘要，形如「可乐等3种」。
     *
     * <p>由明细整体重算（{@link com.ruoyi.system.utils.GoodsSummaryBuilder}），
     * 只给列表页看，不参与任何计算。</p>
     */
    private String goodsSummary;

    /** 采购日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date purchaseDate;

    /** 预计到货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectDate;

    /** 总金额（价税合计） */
    @PriceField(PriceScope.COST)
    private BigDecimal totalAmount;

    /** 运费类型：free-包邮/prepaid-寄付/collect-到付 */
    private String freightType;

    /**
     * 预估运费。说明书 3.4 流程2 只在「到付」场景由采购员下单时录入，
     * 其余运费类型统一归零，避免出现包邮却挂着预估运费的矛盾数据。
     */
    @PriceField(PriceScope.COST)
    private BigDecimal estimatedFreight;

    /**
     * 实际运费（汇总）。由到货单完成处理时按本次运费累加，不接受客户端直接写入，
     * 因此不在 updatePurchaseOrder 的更新列里，只能走 increaseActualFreight。
     */
    @PriceField(PriceScope.COST)
    private BigDecimal actualFreight;

    /**
     * 已支付货款。由财务模块的付款流水完成时累加（说明书 5.4 流程1 末步），
     * 不接受客户端写入，因此不在 updatePurchaseOrder 的更新列里，只能走 increasePaidAmount。
     */
    @PriceField(PriceScope.COST)
    private BigDecimal paidAmount;

    /** 付款状态：未支付/部分支付/已支付/超额支付，由 increasePaidAmount 在 SQL 内算出 */
    private String payStatus;

    /** 物流单号。采购员在订单审核后录入，库管员据此创建到货单。 */
    private String logisticsNo;

    /** 承运商 */
    private String carrier;

    /** 审核人 */
    private String confirmUser;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmTime;

    /** 状态：draft/submitted/approved/partial_received/completed/rejected/cancelled */
    private String status;

    /** 驳回原因。审核驳回时必填，供采购员据此修改后重新提交。 */
    private String rejectReason;

    /** 备注 */
    private String remark;

    /**
     * 退款窗口截止时刻，不落库。只有「可退款采购单」下拉返回的行才带值，
     * 供前端显示这张单还剩多久能退。口径见 RefundWindowResolver。
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private java.util.Date refundDeadline;

    public java.util.Date getRefundDeadline() { return refundDeadline; }

    public void setRefundDeadline(java.util.Date refundDeadline) { this.refundDeadline = refundDeadline; }

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    private Date updateTime;

    /** 创建人昵称（非数据库字段，查询时按用户账号关联 sys_user 带出，供前端展示） */
    private String createUserName;

    /** 审核人昵称（非数据库字段，查询时按用户账号关联 sys_user 带出，供前端展示） */
    private String confirmUserName;

    /** 采购订单明细列表（非数据库字段） */
    private List<PurchaseOrderItem> items;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getPurchaseCode()
    {
        return purchaseCode;
    }

    public void setPurchaseCode(String purchaseCode)
    {
        this.purchaseCode = purchaseCode;
    }

    public String getPartnerCode()
    {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode)
    {
        this.partnerCode = partnerCode;
    }

    public String getPartnerName()
    {
        return partnerName;
    }

    public void setPartnerName(String partnerName)
    {
        this.partnerName = partnerName;
    }

    public String getGoodsSummary()
    {
        return goodsSummary;
    }

    public void setGoodsSummary(String goodsSummary)
    {
        this.goodsSummary = goodsSummary;
    }

    public Date getPurchaseDate()
    {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate)
    {
        this.purchaseDate = purchaseDate;
    }

    public Date getExpectDate()
    {
        return expectDate;
    }

    public void setExpectDate(Date expectDate)
    {
        this.expectDate = expectDate;
    }

    public BigDecimal getTotalAmount()
    {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount)
    {
        this.totalAmount = totalAmount;
    }

    public String getFreightType()
    {
        return freightType;
    }

    public void setFreightType(String freightType)
    {
        this.freightType = freightType;
    }

    public String getLogisticsNo()
    {
        return logisticsNo;
    }

    public void setLogisticsNo(String logisticsNo)
    {
        this.logisticsNo = logisticsNo;
    }

    public String getCarrier()
    {
        return carrier;
    }

    public void setCarrier(String carrier)
    {
        this.carrier = carrier;
    }

    public String getConfirmUser()
    {
        return confirmUser;
    }

    public void setConfirmUser(String confirmUser)
    {
        this.confirmUser = confirmUser;
    }

    public Date getConfirmTime()
    {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime)
    {
        this.confirmTime = confirmTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getCreateUser()
    {
        return createUser;
    }

    public void setCreateUser(String createUser)
    {
        this.createUser = createUser;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateUser()
    {
        return updateUser;
    }

    public void setUpdateUser(String updateUser)
    {
        this.updateUser = updateUser;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public BigDecimal getEstimatedFreight()
    {
        return estimatedFreight;
    }

    public void setEstimatedFreight(BigDecimal estimatedFreight)
    {
        this.estimatedFreight = estimatedFreight;
    }

    public BigDecimal getActualFreight()
    {
        return actualFreight;
    }

    public void setActualFreight(BigDecimal actualFreight)
    {
        this.actualFreight = actualFreight;
    }

    public BigDecimal getPaidAmount()
    {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount)
    {
        this.paidAmount = paidAmount;
    }

    public String getPayStatus()
    {
        return payStatus;
    }

    public void setPayStatus(String payStatus)
    {
        this.payStatus = payStatus;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getCreateUserName()
    {
        return createUserName;
    }

    public void setCreateUserName(String createUserName)
    {
        this.createUserName = createUserName;
    }

    public String getConfirmUserName()
    {
        return confirmUserName;
    }

    public void setConfirmUserName(String confirmUserName)
    {
        this.confirmUserName = confirmUserName;
    }

    public List<PurchaseOrderItem> getItems()
    {
        return items;
    }

    public void setItems(List<PurchaseOrderItem> items)
    {
        this.items = items;
    }
}
