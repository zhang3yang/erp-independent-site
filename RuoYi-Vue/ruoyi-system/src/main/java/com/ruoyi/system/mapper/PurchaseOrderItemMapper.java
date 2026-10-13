package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.PurchaseOrderItem;

/**
 * 采购订单明细Mapper接口
 */
public interface PurchaseOrderItemMapper
{
    /**
     * 查询采购订单明细
     *
     * @param id 采购订单明细ID
     * @return 采购订单明细
     */
    public PurchaseOrderItem selectPurchaseOrderItemById(@Param("shopCode") String shopCode,
            @Param("id") Long id);

    /**
     * 根据采购单号查询明细列表
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @return 明细集合
     */
    public List<PurchaseOrderItem> selectItemsByPurchaseCode(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode);

    /** 按固定顺序锁定采购明细，供入库创建和审核使用。 */
    /** 一次取多张采购订单的明细，供发货下拉过滤「已全部发完」的订单用。 */
    public List<PurchaseOrderItem> selectItemsByPurchaseCodes(@Param("shopCode") String shopCode,
            @Param("purchaseCodes") List<String> purchaseCodes);

    public List<PurchaseOrderItem> selectItemsByPurchaseCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode);

    /**
     * 根据商品代码查询采购明细
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param goodsCode 商品代码
     * @return 采购明细
     */
    public PurchaseOrderItem selectItemByGoodsCode(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode, @Param("goodsCode") String goodsCode);
    public PurchaseOrderItem selectItemByDetailCode(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode, @Param("detailCode") String detailCode);

    /**
     * 新增采购订单明细
     *
     * @param item 采购订单明细
     * @return 结果
     */
    public int insertPurchaseOrderItem(PurchaseOrderItem item);

    /**
     * 批量新增采购订单明细
     *
     * @param items 明细列表
     * @return 结果
     */
    public int batchInsertPurchaseOrderItem(@Param("items") List<PurchaseOrderItem> items);

    /**
     * 修改采购订单明细
     *
     * @param item 采购订单明细
     * @return 结果
     */
    public int updatePurchaseOrderItem(PurchaseOrderItem item);

    /**
     * 更新已到货数量，SQL 内带超收拦截：累计超过采购数量时返回 0 行。
     *
     * <p>调用方靠返回 0 行来判定超收并报错，因此这个拦截条件不能删——
     * 已停用的「采购订单直接入库」旧通道仍然依赖它。
     * 采购发货入库那条新链路走 {@link #increaseReceivedQtyUnchecked}。</p>
     *
     * @param id 明细ID
     * @param incrementQty 本次增加的到货数量
     * @return 结果
     */
    public int increaseReceivedQty(@Param("shopCode") String shopCode,
            @Param("id") Long id, @Param("incrementQty") BigDecimal incrementQty);

    /**
     * 更新已到货数量，不做超收拦截。
     *
     * <p>决议 A1b：入库员点到多少就入多少，既不受发货单预计数量约束，
     * 也不受采购合同数量约束——仓库记录的是事实，不是合同。
     * 超收后 {@code remainingqty} 会变成负数，这是刻意保留的：
     * 夹到 0 就看不出超收了多少。前端把负值显示成「超收 N」。</p>
     *
     * <p>只给采购发货入库这条链路用。旧通道请继续用
     * {@link #increaseReceivedQty}，它的拦截是那边的保护。</p>
     */
    public int increaseReceivedQtyUnchecked(@Param("shopCode") String shopCode,
            @Param("id") Long id, @Param("incrementQty") BigDecimal incrementQty);

    /**
     * 删除采购订单明细
     *
     * @param id 采购订单明细ID
     * @return 结果
     */
    public int deletePurchaseOrderItemById(@Param("shopCode") String shopCode,
            @Param("id") Long id);

    /**
     * 根据采购单号删除所有明细
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @return 结果
     */
    public int deleteItemsByPurchaseCode(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode);
}
