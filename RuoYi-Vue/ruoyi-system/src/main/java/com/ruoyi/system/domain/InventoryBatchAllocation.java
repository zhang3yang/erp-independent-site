package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * FIFO 出库时的实际批次分配结果。
 */
public class InventoryBatchAllocation implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String batchCode;
    private BigDecimal qty;
    private BigDecimal costPrice;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;

    public String getBatchCode()
    {
        return batchCode;
    }

    public void setBatchCode(String batchCode)
    {
        this.batchCode = batchCode;
    }

    public BigDecimal getQty()
    {
        return qty;
    }

    public void setQty(BigDecimal qty)
    {
        this.qty = qty;
    }

    public BigDecimal getCostPrice()
    {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice)
    {
        this.costPrice = costPrice;
    }

    public BigDecimal getBeforeQty()
    {
        return beforeQty;
    }

    public void setBeforeQty(BigDecimal beforeQty)
    {
        this.beforeQty = beforeQty;
    }

    public BigDecimal getAfterQty()
    {
        return afterQty;
    }

    public void setAfterQty(BigDecimal afterQty)
    {
        this.afterQty = afterQty;
    }
}
