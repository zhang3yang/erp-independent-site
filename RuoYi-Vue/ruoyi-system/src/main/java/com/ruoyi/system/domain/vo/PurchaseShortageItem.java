package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单的短装汇总行。
 *
 * <p>决议 A1-2：短装差额本身不需要额外机制——采购订单的 receivedqty 只累加实收，
 * 差额天然留在「采购剩余可发量」里，采购员直接再开发货单就能追。
 * 这个 VO 只是把「哪张发货单、哪个商品、短了多少」显式列出来供采购员查看。</p>
 */
public class PurchaseShortageItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 发生短装的发货单号 */
    private String receiveCode;
    /** 发货日期 */
    private String sendDate;
    private String goodsCode;
    private String goodsName;
    private String spec;
    private String unit;
    /** 预计到货数量 */
    private BigDecimal expectQty;
    /** 实际到货数量 */
    private BigDecimal actualQty;
    /** 短装数量 = max(预计 - 实际, 0) */
    private BigDecimal shortageQty;

    public String getReceiveCode() { return receiveCode; }
    public void setReceiveCode(String receiveCode) { this.receiveCode = receiveCode; }

    public String getSendDate() { return sendDate; }
    public void setSendDate(String sendDate) { this.sendDate = sendDate; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getExpectQty() { return expectQty; }
    public void setExpectQty(BigDecimal expectQty) { this.expectQty = expectQty; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public BigDecimal getShortageQty() { return shortageQty; }
    public void setShortageQty(BigDecimal shortageQty) { this.shortageQty = shortageQty; }
}
