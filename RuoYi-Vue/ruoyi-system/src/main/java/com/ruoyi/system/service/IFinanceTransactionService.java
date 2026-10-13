package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.FinanceTransaction;

/**
 * 资金流水服务（说明书 5.4 三条流程、5.5 接口1~7）
 *
 * <p>三条流程共用一张表，靠 biztype 区分走哪条路：</p>
 * <pre>
 *   采购（支出）  create → submit → audit(通过) → execute        流程1
 *   销售（收入）  create → submit → claim       → audit(通过)    流程2
 *   其他（支出）    create → submit → complete                    流程3
 * </pre>
 * <p>驳回对三条流程统一走 {@link #auditTransaction} 的 approved=false。</p>
 */
public interface IFinanceTransactionService
{
    /**
     * 查询流水列表。跨月合并，说明书 5.4 流程4 的时间范围策略在这里实现。
     *
     * <p>排序与切页都下推到 SQL，只把当前页读进内存：一次查询可能要扫多张月表，
     * 把全部命中行读回来再在内存里排序切片，代价随月表数量和历史数据一起涨。</p>
     *
     * @param pageNum 从 1 开始；小于 1 按 1 处理
     * @param pageSize 每页行数；小于 1 表示不分页，返回全部命中行
     */
    TransactionPage selectTransactionPage(FinanceTransaction query, int pageNum, int pageSize);

    /** 一页流水加满足条件的总行数。 */
    class TransactionPage
    {
        private final List<FinanceTransaction> rows;

        private final long total;

        public TransactionPage(List<FinanceTransaction> rows, long total)
        {
            this.rows = rows;
            this.total = total;
        }

        public List<FinanceTransaction> getRows() { return rows; }

        public long getTotal() { return total; }
    }

    FinanceTransaction selectTransactionByCode(String shopCode, String transactionCode,
            String tableSuffix);

    /** 接口1：创建资金流水，落在当月月表，状态草稿 */
    FinanceTransaction createTransaction(FinanceTransaction transaction, String username);

    /** 草稿或已驳回状态下修改内容 */
    int updateTransaction(FinanceTransaction transaction, String username);

    /**
     * 接口2：提交审核。
     *
     * <p>客户回款建单时就填了订单号的，提交即视为认领：直接进已审核等财务终审，
     * 认领人记为提交人。没填的进待审核，等业务员在销售订单详情里认领。</p>
     */
    int submitTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username);

    /**
     * 接口3：审核。
     *
     * <p>approved=true 时按业务类型分流：采购类从待审核到已审核（等财务打款）；
     * 销售类从已审核到已完成（认领绑定之后的终审，会更新账户余额）。
     * approved=false 时统一驳回，必须填原因。</p>
     */
    int auditTransaction(String shopCode, String transactionCode, String tableSuffix,
            boolean approved, String rejectReason, String username);

    /** 接口4：财务执行打款，已审核 → 已完成，更新账户余额并回写采购单 */
    int executeTransaction(String shopCode, String transactionCode, String tableSuffix,
            String accountCode, Date tradeTime, String attachment, String username);

    /**
     * 接口5：业务员认领并绑定订单，待审核 → 已审核。
     *
     * @param allowOverpay 认领金额超过订单待收时是否放行。默认不放行——超额认领终审后
     *        订单会变成「已收满」，从此被认领下拉剔除，后面真正属于它的回款再也绑不上。
     *        确实是客户多付或几张单合并打款时，由认领人确认后传 true。
     */
    int claimTransaction(String shopCode, String transactionCode, String tableSuffix,
            String bizCode, boolean allowOverpay, String username);

    /**
     * 撤回认领：已审核 → 待审核，清掉订单号、认领人，业务摘要换成「待认领」占位。
     *
     * <p>业务员在订单里认错了单，自己就能撤，不用再找财务驳回。只放认领人本人和有
     * finance:transaction:audit 权限的人。已完成的已经回写了订单已收金额，不能撤，走退款。</p>
     */
    int unclaimTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username);

    /**
     * 待认领的客户回款：销售 + 收入 + 待审核，币种等于传入的订单币种，跨月合并后按交易时间倒序。
     * 销售订单详情的「认领打款」弹窗用它选流水。
     */
    List<FinanceTransaction> selectUnclaimedReceipts(String shopCode, String currency);

    /** 接口6：完成流水（非业务类），待审核 → 已完成，更新账户余额 */
    int completeTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username);

    /** 删除草稿或已驳回的流水 */
    int deleteTransaction(String shopCode, String transactionCode, String tableSuffix);

    /**
     * 某业务单据已完成的收付款合计（说明书 6.4 对外接口）。
     * 采购模块查付款进度、订单模块查回款进度都走这里。
     */
    BigDecimal sumCompletedAmount(String shopCode, String bizType, String bizCode);

    /**
     * 某业务单据下的全部流水，跨月合并后按交易时间倒序（说明书 6.4 对外接口）。
     *
     * <p>与 {@link #sumCompletedAmount} 的区别：那个只给已完成的合计数，这个把在途的
     * 草稿、待审核、已审核也一并给出来。订单和采购单详情要的是「这笔钱走到哪一步了」，
     * 只看合计数会让业务员以为对方没打钱，其实是流水还压在财务的审核队列里。</p>
     */
    List<FinanceTransaction> selectByBusinessDocument(String shopCode, String bizType, String bizCode);
}
