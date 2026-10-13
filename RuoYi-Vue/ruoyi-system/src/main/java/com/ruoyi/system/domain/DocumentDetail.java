package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 单据明细表实体类
 * 对应表：sdsorder.u_f_docdetail_{yyyyMM}
 */
public class DocumentDetail implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 明细代码 */
    private String detailCode;

    /** 单据代码 */
    private String docCode;

    /** 行号 */
    private Integer lineNo;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 规格型号 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 本行仓库代码 */
    private String wareCode;

    /** 本行仓库名称 */
    private String wareName;

    /** 良品状态：1-良品，0-不良品 */
    private Integer qualityFlag;

    /** 在库状态：1-在库，0-不在库 */
    private Integer onhandFlag;



    /** 数量 */
    private BigDecimal qty;

    /** 销售单价 */
    @PriceField(PriceScope.SALE)
    private BigDecimal salePrice;

    /** 销售金额 */
    @PriceField(PriceScope.SALE)
    private BigDecimal saleAmount;

    /** 成本单价 */
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;

    /** 成本金额 */
    @PriceField(PriceScope.COST)
    private BigDecimal costAmount;

    /** 批次代码 */
    private String batchCode;

    /**
     * 来源销售订单明细代码，只有销售出库才有值。
     *
     * <p>审核出库时挑批次要靠它：定制件的专属批次只发给自己那条明细，别人的一律跳过。
     * 建单和审核是两次调用，审核时明细是从库里读回来的，所以这个值必须落库，
     * 不能只在内存里传。</p>
     */
    private String orderDetailCode;

    /** 序列号列表（不持久化，仅用于传递数据） */
    private transient List<String> serialNumbers;

    /** 行备注 */
    private String remark;

    /** 月表后缀 */
    private String tableSuffix;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

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


    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public BigDecimal getSaleAmount() { return saleAmount; }
    public void setSaleAmount(BigDecimal saleAmount) { this.saleAmount = saleAmount; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getOrderDetailCode() { return orderDetailCode; }
    public void setOrderDetailCode(String orderDetailCode) { this.orderDetailCode = orderDetailCode; }

    public List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }
}
