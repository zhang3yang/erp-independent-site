package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.FinanceTransaction;

/**
 * 资金流水 Mapper。按月分表，所有语句都要传 tableSuffix。
 */
public interface FinanceTransactionMapper
{
    /** 全部资金流水月表后缀，供跨月查询使用 */
    List<String> selectTransactionTableSuffixes();

    /**
     * 跨月分页查询。要扫的月表 union all 成一张派生表，排序和分页都在 SQL 里做完，
     * 只把当前页的行捞回来。
     *
     * <p>原来的做法是逐月查询、把全部结果读进内存排序再手工切页：数据量小的时候看不出来，
     * 但代价随「月表数 × 每月流水量」线性涨，而一页只要 10 条。</p>
     *
     * <p>每个 union 分支自己也带 {@code order by ... limit offset+limit}：全局的前 N 条
     * 必然落在「各月各自的前 N 条」里，先在分支内截断能让每张月表只回传够用的行。</p>
     *
     * @param suffixes 已通过白名单校验的月表后缀，调用方保证非空
     * @param offset 跳过多少行
     * @param limit 取多少行；传 0 或负数表示不分页，返回全部
     * @param cap 每个 union 分支最多回传多少行，取 offset + limit；limit &lt;= 0 时不生效
     */
    List<FinanceTransaction> selectTransactionPage(@Param("query") FinanceTransaction query,
            @Param("suffixes") List<String> suffixes,
            @Param("offset") int offset, @Param("limit") int limit, @Param("cap") int cap);

    /** 与 {@link #selectTransactionPage} 同条件的总行数，用于分页总数。suffixes 保证非空。 */
    long countTransactions(@Param("query") FinanceTransaction query, @Param("suffixes") List<String> suffixes);


    FinanceTransaction selectTransactionByCode(@Param("shopCode") String shopCode,
            @Param("transactionCode") String transactionCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 加锁读取。状态流转前必须走这个，防止两个人同时执行同一笔流水导致余额被加两次。
     * 不带 sys_user 左连，否则连出来的 sys_user 行会被一并锁住。
     */
    FinanceTransaction selectTransactionByCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("transactionCode") String transactionCode,
            @Param("tableSuffix") String tableSuffix);

    int insertTransaction(@Param("t") FinanceTransaction transaction,
            @Param("tableSuffix") String tableSuffix);

    /** 草稿态的内容修改。状态、审核人、完成人等流转字段不在这里。 */
    int updateTransaction(@Param("t") FinanceTransaction transaction,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 状态流转。where 里带上 expectedStatus，并发时只有一个人能改成功。
     */
    int updateTransactionStatus(@Param("t") FinanceTransaction transaction,
            @Param("expectedStatus") String expectedStatus,
            @Param("tableSuffix") String tableSuffix);

    int deleteTransactionByCode(@Param("shopCode") String shopCode,
            @Param("transactionCode") String transactionCode,
            @Param("tableSuffix") String tableSuffix);

    /** 某账户在指定月表内的流水条数，账户删除前的引用检查 */
    int countByAccount(@Param("shopCode") String shopCode,
            @Param("accountCode") String accountCode, @Param("tableSuffix") String tableSuffix);

    /** 某分类在指定月表内的流水条数，分类删除前的引用检查 */
    int countByCategory(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 某业务单据已完成的收付款金额合计（绝对值）。
     * 供采购模块查询付款进度、订单模块查询回款进度使用（说明书 6.4）。
     */
    java.math.BigDecimal sumCompletedAmountByBizCode(@Param("shopCode") String shopCode,
            @Param("bizType") String bizType, @Param("bizCode") String bizCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 指定月表里，每张业务单据最后一笔<b>正向</b>收付款的时间（采购看支出、销售看收入）。
     *
     * <p>退款时效窗口的起算点。只认「已完成」的付款。退款流水本身方向相反，天然被排除，
     * 否则退一笔就能把窗口无限续期。</p>
     *
     * @return 每行两列：bizCode、lastTime
     */
    List<java.util.Map<String, Object>> selectLastSettledTimeByBizType(
            @Param("shopCode") String shopCode, @Param("bizType") String bizType,
            @Param("transType") String transType, @Param("tableSuffix") String tableSuffix);

    /**
     * 某业务单据下的全部流水（含在途的草稿、待审核），供订单/采购单详情展示收付款明细。
     * 单号用精确匹配，不像列表查询那样 like——这里要的是「这张单的钱」，模糊匹配会串单。
     */
    List<FinanceTransaction> selectByBizCode(@Param("shopCode") String shopCode,
            @Param("bizType") String bizType, @Param("bizCode") String bizCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 待认领的客户回款：销售 + 收入 + 待审核，币种等于订单币种。
     *
     * <p>不看 bizcode 是否为空：改造前财务预填了单号、卡在待审核的老数据也要能从订单里认领走，
     * 预填的单号原样带回去给业务员参考。</p>
     */
    List<FinanceTransaction> selectUnclaimedReceipts(@Param("shopCode") String shopCode,
            @Param("currency") String currency, @Param("tableSuffix") String tableSuffix);

    /**
     * 解除与订单的绑定并改状态：bizcode、completeuser 置空，bizsummary 换成调用方给的占位文案。
     *
     * <p>{@link #updateTransactionStatus} 各字段用 coalesce 保留原值，清不掉字段，所以单独一条。
     * 撤回认领（已审核 → 待审核）和驳回已认领的回款（已审核 → 已驳回）共用，同样带 expectedStatus 防并发。</p>
     */
    int clearClaimBinding(@Param("t") FinanceTransaction transaction,
            @Param("expectedStatus") String expectedStatus,
            @Param("tableSuffix") String tableSuffix);
}
