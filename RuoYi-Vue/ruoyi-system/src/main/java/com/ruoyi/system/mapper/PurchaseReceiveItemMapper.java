package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.PurchaseReceiveItem;
import com.ruoyi.system.domain.vo.PurchaseShortageItem;

public interface PurchaseReceiveItemMapper
{
    List<String> selectReceiveTableSuffixes();

    List<PurchaseReceiveItem> selectByReceiveCode(@Param("shopCode") String shopCode,
            @Param("receiveCode") String receiveCode, @Param("tableSuffix") String tableSuffix);

    int batchInsert(@Param("items") List<PurchaseReceiveItem> items, @Param("tableSuffix") String tableSuffix);

    int deleteByReceiveCode(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 入库员建入库单时回填实际到货数量与追踪信息，行由「未处理」转「入库中」。
     *
     * <p>WHERE 带 {@code actualqty is null}，保证一行只会被一张入库单占用；
     * 返回 0 行说明这行已被别的入库单占了，调用方应报错让页面刷新。</p>
     */
    int fillActualQty(@Param("item") PurchaseReceiveItem item, @Param("tableSuffix") String tableSuffix);

    /**
     * 入库单审核通过后回填过账结果，行由「入库中」转「已入库」。
     * goodqty 写实际到货数量，badqty 写短装数量，供采购退货的可退量统计使用。
     */
    int markStocked(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix, @Param("inventoryDetailCode") String inventoryDetailCode,
            @Param("qualityStatus") String qualityStatus);

    /**
     * 入库单被驳回时清空回填内容，行退回「未处理」。
     * 只清尚未过账的行（{@code inventorydetailcode is null}），已过账的不受影响。
     */
    /**
     * 实际到货数量填 0 的行：整行没到货，按全额短装关闭（goodqty=0，badqty=qty）。
     *
     * <p>这种行没有库存明细，{@code inventorydetailcode} 写入库单号占位，
     * 否则行状态判不出「已入库」，整单会一直卡在「待入库」。</p>
     */
    int markShortClosed(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix, @Param("inventoryDocCode") String inventoryDocCode);

    int resetByInventoryDoc(@Param("shopCode") String shopCode, @Param("inventoryDocCode") String inventoryDocCode,
            @Param("tableSuffix") String tableSuffix);

    /** 某采购单在指定月表内的短装明细。 */
    List<PurchaseShortageItem> selectShortageByPurchase(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 某条采购明细在指定月表内的在途量。
     *
     * <p>口径：已经登记发货、但还没真正进仓库的数量，含行状态「未处理」与「入库中」
     * （{@code inventorydetailcode is null}）。「入库中」也要算：入库单一建出来
     * actualqty 就有值了，可采购订单的 receivedqty 要等入库单审核通过才加，
     * 不算进来这段量两头都统计不到，同一批货能被重复登记发货。</p>
     */
    BigDecimal selectPendingQty(@Param("shopCode") String shopCode,
            @Param("purchaseDetailCode") String purchaseDetailCode,
            @Param("excludeReceiveCode") String excludeReceiveCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * {@link #selectPendingQty} 的批量版：一次算一批采购明细在指定月表内的在途量。
     *
     * <p>只回填 {@code purchaseDetailCode} 与 {@code qty} 两个字段，没有在途量的明细不出现在结果里。</p>
     */
    List<PurchaseReceiveItem> selectPendingQtyByDetails(@Param("shopCode") String shopCode,
            @Param("detailCodes") List<String> detailCodes,
            @Param("tableSuffix") String tableSuffix);
}
