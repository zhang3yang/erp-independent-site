package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/** 生产工单产出明细。 */
public class ProductionOrderDetail implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long id;
    private String shopCode;
    private String detailCode;
    private String productionCode;
    private Integer lineNo;
    private String productCode;
    private String productName;
    private BigDecimal planYQty;
    private BigDecimal actualYQty;
    /** 本行的物料消耗成本，完工时按本行配方实算并落库；费用调整时作为分摊基数 */
    @PriceField(PriceScope.COST)
    private BigDecimal materialCost;
    @PriceField(PriceScope.COST)
    private BigDecimal unitCost;
    @PriceField(PriceScope.COST)
    private BigDecimal totalCost;
    private String remark;

    /**
     * 本产出商品的物料清单（配方）。
     *
     * <p>不同产出商品的配方不一样，物料挂在产出行下而不是工单下。落库时展开成
     * u_p_productionmaterial 的平表（每行带 detailcode），读出来时再按 detailcode 归回各行。
     * 建单/改单的请求体也走这个嵌套结构。</p>
     */
    private List<ProductionMaterial> materials = new ArrayList<>();

    /**
     * 本产出为哪些销售订单明细生产（订单驱动工单专用，备货生产为空）。
     *
     * <p>不落明细表：展平后序列化进主表的 {@code orderallocation} JSON，读出来按产出行号归回。
     * 见 {@link ProductionOrderAllocation}。</p>
     */
    private List<ProductionOrderAllocation> allocations = new ArrayList<>();

    /**
     * 完工入库时这批成品的批次号（只用于完工请求，不落产出明细表）。
     *
     * <p>批次管理和序列号管理的成品都要有批次，留空由服务端生成；不做追踪的成品不能填。
     * 成品的批次是这一次生产出来的那一批，与工单一一对应，所以不在建单时录、只在完工时定。</p>
     */
    private String batchCode;

    /**
     * 完工入库时这批成品的序列号（只用于完工请求，不落产出明细表）。
     *
     * <p>序列号管理的成品必须逐个给出，个数要等于实际产出数量——序列号是生产环节贴上去的，
     * 系统生成不了。其余追踪方式的成品不能填。</p>
     */
    private List<String> serialNumbers = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }
    public String getProductionCode() { return productionCode; }
    public void setProductionCode(String productionCode) { this.productionCode = productionCode; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getPlanYQty() { return planYQty; }
    public void setPlanYQty(BigDecimal planYQty) { this.planYQty = planYQty; }
    public BigDecimal getActualYQty() { return actualYQty; }
    public void setActualYQty(BigDecimal actualYQty) { this.actualYQty = actualYQty; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public void setMaterialCost(BigDecimal materialCost) { this.materialCost = materialCost; }
    public List<ProductionMaterial> getMaterials() { return materials; }
    public void setMaterials(List<ProductionMaterial> materials) { this.materials = materials; }
    public List<ProductionOrderAllocation> getAllocations() { return allocations; }
    public void setAllocations(List<ProductionOrderAllocation> allocations) { this.allocations = allocations; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
}
