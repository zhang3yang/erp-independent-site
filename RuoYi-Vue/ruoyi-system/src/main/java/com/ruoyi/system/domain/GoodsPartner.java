package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 商品供货关系实体类。
 * 对应表：sdsshop.u_m_goods_partner
 *
 * <p>「这个商品我从哪几家供应商进过货」，给采购下单和手工入库的供应商选择做推荐。
 * 由 PurchaseInboundServiceImpl 在采购入库单**审核通过**时累积，三条入库通道
 * （手工直接入库、采购订单入库、采购发货入库）都收口在那个方法上。</p>
 *
 * <p>纯派生表：不参与任何计算，只影响界面上的推荐排序。对不上就重跑
 * {@code sql/migration/20260904_goods_partner_recommend.sql} 刷一遍。</p>
 */
public class GoodsPartner implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 商品代码 */
    private String goodsCode;

    /** 供应商代码 */
    private String partnerCode;

    /** 供应商名称（冗余，展示用） */
    private String partnerName;

    /** 已审核采购入库次数 */
    private Integer inboundTimes;

    /** 最近一次入库单号 */
    private String lastDocCode;

    /** 最近一次入库的单据日期 */
    private Date lastDocDate;

    /** 最近一次入库的成本单价 */
    @PriceField(PriceScope.COST)
    private BigDecimal lastCostPrice;

    /**
     * 供应商当前是否启用。
     *
     * <p>查询时从 u_m_partner 关联出来，本表不存。停用的供应商仍然要显示在
     * 历史供货记录里（历史事实不会因为停用而消失），但界面上要标出来、不给选。</p>
     */
    private Integer activeFlag;

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

    public String getPartnerCode()
    {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode)
    {
        this.partnerCode = partnerCode;
    }

    public String getPartnerName()
    {
        return partnerName;
    }

    public void setPartnerName(String partnerName)
    {
        this.partnerName = partnerName;
    }

    public Integer getInboundTimes()
    {
        return inboundTimes;
    }

    public void setInboundTimes(Integer inboundTimes)
    {
        this.inboundTimes = inboundTimes;
    }

    public String getLastDocCode()
    {
        return lastDocCode;
    }

    public void setLastDocCode(String lastDocCode)
    {
        this.lastDocCode = lastDocCode;
    }

    public Date getLastDocDate()
    {
        return lastDocDate;
    }

    public void setLastDocDate(Date lastDocDate)
    {
        this.lastDocDate = lastDocDate;
    }

    public BigDecimal getLastCostPrice()
    {
        return lastCostPrice;
    }

    public void setLastCostPrice(BigDecimal lastCostPrice)
    {
        this.lastCostPrice = lastCostPrice;
    }

    public Integer getActiveFlag()
    {
        return activeFlag;
    }

    public void setActiveFlag(Integer activeFlag)
    {
        this.activeFlag = activeFlag;
    }
}
