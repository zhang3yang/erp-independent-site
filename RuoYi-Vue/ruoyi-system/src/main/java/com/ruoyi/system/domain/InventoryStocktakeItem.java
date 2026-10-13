package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 库存盘点明细
 * 对应表：sdsshop.u_m_stocktake_item
 *
 * <p>一行 = 一个商品在盘点仓库里的一种良品状态（良品 / 不良品），只盘在库的货：
 * 借出在外、领到车间、送外维修的货不在仓库里，没法清点。</p>
 */
public class InventoryStocktakeItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private String shopCode;

    private String stocktakeCode;

    private String goodsCode;

    private String goodsName;

    private String spec;

    private String unit;

    /** 1 良品 / 0 不良品 */
    private Integer qualityFlag;

    /** 账面数量：建单时快照，审核时按当前库存刷新 */
    private BigDecimal bookQty;

    /** 实盘数量，null 表示还没盘 */
    private BigDecimal countedQty;

    /** 差异 = 实盘 - 账面，审核时回写 */
    private BigDecimal diffQty;

    /** 盘盈入库成本单价；盘亏不用它，按出库实际成本结算 */
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;

    /** 差异金额，盘盈为正、盘亏为负，审核时回写 */
    @PriceField(PriceScope.COST)
    private BigDecimal diffAmount;

    /** 个别计价的批次商品盘亏时，丢的是哪个批次 */
    private String batchCode;

    /** 序列号商品：盘亏丢了哪几个 / 盘盈多出哪几个，逗号或换行分隔 */
    private String serialNos;

    private String remark;

    /** 商品档案的批次策略 none / batch / serial，前端据此决定要不要填批次、序列号 */
    private String batchStrategy;

    /** 商品档案的成本计算方法 moving_avg / fifo / individual */
    private String costCalculation;

    /** 当前库存（详情接口实时查），盘点期间冻结，正常应等于账面数量 */
    private BigDecimal currentQty;

    /** 被待审核出库单占用的数量。盘亏后剩下的货不够这些单发，审核会被拒 */
    private BigDecimal lockedQty;

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

    public String getStocktakeCode()
    {
        return stocktakeCode;
    }

    public void setStocktakeCode(String stocktakeCode)
    {
        this.stocktakeCode = stocktakeCode;
    }

    public String getGoodsCode()
    {
        return goodsCode;
    }

    public void setGoodsCode(String goodsCode)
    {
        this.goodsCode = goodsCode;
    }

    public String getGoodsName()
    {
        return goodsName;
    }

    public void setGoodsName(String goodsName)
    {
        this.goodsName = goodsName;
    }

    public String getSpec()
    {
        return spec;
    }

    public void setSpec(String spec)
    {
        this.spec = spec;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public Integer getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(Integer qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }

    public BigDecimal getBookQty()
    {
        return bookQty;
    }

    public void setBookQty(BigDecimal bookQty)
    {
        this.bookQty = bookQty;
    }

    public BigDecimal getCountedQty()
    {
        return countedQty;
    }

    public void setCountedQty(BigDecimal countedQty)
    {
        this.countedQty = countedQty;
    }

    public BigDecimal getDiffQty()
    {
        return diffQty;
    }

    public void setDiffQty(BigDecimal diffQty)
    {
        this.diffQty = diffQty;
    }

    public BigDecimal getCostPrice()
    {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice)
    {
        this.costPrice = costPrice;
    }

    public BigDecimal getDiffAmount()
    {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount)
    {
        this.diffAmount = diffAmount;
    }

    public String getBatchCode()
    {
        return batchCode;
    }

    public void setBatchCode(String batchCode)
    {
        this.batchCode = batchCode;
    }

    public String getSerialNos()
    {
        return serialNos;
    }

    public void setSerialNos(String serialNos)
    {
        this.serialNos = serialNos;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getBatchStrategy()
    {
        return batchStrategy;
    }

    public void setBatchStrategy(String batchStrategy)
    {
        this.batchStrategy = batchStrategy;
    }

    public String getCostCalculation()
    {
        return costCalculation;
    }

    public void setCostCalculation(String costCalculation)
    {
        this.costCalculation = costCalculation;
    }

    public BigDecimal getCurrentQty()
    {
        return currentQty;
    }

    public void setCurrentQty(BigDecimal currentQty)
    {
        this.currentQty = currentQty;
    }

    public BigDecimal getLockedQty()
    {
        return lockedQty;
    }

    public void setLockedQty(BigDecimal lockedQty)
    {
        this.lockedQty = lockedQty;
    }
}
