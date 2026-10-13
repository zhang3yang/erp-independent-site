package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 实时库存汇总实体类
 * 对应表：sdsshop.u_m_stock
 *
 * <p>库存按「商家 + 商品 + 仓库 + 良品状态 + 在库状态 + 占用类型」分格存放，
 * 后三个维度就是改造前的单一库存状态码拆出来的。三个维度的显示名称由枚举派生，
 * 不落库，避免冗余名称和编码对不上。</p>
 */
public class InventoryStock implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 仓库代码 */
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 良品状态：1-良品，0-不良品，见 {@link QualityFlag} */
    private Integer qualityFlag;

    /** 在库状态：1-在库，0-不在库，见 {@link OnhandFlag} */
    private Integer onhandFlag;


    /** 物理库存数量 */
    private BigDecimal physicalQty;

    /** 锁定数量（已创建出库单但尚未审核的数量） */
    private BigDecimal lockedQty;

    // 这里原先还有 reservedQty「订单预占数量」。订单预占整个取消了：销售订单从头到尾
    // 不占货，库存只在开出库单时被 lockedQty 锁住，审核过账才真扣。
    // u_m_stock.reservedqty 列还在库里（迁移脚本已把它清零），但不再有任何代码读写。

    /** 库存总价值 */
    @PriceField(PriceScope.COST)
    private BigDecimal totalCostAmount;

    /** 加权平均成本价 */
    @PriceField(PriceScope.COST)
    private BigDecimal avgCostPrice;

    /** 乐观锁版本号 */
    private Integer version;

    /** 最后更新时间 */
    private Date lastUpdated;

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
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

    public String getWareCode()
    {
        return wareCode;
    }

    public void setWareCode(String wareCode)
    {
        this.wareCode = wareCode;
    }

    public String getWareName()
    {
        return wareName;
    }

    public void setWareName(String wareName)
    {
        this.wareName = wareName;
    }

    public Integer getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(Integer qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }

    public Integer getOnhandFlag()
    {
        return onhandFlag;
    }

    public void setOnhandFlag(Integer onhandFlag)
    {
        this.onhandFlag = onhandFlag;
    }

    /** 良品状态名称，由枚举派生给前端展示，不落库 */
    public String getQualityName()
    {
        return QualityFlag.getLabel(qualityFlag);
    }

    /** 在库状态名称，由枚举派生给前端展示，不落库 */
    public String getOnhandName()
    {
        return OnhandFlag.getLabel(onhandFlag);
    }

    /** 用二维状态一次性设置状态 */
    public void setStockState(StockState state)
    {
        this.qualityFlag = state == null ? null : state.getQualityFlag();
        this.onhandFlag = state == null ? null : state.getOnhandFlag();
    }

    /** 二维状态组合，供流转校验和报错文案使用 */
    public StockState getStockState()
    {
        return StockState.of(qualityFlag, onhandFlag);
    }

    public BigDecimal getPhysicalQty()
    {
        return physicalQty;
    }

    public void setPhysicalQty(BigDecimal physicalQty)
    {
        this.physicalQty = physicalQty;
    }

    public BigDecimal getLockedQty()
    {
        return lockedQty;
    }

    public void setLockedQty(BigDecimal lockedQty)
    {
        this.lockedQty = lockedQty;
    }

    /**
     * 获取可用库存数量 = 物理库存 - 待审核出库单锁定。
     *
     * <p>这是可用量的唯一口径，SQL 侧的强约束（InventoryStockMapper.xml 里那几条
     * {@code physicalqty - lockedqty >= 0}）必须与本方法保持一致。</p>
     */
    public BigDecimal getAvailableQty()
    {
        BigDecimal physical = this.physicalQty == null ? BigDecimal.ZERO : this.physicalQty;
        BigDecimal locked = this.lockedQty == null ? BigDecimal.ZERO : this.lockedQty;
        return physical.subtract(locked);
    }

    public BigDecimal getTotalCostAmount()
    {
        return totalCostAmount;
    }

    public void setTotalCostAmount(BigDecimal totalCostAmount)
    {
        this.totalCostAmount = totalCostAmount;
    }

    public BigDecimal getAvgCostPrice()
    {
        return avgCostPrice;
    }

    public void setAvgCostPrice(BigDecimal avgCostPrice)
    {
        this.avgCostPrice = avgCostPrice;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Date getLastUpdated()
    {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated)
    {
        this.lastUpdated = lastUpdated;
    }
}
