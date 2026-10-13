package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售可退商品。客户把已发出的货退回来（I 入库方向）。
 *
 * <p>按「商品 + 仓库」聚合：一张订单可以分多个批次、从不同仓库发出，
 * 退货必须退回它当初发出的那个仓库，否则库存对不上。</p>
 *
 * <pre>
 * 可退数量 = 已发数量 - 已退数量
 * 已发数量：该订单的发货批次生成的 CK 销售出库单中已审核的数量
 * 已退数量：该订单的 XSTHRK 销售退货入库单数量（含待审核，防止重复提交）
 * </pre>
 *
 * <p>{@link #costPrice} 是原出库的<b>实际成本</b>（出库明细 costamount ÷ qty，
 * 跨多次发货时为加权平均），退货入库按它计价。销售单价 {@link #salePrice}
 * 只带出来给页面显示退款金额参考，不参与库存计价。</p>
 */
public class SalesReturnableItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;

    /** 发货仓库，退货原路退回 */
    private String warehouseCode;
    private String warehouseName;

    /** 累计已发数量（已审核的销售出库单） */
    private BigDecimal shippedQty;

    /** 累计已退数量，含待审核的退货单 */
    private BigDecimal returnedQty;

    /** 可退数量 = shippedQty - returnedQty */
    private BigDecimal returnableQty;

    /** 原出库实际成本单价，退货入库按此计价 */
    private BigDecimal costPrice;

    /** 原出库累计成本金额，用于算加权成本 */
    private BigDecimal costAmount;

    /** 订单销售单价，仅供页面显示退款参考 */
    private BigDecimal salePrice;

    /**
     * 订单币种（ISO 4217），从来源销售订单带出来，只用于给 {@link #salePrice}
     * 算出的退款参考金额配符号。
     *
     * <p>不要拿它给 {@link #costPrice}/{@link #costAmount} 配符号：那两个是库存成本，
     * 记的是采购进来的本位币，与订单卖给客户用什么币种无关。</p>
     */
    private String currency;

    /** 商品追踪策略 none/batch/serial */
    private String batchStrategy;

    /**
     * 当初出库实际动用过的批次，退货原路退回时从这里选。
     *
     * <p>FIFO 出库的批次是过账阶段才定的，出库单明细上是空的，只有流水记了。
     * 不给候选的话用户根本不知道该填哪个批次号。</p>
     */
    private List<SalesReturnBatchOption> batchOptions = new ArrayList<>();

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public BigDecimal getShippedQty() { return shippedQty; }
    public void setShippedQty(BigDecimal shippedQty) { this.shippedQty = shippedQty; }

    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }

    public BigDecimal getReturnableQty() { return returnableQty; }
    public void setReturnableQty(BigDecimal returnableQty) { this.returnableQty = returnableQty; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBatchStrategy() { return batchStrategy; }
    public void setBatchStrategy(String batchStrategy) { this.batchStrategy = batchStrategy; }

    public List<SalesReturnBatchOption> getBatchOptions() { return batchOptions; }
    public void setBatchOptions(List<SalesReturnBatchOption> batchOptions) { this.batchOptions = batchOptions; }

    /** 聚合键：商品 + 仓库 */
    public String key()
    {
        return goodsCode + "@" + (warehouseCode == null ? "" : warehouseCode);
    }
}
