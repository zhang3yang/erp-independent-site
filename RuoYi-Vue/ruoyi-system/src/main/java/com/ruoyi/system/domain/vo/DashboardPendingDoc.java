package com.ruoyi.system.domain.vo;

import java.io.Serializable;

/**
 * 看板上「待我处理」的一行，一个待审核队列一行。
 *
 * <p>为什么要拆：系统里压根没有一个统一的审核页面。库存单据里采购入库在入库信息、
 * 销售出库在出库信息、调拨在调拨信息；销售订单和采购订单又各有各的审批流，
 * 数据也不在同一张表上（前者是 {@code u_o_order_} 月表，后者是 {@code u_p_purchase}）。
 * 卡片只给一个总数的话，用户点下去无处可去。拆到队列，前端才能把每一行送到它该去的页面。</p>
 *
 * <h3>docType 的两种取值</h3>
 *
 * <ul>
 *   <li>库存单据：单据自己的类型代码，如 {@code RK} / {@code CK} / {@code DB}；</li>
 *   <li>另外两个队列：{@link #SALES_ORDER} / {@link #PURCHASE_ORDER} 这两个合成值。
 *       它们不是任何一张表里存在的单据类型，只是给前端认路用的键。库存的类型代码都是
 *       短码，不会和这两个下划线命名撞上。</li>
 * </ul>
 *
 * <p>{@link #docTypeName} 对库存单据取自单据自己存的名称而不是配置表，省一次跨库查；
 * 历史单据没存名称时为空，由前端退回展示类型代码。</p>
 *
 * @author wczl
 */
public class DashboardPendingDoc implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 合成类型：销售订单待审核 */
    public static final String SALES_ORDER = "SALES_ORDER";

    /** 合成类型：采购订单待审核 */
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";

    /** 单据类型代码，如 RK / CK / DB，或上面两个合成值 */
    private String docType;

    /** 单据类型名称，如「采购入库」 */
    private String docTypeName;

    /** 该队列待审核的张数 */
    private int docCount;

    /**
     * 这批单子落在哪张月表，形如 {@code 202609}；不分月的队列为空。
     *
     * <p>只有销售订单需要它：销售订单列表是<b>按月查</b>的（页面上那个月份选择器），
     * 而库存单据列表和采购订单列表都是跨月/单表的。不带月份跳过去，用户会落在当月的
     * 列表上，而那张待审核的单子可能是上个月建的——数字说有 3 张，列表一张都不显示。</p>
     *
     * <p>服务层填的是「最近一个还有待审核订单的月份」。跨多个月时前端只能定位到其中一个，
     * 这是月表分库的固有代价，不是这里能补全的。</p>
     */
    private String docMonth;

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDocTypeName() { return docTypeName; }
    public void setDocTypeName(String docTypeName) { this.docTypeName = docTypeName; }

    public int getDocCount() { return docCount; }
    public void setDocCount(int docCount) { this.docCount = docCount; }

    public String getDocMonth() { return docMonth; }
    public void setDocMonth(String docMonth) { this.docMonth = docMonth; }
}
