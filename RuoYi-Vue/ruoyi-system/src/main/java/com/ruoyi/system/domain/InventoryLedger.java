package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 库存流水实体类
 * 对应表：sdsorder.u_f_ledger_{yyyyMM}
 */
public class InventoryLedger implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 流水号 */
    private String ledgerCode;

    /** 单据代码 */
    private String docCode;

    /** 单据类型 */
    private String docType;

    /** 单据类型名称 */
    private String docTypeName;

    /** 明细代码 */
    private String detailCode;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 仓库代码 */
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 良品状态：1-良品，0-不良品 */
    private Integer qualityFlag;

    /** 在库状态：1-在库，0-不在库 */
    private Integer onhandFlag;



    /** 变动前数量 */
    private BigDecimal beforeQty;

    /** 变动数量（正=入库，负=出库） */
    private BigDecimal changeQty;

    /** 变动后数量 */
    private BigDecimal afterQty;

    /** 成本单价 */
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;

    /** 成本金额 */
    @PriceField(PriceScope.COST)
    private BigDecimal costAmount;

    /** 批次号 */
    private String batchCode;

    /** 序列号 */
    private String serialNo;

    /** 来源单据代码 */
    private String sourceDocCode;

    /** 来源单据类型 */
    private String sourceDocType;

    /** 操作人 */
    private String operator;

    /** 操作人姓名（员工昵称）。不落库，查询时按 operator(登录名) 关联 sys_user.nick_name 补上，仅供展示 */
    private String operatorName;

    /** 操作时间 */
    private Date operateTime;

    /** 备注 */
    private String remark;

    /** 月表后缀 */
    private String tableSuffix;

    /**
     * 列表查询用的操作时间区间（非数据库字段）。
     *
     * <p>流水按 operate_time 分月表，这个区间既用来挑月表，也在月表内部再过滤一次。
     * 不填就查全部现存月表：查不到上个月的流水，比列表长一点严重得多。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTimeBegin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTimeEnd;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getLedgerCode() { return ledgerCode; }
    public void setLedgerCode(String ledgerCode) { this.ledgerCode = ledgerCode; }

    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDocTypeName() { return docTypeName; }
    public void setDocTypeName(String docTypeName) { this.docTypeName = docTypeName; }

    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

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

    public Integer getQualityFlag() { return qualityFlag; }
    public void setQualityFlag(Integer qualityFlag) { this.qualityFlag = qualityFlag; }

    public Integer getOnhandFlag() { return onhandFlag; }
    public void setOnhandFlag(Integer onhandFlag) { this.onhandFlag = onhandFlag; }


    /** 良品状态名称，由枚举派生给前端展示，不落库 */
    public String getQualityName() { return QualityFlag.getLabel(qualityFlag); }

    /** 在库状态名称，由枚举派生给前端展示，不落库 */
    public String getOnhandName() { return OnhandFlag.getLabel(onhandFlag); }


    /** 用三维状态一次性设置本行状态 */
    public void setStockState(StockState state)
    {
        this.qualityFlag = state == null ? null : state.getQualityFlag();
        this.onhandFlag = state == null ? null : state.getOnhandFlag();
    }

    /** 三维状态组合 */
    public StockState getStockState() { return StockState.of(qualityFlag, onhandFlag); }


    public BigDecimal getBeforeQty() { return beforeQty; }
    public void setBeforeQty(BigDecimal beforeQty) { this.beforeQty = beforeQty; }

    public BigDecimal getChangeQty() { return changeQty; }
    public void setChangeQty(BigDecimal changeQty) { this.changeQty = changeQty; }

    public BigDecimal getAfterQty() { return afterQty; }
    public void setAfterQty(BigDecimal afterQty) { this.afterQty = afterQty; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public String getSourceDocCode() { return sourceDocCode; }
    public void setSourceDocCode(String sourceDocCode) { this.sourceDocCode = sourceDocCode; }

    public String getSourceDocType() { return sourceDocType; }
    public void setSourceDocType(String sourceDocType) { this.sourceDocType = sourceDocType; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public Date getOperateTime() { return operateTime; }
    public void setOperateTime(Date operateTime) { this.operateTime = operateTime; }

    public Date getOperateTimeBegin() { return operateTimeBegin; }
    public void setOperateTimeBegin(Date operateTimeBegin) { this.operateTimeBegin = operateTimeBegin; }

    public Date getOperateTimeEnd() { return operateTimeEnd; }
    public void setOperateTimeEnd(Date operateTimeEnd) { this.operateTimeEnd = operateTimeEnd; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }
}
