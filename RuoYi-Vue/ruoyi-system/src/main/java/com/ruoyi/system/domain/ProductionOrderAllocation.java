package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产出分配：这张工单的某个产出，是为哪张销售订单的哪条明细生产的、生产多少。
 *
 * <p>对应说明书 4.3.1 的 {@code u_p_production.orderallocation}（TEXT，JSON）。<b>不建表</b>——
 * 说明书把生产模块框在 5 张表内，而分配关系只在建单时写、完工触发发货时读，
 * 没有独立的查询和统计诉求，一个 JSON 数组够用。</p>
 *
 * <p>落库时展平成工单级的一个 JSON 数组，每个元素带 {@link #outputLineNo} 标明归属哪个产出行；
 * 读出来按它归回 {@link ProductionOrderDetail#getAllocations()}。用行号而不是 detailcode，
 * 是因为改单会重建产出明细、detailcode 每次都变，行号在一次保存内稳定。</p>
 *
 * <p>{@link #tableSuffix} 必须带：销售订单按 createtime 分月表，回写
 * {@code stock_source/sourcecode} 时要靠它定位到哪张月表，跨月扫表代价太高。</p>
 */
public class ProductionOrderAllocation implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 归属的产出行号，对应 {@link ProductionOrderDetail#getLineNo()} */
    private Integer outputLineNo;
    /** 销售订单号 */
    private String orderCode;
    /** 销售订单所在月表后缀 yyyyMM */
    private String tableSuffix;
    /** 销售订单明细代码 */
    private String detailCode;
    /** 订单明细的商品编码，必须与所属产出行的产出商品一致 */
    private String goodsCode;
    private String goodsName;
    /** 本工单为这条订单明细生产的数量 */
    private BigDecimal qty;

    /**
     * 建单时该明细的未发量（订单量 − 已发量），<b>分配数量的上限就是它</b>。
     *
     * <p>存一份是为了改单时前端能把输入框的上限画出来；真正的校验一律现查订单明细，
     * 不信这个快照——两次保存之间可能又发了货。</p>
     */
    private BigDecimal pendingQty;

    /**
     * 建单时该明细的定制标记，只用于改单时回显，同样不参与校验。
     *
     * <p>存一份是因为改单时界面要标出「这条是定制品」，而分配是从工单的 JSON 读出来的，
     * 不会回头去查订单明细。它是快照：订单那边后来改过标记，这里不会跟着变。</p>
     */
    private Boolean customMade;

    public Integer getOutputLineNo() { return outputLineNo; }
    public void setOutputLineNo(Integer outputLineNo) { this.outputLineNo = outputLineNo; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }
    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }
    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getPendingQty() { return pendingQty; }
    public void setPendingQty(BigDecimal pendingQty) { this.pendingQty = pendingQty; }
    public Boolean getCustomMade() { return customMade; }
    public void setCustomMade(Boolean customMade) { this.customMade = customMade; }
}
