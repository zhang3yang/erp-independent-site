package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 生产执行请求参数，不落生产工单表。 */
public class ProductionExecutionRequest implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String materialWareCode;
    private String productionWareCode;
    private String productWareCode;
    private Date operationTime;
    private String remark;

    /**
     * 完工时录入的实际产出数量，按 detailCode 匹配产出明细行，只读取 detailCode 和 actualYQty。
     * 不传或某行缺失时该行按计划数量入库，保持与旧调用兼容。
     */
    private List<ProductionOrderDetail> outputs;

    /**
     * 按物料行录入的数量，按 materialCode 匹配。
     *
     * <p>三段各读各的数量字段，缺失的物料行走兜底：</p>
     * <ul>
     *   <li>领料读 pickedQty「本次领料量」，缺失时按剩余未领量一次领足</li>
     *   <li>完工读 actualQty「实际消耗量」，缺失时按「已领 - 已退」全额消耗</li>
     *   <li>退料读 returnedQty「本次退回量」，缺失时把可退的全退回</li>
     * </ul>
     *
     * <p>按批次或序列号管理的物料还要给 batchCode / serialNumbers，库存模块才知道搬哪一批的
     * 成本。一次要动多个批次时，同一个 materialCode 发多行，每行一个批次。</p>
     */
    private List<ProductionMaterial> materials;

    /** 完工时录入的加工费（委外加工费、人工费） */
    private BigDecimal processingCost;
    /** 完工时录入的运费 */
    private BigDecimal freightCost;
    /** 完工时录入的其他费用 */
    private BigDecimal otherCost;

    public String getMaterialWareCode() { return materialWareCode; }
    public void setMaterialWareCode(String value) { this.materialWareCode = value; }
    public String getProductionWareCode() { return productionWareCode; }
    public void setProductionWareCode(String value) { this.productionWareCode = value; }
    public String getProductWareCode() { return productWareCode; }
    public void setProductWareCode(String value) { this.productWareCode = value; }
    public Date getOperationTime() { return operationTime; }
    public void setOperationTime(Date value) { this.operationTime = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { this.remark = value; }
    public List<ProductionOrderDetail> getOutputs() { return outputs; }
    public void setOutputs(List<ProductionOrderDetail> value) { this.outputs = value; }
    public List<ProductionMaterial> getMaterials() { return materials; }
    public void setMaterials(List<ProductionMaterial> value) { this.materials = value; }
    public BigDecimal getProcessingCost() { return processingCost; }
    public void setProcessingCost(BigDecimal value) { this.processingCost = value; }
    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal value) { this.freightCost = value; }
    public BigDecimal getOtherCost() { return otherCost; }
    public void setOtherCost(BigDecimal value) { this.otherCost = value; }
}
