package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 生产工单物料清单快照。 */
public class ProductionMaterial implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long id;
    private String shopCode;
    private String materialCode;
    private String productionCode;
    /**
     * 所属产出明细代码：这一行料是给哪个产出商品用的。
     *
     * <p>配方按产出商品分开，不同产出用的料不一样，计划用量的分母也各是各的产出数量。
     * 同一个物料被两个产出用到时是两行，各自独立领料/消耗/退料，完工时物料成本才能
     * 归到具体的产出上。录入时由服务端按嵌套结构回填，前端不需要（也拿不到）这个值。</p>
     */
    private String detailCode;
    private Integer lineNo;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    private BigDecimal qtyPerUnit;
    /** 计划总用量 = 单耗 × 产出合计，是领料的上限 */
    private BigDecimal totalQty;
    /** 已领量，分批领料时逐次累加，领足（= totalQty）后工单转生产中 */
    private BigDecimal pickedQty;
    /** 实际消耗量，完工时录入，只有这部分计入工单成本 */
    private BigDecimal actualQty;
    /** 已退回量，退料时累加。可退数量 = pickedQty - actualQty - returnedQty */
    private BigDecimal returnedQty;
    private String remark;

    /**
     * 本次操作指定的批次，只在生产执行请求里使用，不落 u_p_productionmaterial。
     *
     * <p>领料、消耗、退料动的都是有批次追踪的库存，按批次管理（且成本算法是 FIFO 或
     * 个别计价）的物料必须指定批次，否则库存模块无法定位要搬哪一批的成本。一个物料
     * 一次要动多个批次时，请求里就给同一个 materialCode 发多行，每行一个批次。</p>
     */
    private transient String batchCode;

    /** 同 {@link #batchCode}，序列号管理的物料用这个，数量必须与本行数量一致。 */
    private transient List<String> serialNumbers;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getProductionCode() { return productionCode; }
    public void setProductionCode(String productionCode) { this.productionCode = productionCode; }
    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }
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
    public BigDecimal getQtyPerUnit() { return qtyPerUnit; }
    public void setQtyPerUnit(BigDecimal qtyPerUnit) { this.qtyPerUnit = qtyPerUnit; }
    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }
    public BigDecimal getPickedQty() { return pickedQty; }
    public void setPickedQty(BigDecimal pickedQty) { this.pickedQty = pickedQty; }
    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }
    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
}
