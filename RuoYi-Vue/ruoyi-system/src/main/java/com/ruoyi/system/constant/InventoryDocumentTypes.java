package com.ruoyi.system.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 库存核心业务单据类型。
 * 单据类型与业务单号前缀保持一致。
 */
public final class InventoryDocumentTypes
{
    /** 采购入库 */
    public static final String PURCHASE_INBOUND = "RK";

    /** 销售出库 */
    public static final String SALES_OUTBOUND = "CK";

    /**
     * 领料出库（已废弃）。
     *
     * <p>三段式改造前，领料直接永久出库，把说明书的 borrow_out 和 consume_out 合成了一步。
     * 现已拆成 {@link #PRODUCTION_PICK} + {@link #PRODUCTION_CONSUME}。
     * 常量保留只为让历史单据仍能识别类型，新单据不再使用。</p>
     */
    @Deprecated
    public static final String MATERIAL_OUTBOUND = "LLCK";

    /** 生产成品入库 */
    public static final String PRODUCT_INBOUND = "SCCPRK";

    /**
     * 生产退料入库（已废弃）。
     *
     * <p>改造前退料是入库方向，现改为 {@link #PRODUCTION_RETURN} 的状态回流。
     * 常量保留只为兼容历史单据。</p>
     */
    @Deprecated
    public static final String MATERIAL_RETURN = "SCTLRK";

    /**
     * 生产领料，对应说明书的 borrow_out。
     *
     * <p>方向 N，同仓库内 良品·在库 → 良品·不在库。车间不在仓库里，料领出去就离库，
     * 这一步不产生成本。</p>
     */
    public static final String PRODUCTION_PICK = "SCLL";

    /**
     * 生产消耗，对应说明书的 consume_out。
     *
     * <p>方向 O，从 良品·不在库（车间的在制品）永久出库。工单的物料成本来源于此。</p>
     */
    public static final String PRODUCTION_CONSUME = "SCXH";

    /**
     * 生产退料，对应说明书的 borrow_return。
     *
     * <p>方向 N，良品·不在库 → 良品·在库。退回来的料坏了要另开损坏登记，
     * 退料单本身不改良品状态。</p>
     */
    public static final String PRODUCTION_RETURN = "SCTL";

    /** 仓库调拨 */
    public static final String WAREHOUSE_TRANSFER = "DB";

    /** 采购订单来源类型 */
    public static final String PURCHASE_ORDER_SOURCE = "CG";

    /**
     * 采购发货单来源类型。
     *
     * <p>采购入库单由采购员登记的发货单生成，来源单号存发货单号而不是采购单号——
     * 采购单号可以从发货单上查到，反过来查不到。审核时靠这个类型识别出
     * 「要回写采购订单、要回填发货明细」的分支。</p>
     */
    public static final String PURCHASE_SHIPMENT_SOURCE = "FH";

    /** 销售订单来源类型 */
    public static final String SALES_ORDER_SOURCE = "SO";

    /**
     * 销售发货批次来源类型。
     *
     * <p>销售出库单由发货批次生成，来源单号存批次号而不是订单号——订单号可以从批次上
     * 查到，反过来一张订单有多个批次，查不回来。</p>
     *
     * <p>取值是 XSFH 不是 FH：{@link #PURCHASE_SHIPMENT_SOURCE} 已经占了 FH，
     * 而 u_f_docmain 的主键是 (doccode, sourcedoctype)，两边撞上会让采购入库的
     * 审核分支把销售单据认成采购发货单。</p>
     */
    public static final String SHIP_BATCH_SOURCE = "XSFH";

    /**
     * 销售补发来源类型。
     *
     * <p>补发是独立业务单，不是原销售订单上的第二个发货批次。出库单以补发单号为来源，
     * 这样不会回写原订单已发数量，也能从库存流水反查是哪一张补发单。</p>
     */
    public static final String SALES_RESHIP_SOURCE = "XSBF";

    /** 采购退货出库 */
    public static final String PURCHASE_RETURN_OUTBOUND = "CGTHCK";

