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
 * 批次信息实体类
 * 对应表：sdsshop.u_m_batchinfo
 *
 * <p>批次和库存一样按三维状态分格，状态名称由枚举派生，不落库。</p>
 */
public class InventoryBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 批次号 */
    private String batchCode;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 仓库代码 */
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 良品状态：1-良品，0-不良品，见 {@link QualityFlag} */
    private Integer qualityFlag;

    /** 在库状态：1-在库，0-不在库，见 {@link OnhandFlag} */
    private Integer onhandFlag;


    /** 初始数量 */
    private BigDecimal initQty;

    /** 剩余数量 */
    private BigDecimal remainQty;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 采购成本单价 */
    @PriceField(PriceScope.COST)
    private BigDecimal costPrice;

    /** 采购单号 */
    private String purchaseCode;

    /**
     * 专属销售订单号。只作展示——判定一律看 {@link #ownerDetailCode}。
     */
    private String ownerOrderCode;

    /**
     * 专属销售订单明细代码：非空表示这一批货是为该明细<b>专门生产</b>的。
     *
     * <p>定制件不能靠「谁先发货谁拿到」——它是按某张订单的规格做出来的，被别的单发走就得
     * 重做一遍。所以工单完工入库时把批次绑到来源明细上，出库挑批次时跳过绑给别人的批次。</p>
     *
     * <p>与已经取消的订单预占的区别：预占扣的是<b>通用库存</b>，谁都能用的货被占住了；
     * 专属绑的是<b>本来就为这张单做出来的那一批</b>，别的单本来也不该拿。</p>
     */
    private String ownerDetailCode;

    /** 采购日期 */
    private Date purchaseDate;

    /** 采购金额 */
    @PriceField(PriceScope.COST)
    private BigDecimal purchaseAmount;

    /** 供应商编码 */
    private String partnerCode;

    /** 供应商名称 */
    private String partnerName;

    /** 生产日期 */
    private Date productDate;

    /** 有效期 */
    private Date expireDate;

    /** 状态：active-有效/closed-已关闭/expired-已过期 */
    private String status;

    /**
     * 这一批货实际存放的货位。
     *
     * <p>与商品档案上的存放位置（{@code u_m_goods_position}）是两个粒度，都保留：
     * 那张表是<b>商品维度</b>的「这个商品一般放哪儿」，一个商品只登记一条，属于主档；
     * 这里是<b>批次维度</b>的「这一批实际放在哪儿」。同一商品的不同批次完全可以分开放
     * ——先进先出拣货时要按批次找货，只知道商品放哪个区是不够的。</p>
     *
     * <p>入库建批次时默认取商品主档上登记的位置，之后由库管员按实际摆放调整。
     * 它是纯管理信息，不参与任何数量、成本、账务计算。</p>
     */
    private String position;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    private Date updateTime;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getWareCode() { return wareCode; }
    public void setWareCode(String wareCode) { this.wareCode = wareCode; }

    public String getWareName() { return wareName; }
    public void setWareName(String wareName) { this.wareName = wareName; }

    public Integer getQualityFlag() { return qualityFlag; }
    public void setQualityFlag(Integer qualityFlag) { this.qualityFlag = qualityFlag; }

    public Integer getOnhandFlag() { return onhandFlag; }
    public void setOnhandFlag(Integer onhandFlag) { this.onhandFlag = onhandFlag; }


    /** 良品状态名称，由枚举派生给前端展示，不落库 */
    public String getQualityName() { return QualityFlag.getLabel(qualityFlag); }

    /** 在库状态名称，由枚举派生给前端展示，不落库 */
    public String getOnhandName() { return OnhandFlag.getLabel(onhandFlag); }


    /** 用三维状态一次性设置状态 */
    public void setStockState(StockState state)
    {
        this.qualityFlag = state == null ? null : state.getQualityFlag();
        this.onhandFlag = state == null ? null : state.getOnhandFlag();
    }

    /** 三维状态组合，供流转校验和报错文案使用 */
    public StockState getStockState() { return StockState.of(qualityFlag, onhandFlag); }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getInitQty() { return initQty; }
    public void setInitQty(BigDecimal initQty) { this.initQty = initQty; }

    public BigDecimal getRemainQty() { return remainQty; }
    public void setRemainQty(BigDecimal remainQty) { this.remainQty = remainQty; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public String getPurchaseCode() { return purchaseCode; }
    public void setPurchaseCode(String purchaseCode) { this.purchaseCode = purchaseCode; }

    public String getOwnerOrderCode() { return ownerOrderCode; }
    public void setOwnerOrderCode(String ownerOrderCode) { this.ownerOrderCode = ownerOrderCode; }

    public String getOwnerDetailCode() { return ownerDetailCode; }
    public void setOwnerDetailCode(String ownerDetailCode) { this.ownerDetailCode = ownerDetailCode; }

    /** 这一批是不是被某张订单明细独占了。空串与 null 一样当没绑。 */
    public boolean isOwned()
    {
        return ownerDetailCode != null && !ownerDetailCode.trim().isEmpty();
    }

    /** 这一批能不能发给指定的订单明细：没绑的谁都能用，绑了的只认自己那条。 */
    public boolean isAvailableFor(String orderDetailCode)
    {
        return !isOwned() || ownerDetailCode.equals(orderDetailCode);
    }

    public Date getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Date purchaseDate) { this.purchaseDate = purchaseDate; }

    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public Date getProductDate() { return productDate; }
    public void setProductDate(Date productDate) { this.productDate = productDate; }

    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    /** 创建人姓名（非数据库字段，查询时按登录名关联 sys_user.nick_name 带出，供前端展示） */
    private String createUserName;
    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
