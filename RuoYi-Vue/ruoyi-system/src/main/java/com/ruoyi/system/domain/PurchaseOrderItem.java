package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 采购订单明细表
 */
public class PurchaseOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 商家编码 */
    private String shopCode;

    /** 采购单号 */
    private String purchaseCode;

    /** 采购订单明细号 */
    private String detailCode;

    /** 行号 */
    private Integer lineNo;

    /** 商品编码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 采购数量 */
    private BigDecimal qty;

    /** 单价 */
    @PriceField(PriceScope.COST)
    private BigDecimal unitPrice;

    /** 不含税金额（qty × unitPrice） */
    @PriceField(PriceScope.COST)
    private BigDecimal amount;

    /** 税率（%） */
    private BigDecimal taxRate;

    /** 税额（amount × taxRate / 100） */
    @PriceField(PriceScope.COST)
    private BigDecimal taxAmount;

    /**
     * 建议入库仓库编码。
     * 说明书的采购明细不含仓库，入库仓库由到货单决定；
     * 本字段仅作为创建到货单时的默认值来源，存量数据保留，新单不再录入。
     */
    private String warehouseCode;

    /** 建议入库仓库名称，同 {@link #warehouseCode} */
    private String warehouseName;

    /** 已到货数量。只在采购入库单审核通过时才增加，代表真正进了仓库的量。 */
    private BigDecimal receivedQty;

    /**
     * 已处理数量 = 已到货 + 在途（已登记发货但还没进仓库的「待入库」与「入库中」）。
     *
     * <p>不落库，由 {@code /purchase/order/items} 现算后回填。剩余可发 = qty − processedQty。
     * 发货登记页拿它显示「已处理」列并限制预计到货数量，避免同一批货被重复登记发货。</p>
     */
    private transient BigDecimal processedQty;

    /** 历史批次号。下单阶段不再录入，仅用于存量数据展示。 */
    private String batchCode;
    /** 历史序列号。下单阶段不再录入，序列号统一在到货质检环节采集。 */
    private transient List<String> serialNumbers;
    private String serialNumbersText;

    /** 备注 */
    private String remark;

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

    public String getDetailCode()
    {
        return detailCode;
    }

    public void setDetailCode(String detailCode)
    {
        this.detailCode = detailCode;
    }

    public Integer getLineNo()
    {
        return lineNo;
    }

    public void setLineNo(Integer lineNo)
    {
        this.lineNo = lineNo;
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

    public BigDecimal getQty()
    {
        return qty;
    }

    public void setQty(BigDecimal qty)
    {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice()
    {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice)
    {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public BigDecimal getTaxRate()
    {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate)
    {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount()
    {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount)
    {
        this.taxAmount = taxAmount;
    }

    public String getWarehouseCode()
    {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode)
    {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName()
    {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName)
    {
        this.warehouseName = warehouseName;
    }

    public BigDecimal getReceivedQty()
    {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty)
    {
        this.receivedQty = receivedQty;
    }

    public BigDecimal getProcessedQty()
    {
        return processedQty;
    }

    public void setProcessedQty(BigDecimal processedQty)
    {
        this.processedQty = processedQty;
    }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
    public String getSerialNumbersText() { return serialNumbersText; }
    public void setSerialNumbersText(String serialNumbersText) { this.serialNumbersText = serialNumbersText; }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
