package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单行。对应表 {@code u_s_orderitem}。
 *
 * <h3>⚠ 每一行都是下单当时的商品快照，不 join 商品表</h3>
 * <p>{@link #goodsName} / {@link #imgUrl} / {@link #price} 都在这张表上存一份。
 * 商品改名、调价、下架都不该改变一张已经下的单——要的是当时那一份，不是现在那一份。</p>
 *
 * <p>这和 {@code u_s_sitegoods} 从「挑」变成「录」（boundary §5.4）是同一个道理，
 * 只是那边防的是跨库 join，这边防的是时间。</p>
 */
public class SiteOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String orderCode;

    private String goodsCode;
    private String goodsName;
    /**
     * 买家选中的组合编码（下单当时）。没选、或这个商品压根没有组合时为 null。
     *
     * <p>⚠ 不是外键：商家随后可能把那条型号删了，历史订单不受影响。</p>
     */
    private String comboCode;
    /** 型号名称快照。商家改名不影响历史订单 */
    private String comboName;
    /** 名称下面那行小字，一般是商品分类 */
    private String subtitle;
    private String imgUrl;

    /**
     * 单价快照。
     *
     * <p>⚠ <b>0 表示价格面议，不是免费。</b>外贸站大量商品不标价，
     * 前台显示的是「Inquire for price」。所以：</p>
     * <ul>
     *   <li>算小计时 0 的行要跳过，不能当 0 元算进去；</li>
     *   <li>显示时 0 要渲染成「面议」，不能渲染成 0.00。</li>
     * </ul>
     */
    private BigDecimal price;
    private String currency;

    /** 数量。⚠ 是小数不是整数：外贸按米、按千克下单很常见 */
    private BigDecimal qty;
    private String unit;

    /** 行小计 = price × qty。面议行为 0 */
    private BigDecimal lineTotal;

    /** 行序，保持买家清单里的顺序 */
    private Integer sortNum;

    /** 是不是面议行。前端和统计都用这个判，别到处写 {@code price == 0} */
    public boolean isInquireOnly()
    {
        return price == null || price.signum() <= 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getComboCode() { return comboCode; }
    public void setComboCode(String comboCode) { this.comboCode = comboCode; }

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
}
