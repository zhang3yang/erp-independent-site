package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

public class PurchaseReceiveItem implements Serializable
{
    private Long id; private String shopCode; private String detailCode; private String receiveCode;
    private String purchaseDetailCode; private String goodsCode; private String goodsName; private String spec; private String unit;
    /** 预计到货数量，采购员登记发货时填 */
    private BigDecimal qty;
    /**
     * 实际到货数量，入库员做采购入库时填。
     * {@code null} 表示该行还没入库——「未入库」和「收到 0 件」必须区分得开，
     * 行状态判定就建立在这个区分上，不要给它兜底成 0。
     */
    private BigDecimal actualQty;
    /** 入库审核通过时回填 = 实际到货数量。采购退货的可退量统计依赖本字段。 */
    private BigDecimal goodQty;
    /** 短装数量 = max(预计 - 实际, 0) */
    private BigDecimal badQty;
    /** 到货情况：齐套 / 短装 / 超发 */
    private String qualityStatus;
    @PriceField(PriceScope.COST)
    private BigDecimal price;
    @PriceField(PriceScope.COST)
    private BigDecimal amount;
    private String batchCode; private String serialNumbers;
    private String inventoryDocCode; private String inventoryDetailCode; private String remark;
    /** 入库仓库。说明书把仓库归属放在到货环节，合格品按本字段生成库存入库单。 */
    private String warehouseCode; private String warehouseName;
    /**
     * 商品追踪策略 none/batch/serial。
     * 查询时从商品主数据实时关联，不落库，确保质检时读到的是最新策略。
     */
    private String batchStrategy;
    /**
     * 行状态：未处理 / 入库中 / 已入库。
     * 由 actualQty 与 inventoryDetailCode 派生，不落库。
     */
    private String lineStatus;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getShopCode(){return shopCode;} public void setShopCode(String v){shopCode=v;}
    public String getDetailCode(){return detailCode;} public void setDetailCode(String v){detailCode=v;} public String getReceiveCode(){return receiveCode;} public void setReceiveCode(String v){receiveCode=v;}
    public String getPurchaseDetailCode(){return purchaseDetailCode;} public void setPurchaseDetailCode(String v){purchaseDetailCode=v;} public String getGoodsCode(){return goodsCode;} public void setGoodsCode(String v){goodsCode=v;}
    public String getGoodsName(){return goodsName;} public void setGoodsName(String v){goodsName=v;} public String getSpec(){return spec;} public void setSpec(String v){spec=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public BigDecimal getQty(){return qty;} public void setQty(BigDecimal v){qty=v;}
    public BigDecimal getActualQty(){return actualQty;} public void setActualQty(BigDecimal v){actualQty=v;}
    public BigDecimal getGoodQty(){return goodQty;} public void setGoodQty(BigDecimal v){goodQty=v;} public BigDecimal getBadQty(){return badQty;} public void setBadQty(BigDecimal v){badQty=v;}
    public String getQualityStatus(){return qualityStatus;} public void setQualityStatus(String v){qualityStatus=v;} public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getBatchCode(){return batchCode;} public void setBatchCode(String v){batchCode=v;} public String getSerialNumbers(){return serialNumbers;} public void setSerialNumbers(String v){serialNumbers=v;} public String getInventoryDocCode(){return inventoryDocCode;} public void setInventoryDocCode(String v){inventoryDocCode=v;} public String getInventoryDetailCode(){return inventoryDetailCode;} public void setInventoryDetailCode(String v){inventoryDetailCode=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
    public String getWarehouseCode(){return warehouseCode;} public void setWarehouseCode(String v){warehouseCode=v;}
    public String getWarehouseName(){return warehouseName;} public void setWarehouseName(String v){warehouseName=v;}
    public String getBatchStrategy(){return batchStrategy;} public void setBatchStrategy(String v){batchStrategy=v;}
    public String getLineStatus(){return lineStatus;} public void setLineStatus(String v){lineStatus=v;}
}
