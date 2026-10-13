package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 销售订单的「商品描述」行。
 *
 * <p>销售自己写给客户和生产看的一份口径说明：客户谈单时说的是「定制礼盒 A 款 1000 个」，
 * 内部备货对应的可能是好几个 SKU。这张清单只有名称、数量、单价，
 * <b>不挂商品编码、不占库存、不参与发货，也不进货款合计</b>——
 * 货款仍旧只按 {@link SalesOrderItem}（货品明细）算。</p>
 *
 * <p>不落明细月表，整张清单序列化成 JSON 存在
 * {@code u_o_order_{yyyyMM}.descitems} 里：它不跟任何东西关联，
 * 开一张明细月表换来的只是多一套 DDL、mapper 和跨月清理逻辑。
 * 同库里 {@code u_p_production.orderallocation} 也是这么存的。</p>
 */
public class SalesOrderDescItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 名称 / 描述，必填 */
    private String name;
    /** 数量 */
    private BigDecimal qty;
    /** 销售单价 */
    @PriceField(PriceScope.SALE)
    private BigDecimal price;
    /**
     * 金额 ＝ 数量 × 单价。
     *
     * <p>服务层按前两列重算后落库，不收前端传进来的值——它只是给人看的合计，
     * 让前端自己算等于把「金额和明细对不上」这种脏数据放进库里。</p>
     */
    @PriceField(PriceScope.SALE)
    private BigDecimal amount;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
