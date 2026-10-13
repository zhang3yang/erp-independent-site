package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 「录入快递单号」弹窗要的一整屏数据：本批次能发的行、各行单价，以及这张订单的付款额度。
 *
 * <p><b>为什么单开一个接口，不塞进批次详情</b>：算「已占额度」要把发货批次的月表枚举一遍
 * 再逐月查（见 {@code ShipBatchPendingQtyResolver} 的成本提示），而批次详情在打印、完成、
 * 改单等好几处都会读，每次都付这笔钱不值当。录单弹窗一次只开一张单，付得起。</p>
 *
 * <p>金额字段都带 {@link PriceField}：仓库岗位看这个 VO 会拿到一串 null，这是对的——
 * 录单是业务员的活，客户付了多少钱不该顺着接口漏给库管员。</p>
 */
public class ShipBatchLogisticsInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shipCode;
    private String orderCode;
    /** 订单币种（ISO 4217）。下面几个金额和 {@link Line#price} 都是这个币种 */
    private String currency;
    /**
     * 订单的国内收货方式，决定承运商与快递单号是不是必填。
     * 「上门提货」是客户自己开车来拉，没有承运商也没有面单。
     */
    private String receiveType;

    /** 订单总货款 */
    @PriceField(PriceScope.SALE)
    private BigDecimal totalAmount;
    /** 订单上客户已付的金额（库里叫 paidamount，站在公司一侧是「已收」）。发货货值的天花板就是它 */
    @PriceField(PriceScope.SALE)
    private BigDecimal paidAmount;
    /**
     * 这张订单上已经把额度占掉的货值：已完成批次（已回写 shippedqty）＋
     * 其它已录入/已打印快递单号的批次，<b>不含本批次</b>。
     */
    @PriceField(PriceScope.SALE)
    private BigDecimal committedAmount;
    /**
     * 本批次还能发多少货值 ＝ {@link #paidAmount} − {@link #committedAmount}，负数按 0 算。
     *
     * <p>前端拿它和「各行数量 × 单价」的合计比，业务员一边调数量一边就知道够不够，
     * 不用靠提交后报错试出来。后端提交时会用同一套口径再算一次，这里只是提前告知。</p>
     */
    @PriceField(PriceScope.SALE)
    private BigDecimal allowanceAmount;

    private List<Line> items = new ArrayList<>();

    /** 本批次的一行货：数量是仓库备货前排的计划量，业务员按实际备货情况往下调。 */
    public static class Line implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 发货批次明细代码，提交时按它定位改哪一行 */
        private String detailCode;
        private String goodsCode;
        private String goodsName;
        private String spec;
        private String unit;
        /** 批次上原本排的数量，弹窗里的默认值与上限 */
        private BigDecimal plannedQty;
        /** 来源订单明细上的单价，算货值用 */
        @PriceField(PriceScope.SALE)
        private BigDecimal price;

        public String getDetailCode() { return detailCode; }
        public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

        public String getGoodsCode() { return goodsCode; }
        public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getSpec() { return spec; }
        public void setSpec(String spec) { this.spec = spec; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public BigDecimal getPlannedQty() { return plannedQty; }
        public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

    public String getShipCode() { return shipCode; }
    public void setShipCode(String shipCode) { this.shipCode = shipCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReceiveType() { return receiveType; }
    public void setReceiveType(String receiveType) { this.receiveType = receiveType; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getCommittedAmount() { return committedAmount; }
    public void setCommittedAmount(BigDecimal committedAmount) { this.committedAmount = committedAmount; }

    public BigDecimal getAllowanceAmount() { return allowanceAmount; }
    public void setAllowanceAmount(BigDecimal allowanceAmount) { this.allowanceAmount = allowanceAmount; }

    public List<Line> getItems() { return items; }
    public void setItems(List<Line> items) { this.items = items; }
}
