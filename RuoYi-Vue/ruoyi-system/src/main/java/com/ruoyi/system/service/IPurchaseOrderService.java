package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.PurchaseOrder;

/**
 * 采购订单Service接口
 */
public interface IPurchaseOrderService
{
    /**
     * 查询采购订单
     *
     * @param id 采购订单ID
     * @return 采购订单
     */
    public PurchaseOrder selectPurchaseOrderById(String shopCode, Long id);

    /**
     * 查询采购订单（根据商家编码和采购单号）
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @return 采购订单
     */
    public PurchaseOrder selectPurchaseOrderByCode(String shopCode, String purchaseCode);

    /**
     * 查询采购订单列表
     *
     * @param purchaseOrder 采购订单
     * @return 采购订单集合
     */
    public List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder);

    /**
     * 还要付款的采购单（已付货款没够订单总额），供财务建采购付款流水时选单。
     *
     * <p>不分页、不限条数：这是下拉的数据源，被截断就等于有些单永远选不到。</p>
     *
     * @param shopCode 商家编码
     * @param includeCode 无论如何都要带上的采购单号，可为空；改单时用它保住已经绑上的那张
     * @return 采购单集合
     */
    public List<PurchaseOrder> selectPayableOrders(String shopCode, String includeCode);

    /**
     * 可退款的采购单（已经付过钱的），供财务建采购退款流水时选单。
     *
     * <p>与 {@link #selectPayableOrders} 是相反的集合，且包含已取消的单——
     * 采购单取消后把预付款要回来，正是采购退款最常见的场景。</p>
     *
     * @param shopCode 商家编码
     * @param includeCode 无论如何都要带上的采购单号，可为空；改单时用它保住已经绑上的那张
     * @return 采购单集合
     */
    public List<PurchaseOrder> selectRefundableOrders(String shopCode, String includeCode);

    /**
     * 新增采购订单
     *
     * @param purchaseOrder 采购订单
     * @return 结果
     */
    public int insertPurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 修改采购订单
     *
     * @param purchaseOrder 采购订单
     * @return 结果
     */
    public int updatePurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 批量删除采购订单
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deletePurchaseOrderByIds(String shopCode, Long[] ids);

    /**
     * 提交采购订单审核
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param updateUser 更新人
     * @return 结果
     */
    public int submitPurchaseOrder(String shopCode, String purchaseCode, String updateUser);

    /**
     * 审核采购订单
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param approved true-审核通过，false-驳回
     * @param rejectReason 驳回原因，驳回时必填，通过时忽略
     * @param updateUser 更新人
     * @return 结果
     */
    public int approvePurchaseOrder(String shopCode, String purchaseCode, boolean approved,
            String rejectReason, String updateUser);

    /**
     * 取消采购订单
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param updateUser 更新人
     * @return 结果
     */
    public int cancelPurchaseOrder(String shopCode, String purchaseCode, String updateUser);

    /**
     * 录入物流信息。仅已审核或部分到货的订单可录入。
     *
     * @param logisticsNo 物流单号
     * @param carrier 承运商
     * @return 结果
     */
    public int updateLogistics(String shopCode, String purchaseCode, String logisticsNo,
            String carrier, String updateUser);
}
