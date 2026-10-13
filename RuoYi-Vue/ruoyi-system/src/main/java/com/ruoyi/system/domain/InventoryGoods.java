package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 商品实体类
 * 对应表：sdsshop.u_m_waregoods
 */
public class InventoryGoods implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 商品代码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 商品分类 */
    private String category;

    /**
     * 查询用：分类及其全部下级分类的名称。
     * 分类是树，按父分类查询时要把子分类下的商品一并带出来，
     * 由 Service 依据 category 展开，前端不传。
     */
    private List<String> categoryNames;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 商品类型：raw-原材料，semifinished-半成品，finished-成品 */
    private String goodsType;

    /** 批次策略：none/batch/serial */
    private String batchStrategy;

    /** 是否必须记录有效期 */
    private Integer requireExpireFlag;

    /** 成本计算方法：moving_avg/fifo/individual */
    private String costCalculation;

    /**
     * 参考价：只在商品主档展示，供业务人员开单时参照，不参与任何单据、成本或金额计算。
     * 销售出库的售价以入库成本为基准带出，与这里无关。
     */
    @PriceField(PriceScope.SALE)
    private BigDecimal standardPrice;

    /** 最低库存预警量 */
    private BigDecimal minStockQty;

    /** 最高库存预警量 */
    private BigDecimal maxStockQty;

    /** 商品主图URL */
    private String imgUrl;

    /** 主图缩略图URL */
    private String thumbUrl;

    /** 其他附加主图（逗号分隔） */
    private String mainImgs;

    /** 条形码 */
    private String barcode;

    /** 入库检查事项 */
    private String confirmItems;

    /** 是否启用 */
    private Integer activeFlag;

    /**
     * 是否已被单据使用：1-已建过单据或已有库存。
     *
     * <p>置 1 后，名称、规格型号、计量单位、商品类型、批次策略、成本计算方法就锁定不能再改，
     * 因为这些字段已经被单据明细和库存成本引用，改了会让历史单据对不上账。
     * 商品编码是主键，任何时候都不能改。</p>
     *
     * <p>在 DocumentServiceImpl 保存单据明细时置位，不区分审核状态——待审核的单据同样引用了这些字段。</p>
     */
    private Integer usedFlag;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    private Date updateTime;

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

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public List<String> getCategoryNames()
    {
        return categoryNames;
    }

    public void setCategoryNames(List<String> categoryNames)
    {
        this.categoryNames = categoryNames;
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

    public String getGoodsType()
    {
        return goodsType;
    }

    public void setGoodsType(String goodsType)
    {
        this.goodsType = goodsType;
    }

    public String getBatchStrategy()
    {
        return batchStrategy;
    }

    public void setBatchStrategy(String batchStrategy)
    {
        this.batchStrategy = batchStrategy;
    }

    public Integer getRequireExpireFlag()
    {
        return requireExpireFlag;
    }

    public void setRequireExpireFlag(Integer requireExpireFlag)
    {
        this.requireExpireFlag = requireExpireFlag;
    }

    public String getCostCalculation()
    {
        return costCalculation;
    }

    public void setCostCalculation(String costCalculation)
    {
        this.costCalculation = costCalculation;
    }

    public BigDecimal getStandardPrice()
    {
        return standardPrice;
    }

    public void setStandardPrice(BigDecimal standardPrice)
    {
        this.standardPrice = standardPrice;
    }

    public BigDecimal getMinStockQty()
    {
        return minStockQty;
    }

    public void setMinStockQty(BigDecimal minStockQty)
    {
        this.minStockQty = minStockQty;
    }

    public BigDecimal getMaxStockQty()
    {
        return maxStockQty;
    }

    public void setMaxStockQty(BigDecimal maxStockQty)
    {
        this.maxStockQty = maxStockQty;
    }

    public String getImgUrl()
    {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl)
    {
        this.imgUrl = imgUrl;
    }

    public String getThumbUrl()
    {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl)
    {
        this.thumbUrl = thumbUrl;
    }

    public String getMainImgs()
    {
        return mainImgs;
    }

    public void setMainImgs(String mainImgs)
    {
        this.mainImgs = mainImgs;
    }

    public String getBarcode()
    {
        return barcode;
    }

    public void setBarcode(String barcode)
    {
        this.barcode = barcode;
    }

    public String getConfirmItems()
    {
        return confirmItems;
    }

    public void setConfirmItems(String confirmItems)
    {
        this.confirmItems = confirmItems;
    }

    public Integer getActiveFlag()
    {
        return activeFlag;
    }

    public void setActiveFlag(Integer activeFlag)
    {
        this.activeFlag = activeFlag;
    }

    public Integer getUsedFlag()
    {
        return usedFlag;
    }

    public void setUsedFlag(Integer usedFlag)
    {
        this.usedFlag = usedFlag;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getCreateUser()
    {
        return createUser;
    }

    public void setCreateUser(String createUser)
    {
        this.createUser = createUser;
    }

    /** 创建人姓名（非数据库字段，查询时按登录名关联 sys_user.nick_name 带出，供前端展示） */
    private String createUserName;

    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateUser()
    {
        return updateUser;
    }

    public void setUpdateUser(String updateUser)
    {
        this.updateUser = updateUser;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
