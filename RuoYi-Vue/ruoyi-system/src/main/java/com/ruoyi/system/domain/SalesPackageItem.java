package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 套餐里的一行商品。
 *
 * <p>商品名称、规格、单位是<b>建套餐那一刻的快照</b>，与订单明细同一套做法：
 * 商品档案改了名，已建的套餐显示的仍是旧名，但下单时会按 goodsCode 重新取当前档案，
 * 所以铺进订单的永远是最新的名称与标准价。</p>
 */
public class SalesPackageItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String packageCode;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    /** 本套餐里这个商品的数量，下单时按它铺进明细 */
    private BigDecimal qty;
    private String remark;

    /**
     * 商品当前标准价，<b>不落库</b>，只有下单用的「可选套餐」接口才回填。
     *
     * <p>套餐本身不定价——定了价就等于又造了一套价目表，跟商品档案的标准价迟早对不上。
     * 下单铺进明细时按这个价带出，谈好的价在订单行上改。</p>
     */
    @PriceField(PriceScope.SALE)
    private BigDecimal standardPrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String packageCode) { this.packageCode = packageCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public BigDecimal getStandardPrice() { return standardPrice; }
    public void setStandardPrice(BigDecimal standardPrice) { this.standardPrice = standardPrice; }
}
