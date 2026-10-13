package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.vo.PurchaseShortageItem;

/**
 * 采购发货（原「采购到货与质检」）。
 *
 * <p>业务侧定案：采购到货一律视为合格，不再有质检环节；入库由两个角色分两步完成——
 * 采购员在本模块登记发货（预计到货数量），入库员在库存模块做采购入库（实际到货数量）。
 * 因此本服务只负责发货侧，库存过账、签收信息采集都在
 * {@link IPurchaseInboundService#createInboundFromShipment} 那边。</p>
 *
 * <h3>状态机</h3>
 *
 * <p>一张发货单对应一次实际发货、一个物流单号，所以它是<b>整单入库</b>的：入库员
 * 要么把整单一次入完，要么一行都不入，不存在半张单入了一半的中间态。分批到货是
 * 采购员那边拆成多张发货单来表达，各自带自己的物流单号。</p>
 *
 * <p>真正的状态载体仍是<b>明细行</b>，单头状态由明细派生：</p>
 *
 * <pre>
 * 行状态（不落库，由 actualqty 与 inventorydetailcode 判定）
 *   未处理   actualqty IS NULL                                  → 计入在途量
 *   入库中   actualqty 有值 且 inventorydetailcode 为空          → 计入在途，入库单还在待审核
 *   已入库   inventorydetailcode 非空                            → 不计在途，已进采购订单 receivedqty
 *
 * 单头状态（派生，只有两个）
 *   待入库 ────────────> 已入库
 *   全部行已过账才算已入库；入库单建了但还没审核通过，货没进仓库，仍是待入库。
 * </pre>
 *
 * <p>某个商品整行没到货时，入库员填实际到货数量 0，该行按全额短装关闭，整单照样一次入完。
 * 短装差额通过 receivedqty 只加实收自然回到采购剩余可发量，采购员可以另开发货单继续发。</p>
 */
public interface IPurchaseReceiveService
{
    /**
     * 货还没进仓库。全部行未处理时可修改、可取消、可被入库员选中做入库；
     * 入库单已建但还在待审核（行处于「入库中」）时仍是本状态，但整单只读。
     */
    String STATUS_PENDING = "待入库";
    /** 全部明细行已过账，终态。 */
    String STATUS_STOCKED = "已入库";

    List<PurchaseReceive> selectReceiveList(PurchaseReceive query, String tableSuffix);

    PurchaseReceive selectReceiveDetail(String shopCode, String receiveCode, String tableSuffix);

    /**
     * 采购员登记发货。只接受发货日期、预计到货数量、预估运费；
     * 签收人、签收时间、实际到货数量都留给入库员。
     */
    PurchaseReceive createReceive(String shopCode, PurchaseReceive command, String username);

    /** 修改发货单。仅全部明细行未处理（{@link #STATUS_PENDING}）时允许。 */
    int updateDraft(String shopCode, PurchaseReceive command, String tableSuffix, String username);

    /** 取消发货单。仅全部明细行未处理时允许。 */
    int cancelReceive(String shopCode, String receiveCode, String tableSuffix, String username);

    /**
     * 查询可供入库的发货单：全部行都还没被占用的待入库单，供库存模块的采购入库页下拉选择。
     *
     * <p>跨全部月表查询，因为入库员并不知道发货单落在哪个月。</p>
     */
    List<PurchaseReceive> selectPendingShipments(String shopCode);

    /**
     * 跨月表定位发货单。
     *
     * <p>入库单上只存了发货单号，存不下月表后缀（DocumentMain 没有空闲字段，
     * 单号 DH+流水 也反推不出月份），因此由本方法遍历月表查找。</p>
     *
     * @return 命中的发货单，已带上 tableSuffix；找不到返回 null
     */
    PurchaseReceive findReceiveAcrossMonths(String shopCode, String receiveCode);

    /**
     * 按单头状态重算并落库。入库单创建、审核、驳回后都要调一次。
     *
     * @return 重算后的状态
     */
    String refreshStatus(String shopCode, String receiveCode, String tableSuffix, String username);

    /**
     * 某采购订单的短装汇总，跨全部月表统计。
     *
     * <p>决议 A1-2：短装差额自动留在采购剩余可发量里（receivedqty 只加实收的自然结果），
     * 这里额外提供「哪张发货单、哪个商品、短了多少」的明细供采购员追货。</p>
     */
    List<PurchaseShortageItem> selectShortageItems(String shopCode, String purchaseCode);

    /**
     * 按采购明细汇总在途量：已经登记发货、但还没进仓库的数量（「待入库」+「入库中」），跨全部月表。
     *
     * <p>采购剩余可发量 = 采购数量 − 已入库(receivedqty) − 本方法返回的在途量。
     * 发货登记页据此显示「已处理」列并限制预计到货数量，同时用来把发完的订单从下拉里滤掉。</p>
     *
     * @return key 为采购明细编码；没有在途量的明细不出现在结果里
     */
    Map<String, BigDecimal> selectPendingQtyByDetails(String shopCode, List<String> purchaseDetailCodes);
}
