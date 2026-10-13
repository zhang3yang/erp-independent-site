package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退货可选批次。
 *
 * <p>批次商品按 FIFO 出库时，批次是过账阶段才定下来的，出库单明细上的 batchcode 为空，
 * 只有库存流水记下了真正扣的是哪一批。退货要原路退回，候选批次就从流水来。</p>
 */
public class SalesReturnBatchOption implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String goodsCode;
    private String warehouseCode;
    private String batchCode;

    /** 该批次在这张订单上出库的数量 */
    private BigDecimal qty;

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    /** 聚合键：商品 + 仓库 */
    public String key()
    {
        return goodsCode + "@" + (warehouseCode == null ? "" : warehouseCode);
    }
}