    /**
     * 销售退货入库。
     *
     * <p>方向 I，客户把已发出的货退回来，重新进「良品·在库」。与采购退货
     * {@link #PURCHASE_RETURN_OUTBOUND} 对称：那边是货出去，这边是货回来。</p>
     *
     * <p>入库成本取原销售出库单的实际成本（加权），不是订单售价——退货是把当初
     * 出库的那笔成本原样还回库存池，用售价入库会凭空抬高库存总成本。</p>
     */
    public static final String SALES_RETURN_INBOUND = "XSTHRK";

    /**
     * 盘盈入库。
     *
     * <p>方向 I，实盘比账面多出来的部分。只由盘点单审核生成并当场过账，
     * 出入库页面不能手工开，调整库存必须带着盘点单这条证据。</p>
     */
    public static final String STOCKTAKE_GAIN = "PYRK";

    /**
     * 盘亏出库。
     *
     * <p>方向 O，实盘比账面少掉的部分，成本按出库实际成本结算（FIFO 自动跨批次、
     * 个别计价按盘点时指定的批次/序列号）。来源同 {@link #STOCKTAKE_GAIN}。</p>
     */
    public static final String STOCKTAKE_LOSS = "PKCK";

    /**
     * 盘点单来源类型。
     *
     * <p>盘盈入库、盘亏出库以盘点单号为来源。过账时的盘点冻结检查靠它放行
     * 盘点单自己生成的调整单，见 StocktakeFreezeGuard。</p>
     */
    public static final String STOCKTAKE_SOURCE = "PD";

    /**
     * 成本调整。
     *
     * <p>方向 C：数量不动，只改库存成本。采购运费后到要补进成本、入库价录错要更正时用。
     * 按商品的计价方式落到不同的成本层——移动平均改库存汇总、批次计价改批次单价、
     * 序列号个别计价改那几个序列号——见 {@link com.ruoyi.system.utils.CostAdjustLayer}。</p>
     *
     * <p>流水的 change_qty 为 0，看板按数量正负统计出入库金额，天然不会把它算进去。
     * 只能在「成本调整」页面建和审，通用单据接口的审核、修改都挡掉了。</p>
     */
    public static final String COST_ADJUST = "CBTZ";

    /** 借出 */
    public static final String LOAN = "JC";

    /** 归还 */
    public static final String RETURN = "GH";

    /** 损坏登记 */
    public static final String DAMAGE = "SHDJ";

    /**
     * 损坏出库（报废）。
     *
     * <p>方向 O，从「不良品·在库」直接永久出库。修不好或不值得修的坏货走这条路，
     * 不经过送修，出完库存和损坏数量一起减少。</p>
     */
    public static final String DAMAGE_OUTBOUND = "SHCK";

    /** 送修 */
    public static final String REPAIR_OUT = "SX";

    /** 维修归还 */
    public static final String REPAIR_IN = "WXGH";

    /**
     * 方向 N 的单据类型：货只是在企业内部换个仓、换个状态，并没有真的进出。
     *
     * <p>统计出入库金额时必须排除这一组。调拨写的是一进一出两条流水，
     * 金额相等方向相反（见 InventoryDocumentPostingService#writeTransferLedgers），
     * 算进去会让当月入库额和出库额同时虚增一笔，而且调得越多虚得越高。
     * 生产领料、借出归还、损坏登记、送修归还同理。</p>
     *
     * <p>废弃的 LLCK、SCTLRK 不在其中：它们当年就是按 O/I 方向记的，
     * 历史单据的金额本来就该算进去。</p>
     */
    public static final List<String> INTERNAL_MOVE_TYPES = Collections.unmodifiableList(
            Arrays.asList(WAREHOUSE_TRANSFER, PRODUCTION_PICK, PRODUCTION_RETURN,
                    LOAN, RETURN, DAMAGE, REPAIR_OUT, REPAIR_IN));

    private InventoryDocumentTypes()
    {
    }
}
