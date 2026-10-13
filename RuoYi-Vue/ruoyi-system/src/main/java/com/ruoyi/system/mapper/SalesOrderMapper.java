package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SalesOrder;

/**
 * 销售订单主表 Mapper。表名按 {@code tableSuffix} 拼月份，调用前必须校验后缀格式，
 * 未经校验的字符串不能拼进 SQL。
 */
public interface SalesOrderMapper
{
    /** 枚举已存在的订单月表后缀，用于跨月查询与定位。 */
    List<String> selectOrderTableSuffixes();

    List<SalesOrder> selectList(@Param("query") SalesOrder query, @Param("tableSuffix") String tableSuffix);

    /**
     * 按履约状态批量取单头。
     *
     * <p>发货下拉要的是「已备货/备货中/发货中」三个状态的单，一次 in 查完即可。
     * 逐个状态各发一次查询、再按订单逐张捞明细，是把同一份数据拆成了 N+1 次往返。</p>
     *
     * @param states 允许的履约状态，调用方保证非空；结果按 states 的先后分组，组内建单时间倒序
     */
    List<SalesOrder> selectByStates(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix, @Param("states") List<String> states);

    /** 按订单号批量取单头，排序与 {@link #selectList} 一致。orderCodes 由调用方保证非空。 */
    List<SalesOrder> selectByCodes(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix, @Param("orderCodes") List<String> orderCodes);

    /**
     * 可回款订单：排除掉指定状态、且「已收 &lt; 应收」的单。
     *
     * <p>下拉里只可能出现还欠钱的单，把这两个条件下推到 SQL；否则不传月份时
     * 会把所有月份的全部订单读进内存再逐条丢掉，代价随历史数据一起涨。</p>
     *
     * @param excludedStates 不参与回款的履约状态，调用方保证非空
     */
    List<SalesOrder> selectReceivableOrders(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix, @Param("excludedStates") List<String> excludedStates);

    /**
     * 可退款订单：排除掉指定状态、且已经收过钱的单。
     *
     * <p>退款时效窗口（最后一笔回款完成后多久内可退）依赖资金流水，留在服务层判断。</p>
     *
     * @param excludedStates 不参与退款的履约状态，调用方保证非空
     */
    List<SalesOrder> selectRefundableOrders(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix, @Param("excludedStates") List<String> excludedStates);

    SalesOrder selectByCode(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix);

    SalesOrder selectByCodeForUpdate(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix);

    int insert(@Param("order") SalesOrder order, @Param("tableSuffix") String tableSuffix);

    /** 只允许改草稿与已驳回的单。状态条件与服务层校验双保险。 */
    int updateDraft(@Param("order") SalesOrder order, @Param("tableSuffix") String tableSuffix);

    /**
     * 履约状态迁移，可一次给多个合法前置状态，并顺带写驳回/关闭原因。
     *
     * <p>与 {@link #updateOrderState} 的区别只在「多个前置状态 + 带原因」，
     * 同样靠 CAS 挡并发：更新 0 行说明状态已被别人推走。</p>
     *
     * @param expectedStates 合法的前置状态，为空表示不校验
     * @param rejectReason 驳回原因，传 null 表示不动这一列
     * @param closeReason 关闭原因，传 null 表示不动这一列
     */
    int updateStateWithReason(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix, @Param("orderState") String orderState,
            @Param("expectedStates") java.util.List<String> expectedStates,
            @Param("rejectReason") String rejectReason, @Param("closeReason") String closeReason);

    /**
     * 履约状态迁移，带期望值 CAS。并发下两个人同时推进状态时，
     * 后到的那个更新 0 行，服务层据此报「订单状态已变化」。
     */
    int updateOrderState(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix, @Param("orderState") String orderState,
            @Param("expectedState") String expectedState);

    /** 发货比例与履约状态一起回写，发货后由明细汇总重算得出，不做 CAS。 */
    int updateShipProgress(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix, @Param("sendPercent") BigDecimal sendPercent,
            @Param("orderState") String orderState);

    /**
     * 收款回写。金额在 SQL 内自增，多笔回款并发不丢数；支付状态与回款状态同源派生，
     * 由服务层算好一起传进来。首笔回款时补 paytime。
     */
    int applyPayment(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix, @Param("paidDelta") BigDecimal paidDelta,
            @Param("payStatus") String payStatus, @Param("receiveStatus") String receiveStatus,
            @Param("payTime") Date payTime);

    /**
     * 删除还没过审的订单。
     *
     * @param allowedStates 允许删除的履约状态，由 Service 传入（草稿与已驳回）；
     *                      写进 SQL 是并发兜底，状态变了就删 0 行
     */
    int deleteDraft(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix, @Param("allowedStates") List<String> allowedStates);
}
