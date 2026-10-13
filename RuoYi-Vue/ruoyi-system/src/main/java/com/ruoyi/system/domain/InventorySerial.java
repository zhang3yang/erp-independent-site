package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 序列号实体类
 * 对应表：sdsshop.u_m_serial
 *
 * <p>序列号和库存一样按三维状态分格，状态名称由枚举派生，不落库。</p>
 */
public class InventorySerial implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 序列号 */
    private String serialNo;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    private String spec;

    private String unit;

    private String batchCode;

    /** 个别计价成本单价；历史数据为空时回退到所属批次成本 */
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;

    /** 仓库代码 */
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 良品状态：1-良品，0-不良品，见 {@link QualityFlag} */
    private Integer qualityFlag;

    /** 在库状态：1-在库，0-不在库，见 {@link OnhandFlag} */
    private Integer onhandFlag;


    /** 入库单号 */
    private String inboundDoc;

    private String inboundDetail;

    /** 出库单号 */
    private String outboundDoc;

    private String outboundDetail;

    /** 状态：instock-在库/outstock-已出库 */
    private String status;

    /** 入库时间 */
    private Date inTime;

    /** 出库时间 */
    private Date outTime;

    /** 采购单号 */
    private String purchaseCode;

    /** 供应商编码 */
    private String partnerCode;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public String getWareCode() { return wareCode; }
    public void setWareCode(String wareCode) { this.wareCode = wareCode; }

    public String getWareName() { return wareName; }
    public void setWareName(String wareName) { this.wareName = wareName; }

    public Integer getQualityFlag() { return qualityFlag; }
    public void setQualityFlag(Integer qualityFlag) { this.qualityFlag = qualityFlag; }

    public Integer getOnhandFlag() { return onhandFlag; }
    public void setOnhandFlag(Integer onhandFlag) { this.onhandFlag = onhandFlag; }


    /** 良品状态名称，由枚举派生给前端展示，不落库 */
    public String getQualityName() { return QualityFlag.getLabel(qualityFlag); }

    /** 在库状态名称，由枚举派生给前端展示，不落库 */
    public String getOnhandName() { return OnhandFlag.getLabel(onhandFlag); }


    /** 用三维状态一次性设置状态 */
    public void setStockState(StockState state)
    {
        this.qualityFlag = state == null ? null : state.getQualityFlag();
        this.onhandFlag = state == null ? null : state.getOnhandFlag();
    }

    /** 三维状态组合，供流转校验和报错文案使用 */
    public StockState getStockState() { return StockState.of(qualityFlag, onhandFlag); }

    public String getInboundDoc() { return inboundDoc; }
    public void setInboundDoc(String inboundDoc) { this.inboundDoc = inboundDoc; }

    public String getInboundDetail() { return inboundDetail; }
    public void setInboundDetail(String inboundDetail) { this.inboundDetail = inboundDetail; }

    public String getOutboundDoc() { return outboundDoc; }
    public void setOutboundDoc(String outboundDoc) { this.outboundDoc = outboundDoc; }

    public String getOutboundDetail() { return outboundDetail; }
    public void setOutboundDetail(String outboundDetail) { this.outboundDetail = outboundDetail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }

    public Date getOutTime() { return outTime; }
    public void setOutTime(Date outTime) { this.outTime = outTime; }

    public String getPurchaseCode() { return purchaseCode; }
    public void setPurchaseCode(String purchaseCode) { this.purchaseCode = purchaseCode; }

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    /** 创建人姓名（非数据库字段，查询时按登录名关联 sys_user.nick_name 带出，供前端展示） */
    private String createUserName;
    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
