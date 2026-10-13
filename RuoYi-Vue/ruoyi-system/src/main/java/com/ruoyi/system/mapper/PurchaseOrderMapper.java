package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.PurchaseOrder;

/**
 * 采购订单Mapper接口
 */
public interface PurchaseOrderMapper
{
    /**
     * 查询采购订单
     *
     * @param id 采购订单ID
     * @return 采购订单
     */
    public PurchaseOrder selectPurchaseOrderById(@Param("shopCode") String shopCode, @Param("id") Long id);

    /**
     * 查询采购订单（根据商家编码和采购单号）
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @return 采购订单
     */
    public PurchaseOrder selectPurchaseOrderByCode(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode);

    /** 锁定采购订单，序列化同一采购单的入库创建与审核。 */
    public PurchaseOrder selectPurchaseOrderByCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode);

    /**
     * 查询采购订单列表
     *
     * @param purchaseOrder 采购订单
     * @return 采购订单集合
     */
    public List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder);

    /**
     * 还要付款的采购单：已付货款没够订单总额的。供财务建采购付款流水时选单。
     *
     * <p>筛选写在 SQL 里而不是查回来再过滤：这是财务建单下拉的数据源，
     * 商家的采购单可以有几千张，全捞回内存再挑一遍既慢又容易被分页截断。</p>
     *
     * @param shopCode 商家编码
     * @param includeCode 无论如何都要带上的采购单号，可为空；用于改单时保住已经绑上的那张
     * @return 采购单集合，按创建时间倒序
     */
    public List<PurchaseOrder> selectPayableOrders(@Param("shopCode") String shopCode,
            @Param("includeCode") String includeCode);

    /**
     * 可退款的采购单：已经付过钱的（已付货款 &gt; 0）。供财务建采购退款流水时选单。
     *
     * <p>与 {@link #selectPayableOrders} 是相反的集合，且<b>包含已取消的单</b>——
     * 采购单取消后把预付款要回来，正是采购退款最常见的场景。</p>
     *
     * @param shopCode 商家编码
     * @param includeCode 无论如何都要带上的采购单号，可为空；用于改单时保住已经绑上的那张
     * @return 采购单集合，按创建时间倒序
     */
    public List<PurchaseOrder> selectRefundableOrders(@Param("shopCode") String shopCode,
            @Param("includeCode") String includeCode);

    /**
     * 新增采购订单
     *
     * @param purchaseOrder 采购订单
     * @return 结果
     */
    public int insertPurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 修改采购订单的内容（供应商、日期、金额、运费、备注）。
     *
     * <p>只更新业务员在改单表单里能填的那几列，且 SQL 里卡了 {@code status = 'draft'}。
     * 状态、审核人、审核时间、驳回原因都<b>不</b>在这条语句里——参数对象是前端提交的表单，
     * 把那几列放进来等于让「修改」权限可以自己批单子。状态流转一律走
     * {@link #updatePurchaseOrderStatus}。</p>
     *
     * @param purchaseOrder 采购订单
     * @return 更新行数，0 表示订单已不是草稿（被人抢先提交了）
     */
    public int updatePurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 采购订单状态流转。提交、审核（通过/驳回）、取消共用。
     *
     * <p>期望状态写在 SQL 的 where 里，影响行数为 0 即表示有人抢先改了状态，
     * 与库存单据、资金流水的状态流转是同一套写法。</p>
     *
     * @param status 目标状态
     * @param expectedStatuses 允许的当前状态，非空
     * @param rejectReason 驳回原因；<b>无条件写入</b>，审核通过时传 null 以清掉上一轮的原因，
     *        提交与取消传库里的原值表示保持不变
     * @param confirmUser 审核人，传 null 表示不动这一列
     * @param confirmTime 审核时间，传 null 表示不动这一列
     * @return 更新行数，0 表示状态已变化
     */
    public int updatePurchaseOrderStatus(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode,
            @Param("status") String status,
            @Param("expectedStatuses") List<String> expectedStatuses,
            @Param("rejectReason") String rejectReason,
            @Param("confirmUser") String confirmUser,
            @Param("confirmTime") java.util.Date confirmTime,
            @Param("updateUser") String updateUser);

    /**
     * 更新采购订单状态
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param status 状态
     * @return 结果
     */
    /**
     * 录入物流信息。仅已审核或部分到货的订单可更新，状态条件在 SQL 中判定。
     *
     * @return 更新行数，0 表示订单状态已变化
     */
    public int updateLogistics(@Param("shopCode") String shopCode, @Param("purchaseCode") String purchaseCode,
            @Param("logisticsNo") String logisticsNo, @Param("carrier") String carrier,
            @Param("updateUser") String updateUser);

    public int updateStatus(@Param("shopCode") String shopCode, @Param("purchaseCode") String purchaseCode,
            @Param("status") String status);

    /**
     * 累加实际运费。到货单完成处理时按本次运费调用，SQL 内自增以支持并发到货。
     *
     * @param amount 本次运费，调用方保证非负
     * @return 更新行数
     */
    public int increaseActualFreight(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode, @Param("amount") java.math.BigDecimal amount);

    /**
     * 累加已付货款并同步付款状态。财务模块的付款流水完成时调用（说明书 5.4 流程1 末步）。
     * paystatus 在 SQL 内按新的 paidamount 算出，支持同一采购单并发多笔付款。
     *
     * @param amount 本次付款金额，调用方传正数
     * @return 更新行数
     */
    public int increasePaidAmount(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode, @Param("amount") java.math.BigDecimal amount);

    /**
     * 删除采购订单
     *
     * @param id 采购订单ID
     * @return 结果
     */
    public int deletePurchaseOrderById(@Param("shopCode") String shopCode, @Param("id") Long id);

    /**
     * 批量删除采购订单
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deletePurchaseOrderByIds(@Param("shopCode") String shopCode, @Param("ids") Long[] ids);
}
