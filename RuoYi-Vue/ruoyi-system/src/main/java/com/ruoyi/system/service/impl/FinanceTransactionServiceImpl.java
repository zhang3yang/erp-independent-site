package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.FinanceAccount;
import com.ruoyi.system.domain.FinanceCategory;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.mapper.FinanceAccountMapper;
import com.ruoyi.system.mapper.FinanceCategoryMapper;
import com.ruoyi.system.mapper.FinanceTransactionMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.service.IFinanceTransactionService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 资金流水服务实现（说明书 5.4 三条流程）
 */
@Service
public class FinanceTransactionServiceImpl implements IFinanceTransactionService
{
    /** 资金流水的业务类型，与收支分类的 BIZ_TYPES 一一对应。 */
    private static final List<String> BIZ_TYPES = Arrays.asList("采购", "销售", "其他");

    /** 认领被撤回或驳回后，客户回款的业务摘要换成这句：原来的摘要在认领时已被订单摘要覆盖，回不去了 */
    static final String UNCLAIMED_SUMMARY = "客户回款，待业务员认领";

    /** 财务终审权限。撤回认领除认领人本人外，只放有这个权限的人 */
    private static final String PERMI_AUDIT = "finance:transaction:audit";

    /** 退款时效报错里的时间格式。SimpleDateFormat 非线程安全，用的地方现造一个，不要提成静态字段。 */
    private static final String ERROR_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    @Autowired
    private FinanceTransactionMapper transactionMapper;

    @Autowired
    private FinanceAccountMapper accountMapper;

    @Autowired
    private FinanceCategoryMapper categoryMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    /**
     * 销售订单服务。销售回款的绑定校验与回写都走它，不直接碰订单表——
     * 订单按月分表，且已收金额与支付/回款状态必须一起派生，散着写迟早对不上。
     */
    @Autowired
    private ISalesOrderService salesOrderService;

    @Autowired
    private InventoryDocumentCodeGenerator codeGenerator;

    /** 退款时效窗口。下拉里藏起来只是不方便选，这里才是真正拦住直接调接口的那道 */
    @Autowired
    private RefundWindowResolver refundWindowResolver;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 列表页分页查询：排序和切页都下推到 SQL，只把当前页读进内存。
     *
     * <p>不能用 PageHelper 的 startPage——一次查询要扫多张月表，PageHelper 只会作用在
     * 第一条 SQL 上。这里改成把月表 union all 起来，让数据库一次排好序再取这一页。</p>
     *
     * @param pageNum 从 1 开始；小于 1 按 1 处理
     * @param pageSize 每页行数；小于 1 表示不分页，返回全部命中行
     */
    @Override
    public TransactionPage selectTransactionPage(FinanceTransaction query, int pageNum, int pageSize)
    {
        List<String> suffixes = resolveQuerySuffixes(query);
        if (suffixes.isEmpty())
        {
            return new TransactionPage(new ArrayList<FinanceTransaction>(), 0L);
        }
        if (pageSize < 1)
        {
            List<FinanceTransaction> all = transactionMapper.selectTransactionPage(query, suffixes, 0, 0, 0);
            return new TransactionPage(all, (long) all.size());
        }
        int page = pageNum < 1 ? 1 : pageNum;
        int offset = (page - 1) * pageSize;
        // 分支内截断的上界：全局前 offset+pageSize 条必然落在各月各自的前 offset+pageSize 条里
        int cap = offset + pageSize;
        List<FinanceTransaction> rows = transactionMapper.selectTransactionPage(
                query, suffixes, offset, pageSize, cap);
        return new TransactionPage(rows, transactionMapper.countTransactions(query, suffixes));
    }

    /** 排序键：交易时间优先，没填交易时间的用创建时间兜底，两者都没有的排最后 */
    private Date sortKey(FinanceTransaction transaction)
    {
        if (transaction.getTradeTime() != null)
        {
            return transaction.getTradeTime();
        }
        return transaction.getCreateTime() == null ? new Date(0L) : transaction.getCreateTime();
    }

    /**
     * 本次查询要扫哪几张月表：createtime 范围覆盖到的月份 ∩ 实际存在的月表。
     *
     * <p>只有 createtime 能用来缩小扫描范围——它决定了流水落在哪张月表。
     * 没给 createtime 条件时（包括只按 tradetime 过滤）一律扫全部已存在的月表，
     * 月表内部按 tradetime 走 idx_transaction_shop_tradetime 索引过滤。</p>
     */
    private List<String> resolveQuerySuffixes(FinanceTransaction query)
    {
        List<String> existing = FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes());
        if (query.getCreateTimeBegin() == null && query.getCreateTimeEnd() == null)
        {
            return existing;
        }
        Set<String> wanted = new LinkedHashSet<String>(
                FinanceTableSuffixes.between(query.getCreateTimeBegin(), query.getCreateTimeEnd()));
        List<String> result = new ArrayList<String>();
        for (String suffix : existing)
        {
            if (wanted.contains(suffix))
            {
                result.add(suffix);
            }
        }
        return result;
    }

    @Override
    public FinanceTransaction selectTransactionByCode(String shopCode, String transactionCode,
            String tableSuffix)
    {
        String suffix = requireSuffix(tableSuffix);
        if (StringUtils.isEmpty(transactionCode))
        {
            return null;
        }
        return transactionMapper.selectTransactionByCode(shopCode, transactionCode.trim(), suffix);
    }

    // ------------------------------------------------------------------
    // 接口1：创建
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public FinanceTransaction createTransaction(FinanceTransaction transaction, String username)
    {
        validateContent(transaction);

        // 流水号由服务端生成，忽略客户端传入值
        transaction.setTransactionCode(codeGenerator.nextTransactionCode());
        transaction.setStatus(FinanceTransaction.STATUS_DRAFT);
        transaction.setCreateUser(username);
        // 落在哪张月表由 createtime 决定，新建就是当月
        transaction.setTableSuffix(FinanceTableSuffixes.current());

        transactionMapper.insertTransaction(transaction, transaction.getTableSuffix());
        return transaction;
    }

    @Override
    @Transactional
    public int updateTransaction(FinanceTransaction transaction, String username)
    {
        String suffix = requireSuffix(transaction.getTableSuffix());
        FinanceTransaction existing = lock(transaction.getShopCode(),
                transaction.getTransactionCode(), suffix);
        if (!FinanceTransaction.STATUS_DRAFT.equals(existing.getStatus())
                && !FinanceTransaction.STATUS_REJECTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有草稿或已驳回的流水才能修改，当前状态：" + existing.getStatus());
        }
        validateContent(transaction);
        // 被驳回的流水改完退回草稿，重新走一遍提交审核，审核痕迹不跳步
        if (FinanceTransaction.STATUS_REJECTED.equals(existing.getStatus()))
        {
            FinanceTransaction reset = flowCommand(transaction.getShopCode(),
                    transaction.getTransactionCode());
            reset.setStatus(FinanceTransaction.STATUS_DRAFT);
            reset.setRejectReason(null);
            transactionMapper.updateTransactionStatus(reset, FinanceTransaction.STATUS_REJECTED, suffix);
        }
        return transactionMapper.updateTransaction(transaction, suffix);
    }

    // ------------------------------------------------------------------
    // 接口2：提交审核
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int submitTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!FinanceTransaction.STATUS_DRAFT.equals(existing.getStatus())
                && !FinanceTransaction.STATUS_REJECTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有草稿或已驳回的流水才能提交，当前状态：" + existing.getStatus());
        }

        // 草稿可能是在规则收口前创建的，也可能在创建后遇到分类被停用。
        // 提交是进入审批流的最后一道入口，必须按当前配置重新复核，不能只信创建时的校验。
        validateCurrentRules(existing);

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        // 重新提交时清掉上一轮的驳回原因，避免详情页显示已失效的说明
        command.setRejectReason(null);

        // 客户回款且建单时就填了订单号：财务已经知道是哪张单（平台订单、客户备注了单号），
        // 提交即视为认领，直接进已审核等终审，不再让业务员去订单里再点一次。
        // 校验与认领同一套：订单存在且可收款、币种一致、不超待收。
        String bizCode = StringUtils.trimToNull(existing.getBizCode());
        if (isSaleReceipt(existing) && bizCode != null)
        {
            SalesOrder order = requireSalesOrder(shopCode, bizCode, false);
            CurrencySupport.requireSame(order.getCurrency(), existing.getCurrency(),
                    "订单「" + order.getOrderCode() + "」",
                    "本笔流水的收款账户「" + existing.getAccountName() + "」");
            // 这里没有「放行超额」的入口：超了就让财务改金额，
            // 或者清空订单号交给业务员在订单里认领、由他确认超额
            requireWithinReceivable(existing, order,
                    "。请修改金额；确实是客户多付的，清空订单号后提交，由业务员在订单里认领并确认超额");
            command.setStatus(FinanceTransaction.STATUS_AUDITED);
            command.setBizCode(order.getOrderCode());
            command.setBizSummary(buildSaleSummary(order, existing.getPartyName(), false));
            command.setCompleteUser(username);
            return requireAffected(transactionMapper.updateTransactionStatus(
                    command, existing.getStatus(), suffix));
        }

        command.setStatus(FinanceTransaction.STATUS_SUBMITTED);
        return requireAffected(transactionMapper.updateTransactionStatus(
                command, existing.getStatus(), suffix));
    }

    // ------------------------------------------------------------------
    // 接口3：审核
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int auditTransaction(String shopCode, String transactionCode, String tableSuffix,
            boolean approved, String rejectReason, String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        command.setAuditUser(username);
        command.setAuditTime(new Date());

        if (!approved)
        {
            // 驳回对三条流程统一：待审核和已审核都能驳回，驳回必须说明原因，
            // 否则经办人不知道该改什么，事后也无法追溯。
            if (!FinanceTransaction.STATUS_SUBMITTED.equals(existing.getStatus())
                    && !FinanceTransaction.STATUS_AUDITED.equals(existing.getStatus()))
            {
                throw new ServiceException("只有待审核或已审核的流水才能驳回，当前状态：" + existing.getStatus());
            }
            String reason = StringUtils.trimToNull(rejectReason);
            if (reason == null)
            {
                throw new ServiceException("驳回资金流水必须填写驳回原因");
            }
            if (reason.length() > 500)
            {
                throw new ServiceException("驳回原因不能超过500个字符");
            }
            command.setStatus(FinanceTransaction.STATUS_REJECTED);
            command.setRejectReason(reason);
            // 驳回已认领的回款要连绑定一起清掉：提交已经改成「填了单号即绑定」，
            // 留着旧单号，财务不改就重提会原样绑回那张认错的单。清掉等于回到「必须重新认领」，
            // 与改造前「驳回后重提再认领一次」的语义一致。
            if (isSaleReceipt(existing) && FinanceTransaction.STATUS_AUDITED.equals(existing.getStatus()))
            {
                command.setBizSummary(UNCLAIMED_SUMMARY);
                return requireAffected(transactionMapper.clearClaimBinding(
                        command, existing.getStatus(), suffix));
            }
            return requireAffected(transactionMapper.updateTransactionStatus(
                    command, existing.getStatus(), suffix));
        }

        // 流程2 回款：业务员认领绑定后的终审，通过即完成并入账
        if (isSaleReceipt(existing))
        {
            if (!FinanceTransaction.STATUS_AUDITED.equals(existing.getStatus()))
            {
                throw new ServiceException("回款流水要先由业务员认领绑定订单，当前状态：" + existing.getStatus());
            }
            return complete(existing, command, suffix, username);
        }

        if (!FinanceTransaction.STATUS_SUBMITTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有待审核的流水才能审核，当前状态：" + existing.getStatus());
        }
        if (!FinanceTransaction.BIZ_PURCHASE.equals(existing.getBizType())
                && !FinanceTransaction.BIZ_SALE.equals(existing.getBizType()))
        {
            throw new ServiceException("非业务流水请直接使用「完成」操作，不需要单独审核一步");
        }
        // 采购退款（供应商把钱退回来）：钱已经在账上了，没有「执行打款」这一步，审核通过即完成
        if (!needsExecuteStep(existing.getBizType(), existing.getTransType()))
        {
            return complete(existing, command, suffix, username);
        }
        // 流程1 采购付款、以及销售退款：审核通过后进入已审核，等财务选账户执行打款
        command.setStatus(FinanceTransaction.STATUS_AUDITED);
        command.setRejectReason(null);
        return requireAffected(transactionMapper.updateTransactionStatus(
                command, FinanceTransaction.STATUS_SUBMITTED, suffix));
    }

    // ------------------------------------------------------------------
    // 接口4：执行打款（流程1）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int executeTransaction(String shopCode, String transactionCode, String tableSuffix,
            String accountCode, Date tradeTime, String attachment, String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!FinanceTransaction.STATUS_AUDITED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有已审核的流水才能执行打款，当前状态：" + existing.getStatus());
        }
        if (!FinanceTransaction.TYPE_EXPENSE.equals(existing.getTransType()))
        {
            throw new ServiceException("执行打款只针对支出类流水");
        }

        String account = StringUtils.trimToNull(accountCode);
        if (account == null)
        {
            account = existing.getAccountCode();
        }
        if (StringUtils.isEmpty(account))
        {
            throw new ServiceException("请选择付款账户");
        }
        if (tradeTime == null)
        {
            throw new ServiceException("请填写实际交易时间");
        }

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        command.setAccountCode(account);
        command.setTradeTime(tradeTime);
        command.setAttachment(FinanceText.optional(attachment, "凭证附件路径", 1000));
        existing.setAccountCode(account);
        existing.setTradeTime(tradeTime);
        return complete(existing, command, suffix, username);
    }

    // ------------------------------------------------------------------
    // 接口5：认领绑定（流程2）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int claimTransaction(String shopCode, String transactionCode, String tableSuffix,
            String bizCode, boolean allowOverpay, String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!FinanceTransaction.STATUS_SUBMITTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有待认领的流水才能认领，当前状态：" + existing.getStatus());
        }
        if (!FinanceTransaction.BIZ_SALE.equals(existing.getBizType()))
        {
            throw new ServiceException("只有销售回款流水需要认领绑定订单");
        }
        if (!isSaleReceipt(existing))
        {
            // 退款不存在「不知道是谁打的钱」这个问题，建单时就绑死了
            throw new ServiceException("销售退款在建单时已绑定订单，不需要认领");
        }
        String code = StringUtils.trimToNull(bizCode);
        if (code == null)
        {
            throw new ServiceException("请填写要绑定的订单号");
        }
        // 绑定到不存在的订单，回写时会静默跳过——钱到账了、订单上却看不到，
        // 而且事后无从发现。所以在认领这一步就卡死，与采购付款绑定采购单的口径一致。
        // 走到这里必然是客户回款（上面已挡掉退款），所以 refund 恒为 false
        SalesOrder order = requireSalesOrder(shopCode, code, false);
        // 横向权限：业务员只能把钱认到自己名下的单上。订单详情本身按归属过滤，
        // 这条防的是直接调接口把钱认到同事的单上。有「查看全部订单」权限的（主管、财务）不受限。
        OwnershipScope.requireOwn(order.getStaffCode(), "订单", order.getOrderCode(),
                SalesOrderServiceImpl.PERMI_VIEW_ALL);
        // 认领是「这笔钱是哪张单的」——钱已经躺在某个账户里了，账户币种在建单时就定死。
        // 认到别的币种的订单上，订单的已收金额会被加上一笔完全不同的钱，所以在这里挡死，
        // 与前端只列同币种订单的过滤是同一条规则，前端能绕过，后端不能。
        CurrencySupport.requireSame(order.getCurrency(), existing.getCurrency(),
                "订单「" + order.getOrderCode() + "」",
                "本笔流水的收款账户「" + existing.getAccountName() + "」");
        if (!allowOverpay)
        {
            requireWithinReceivable(existing, order);
        }

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        command.setStatus(FinanceTransaction.STATUS_AUDITED);
        command.setBizCode(order.getOrderCode());
        command.setBizSummary(buildSaleSummary(order, existing.getPartyName(), false));
        // 说明书 5.4 流程2：completeuser 记的是认领的业务员，不是最后点通过的审核人
        command.setCompleteUser(username);
        command.setRejectReason(null);
        return requireAffected(transactionMapper.updateTransactionStatus(
                command, FinanceTransaction.STATUS_SUBMITTED, suffix));
    }

    // ------------------------------------------------------------------
    // 撤回认领（流程2）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int unclaimTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!isSaleReceipt(existing))
        {
            throw new ServiceException("只有客户回款有认领这一步，其他流水没有撤回认领");
        }
        if (FinanceTransaction.STATUS_COMPLETED.equals(existing.getStatus()))
        {
            // 已完成的钱已经加进订单的已收金额和账户余额，撤回等于倒账，必须走退款留痕
            throw new ServiceException("这笔回款已终审完成、已计入订单已收金额，不能撤回认领，需要更正请走退款");
        }
        if (!FinanceTransaction.STATUS_AUDITED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有已认领、还没终审的回款才能撤回认领，当前状态：" + existing.getStatus());
        }
        // 认领人本人可撤；别的业务员没有理由动不是自己认的钱；财务审核人本来就有驳回权，放行
        if (!StringUtils.equals(username, existing.getCompleteUser()) && !hasAuditPermission())
        {
            throw new ServiceException("这笔回款是「" + existing.getCompleteUser()
                    + "」认领的，只有认领人本人或财务审核人能撤回");
        }

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        command.setStatus(FinanceTransaction.STATUS_SUBMITTED);
        command.setBizSummary(UNCLAIMED_SUMMARY);
        command.setRejectReason(null);
        return requireAffected(transactionMapper.clearClaimBinding(
                command, FinanceTransaction.STATUS_AUDITED, suffix));
    }

    /** 当前登录人有没有财务终审权限。没有登录上下文（内部调用、单元测试）按没有算 */
    private boolean hasAuditPermission()
    {
        try
        {
            return SecurityUtils.hasPermi(PERMI_AUDIT);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 接口6：完成流水（流程3 非业务）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int completeTransaction(String shopCode, String transactionCode, String tableSuffix,
            String username)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!FinanceTransaction.STATUS_SUBMITTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有待审核的流水才能完成，当前状态：" + existing.getStatus());
        }
        if (FinanceTransaction.BIZ_PURCHASE.equals(existing.getBizType())
                || FinanceTransaction.BIZ_SALE.equals(existing.getBizType()))
        {
            throw new ServiceException("采购付款请走「审核 + 执行打款」，销售回款请走「认领 + 审核」");
        }

        FinanceTransaction command = flowCommand(shopCode, transactionCode);
        command.setAuditUser(username);
        command.setAuditTime(new Date());
        return complete(existing, command, suffix, username);
    }

    // ------------------------------------------------------------------
    // 完成入账：三条流程最终都汇到这里
    // ------------------------------------------------------------------

    /**
     * 置为已完成，更新账户余额并快照余额，最后回写业务单据。
     *
     * @param existing 加锁读到的当前流水，状态用作乐观条件
     * @param command 已经填好本次流转要写的字段
     */
    private int complete(FinanceTransaction existing, FinanceTransaction command,
            String suffix, String username)
    {
        String accountCode = command.getAccountCode() != null
                ? command.getAccountCode() : existing.getAccountCode();
        if (StringUtils.isEmpty(accountCode))
        {
            throw new ServiceException("流水没有指定账户，无法入账");
        }
        FinanceAccount account = accountMapper.selectAccountByCode(existing.getShopCode(), accountCode);
        if (account == null)
        {
            throw new ServiceException("账户不存在：" + accountCode);
        }
        if (Integer.valueOf(0).equals(account.getIsActive()))
        {
            throw new ServiceException("账户「" + account.getAccountName() + "」已停用，不能入账");
        }
        if (existing.getTradeTime() == null && command.getTradeTime() == null)
        {
            throw new ServiceException("请先填写实际交易时间再完成流水");
        }

        // 入账是钱真正进出账户的一刻，币种在这里定死为账户币种——余额是按账户币种累加的，
        // 记成别的币种等于把两种钱加到一起。绑了销售订单的流水在建单/认领时已经按订单币种
        // 锁过账户，这里再卡一道：审批期间账户或订单被改过，也不会让不同币种的钱串账。
        String accountCurrency = CurrencySupport.require(account.getCurrency());
        if (FinanceTransaction.BIZ_SALE.equals(existing.getBizType())
                && StringUtils.isNotEmpty(existing.getBizCode()))
        {
            CurrencySupport.requireSame(existing.getCurrency(), accountCurrency,
                    "订单「" + existing.getBizCode() + "」",
                    "账户「" + account.getAccountName() + "」");
        }
        // 采购付款是在这一步才选账户的（建单时账户可以留空），所以这道校验必须在这里再卡一次，
        // 不能只靠建单时的 applyCurrency。
        requirePurchaseInBaseCurrency(existing.getBizType(), existing.getBizCode(),
                accountCurrency, account.getAccountName());
        command.setCurrency(accountCurrency);

        // 余额自增用 SQL 内自增，同一账户并发完成多笔流水时不会丢数；
        // 自增后立刻回读，此时该行仍被本事务持有，读到的就是本笔流水完成后的余额。
        accountMapper.increaseBalance(existing.getShopCode(), accountCode, existing.getAmount());
        FinanceAccount updated = accountMapper.selectAccountByCode(existing.getShopCode(), accountCode);

        command.setStatus(FinanceTransaction.STATUS_COMPLETED);
        command.setAccountCode(accountCode);
        command.setAccountName(account.getAccountName());
        command.setBalance(updated == null ? null : updated.getCurrentBalance());
        command.setCompleteTime(new Date());
        // 流程2 在认领时已经记下了业务员，不覆盖；其余流程记当前操作人
        if (command.getCompleteUser() == null && existing.getCompleteUser() == null)
        {
            command.setCompleteUser(username);
        }
        command.setRejectReason(null);

        int affected = requireAffected(transactionMapper.updateTransactionStatus(
                command, existing.getStatus(), suffix));

        writeBackBusinessDocument(existing);
        return affected;
    }

    /**
     * 回写业务单据。
     *
     * <p>采购付款按说明书 5.4 流程1 回写 paidamount / paystatus；
     * 销售回款按流程2 回写订单的已收金额与支付/回款状态。</p>
     *
     * <p><b>两边不能共用一套代码</b>：{@code u_p_purchase.paidamount} 是「付出去的钱」，
     * {@code u_o_order.paidamount} 是「收进来的钱」，同名反向。走两条独立路径，
     * 销售侧只经 {@link ISalesOrderService#applyPayment}，它会按订单总额重新派生状态。</p>
     */
    private void writeBackBusinessDocument(FinanceTransaction transaction)
    {
        if (StringUtils.isEmpty(transaction.getBizCode()))
        {
            return;
        }
        if (FinanceTransaction.BIZ_PURCHASE.equals(transaction.getBizType()))
        {
            writeBackPurchaseOrder(transaction);
        }
        else if (FinanceTransaction.BIZ_SALE.equals(transaction.getBizType()))
        {
            writeBackSalesOrder(transaction);
        }
    }

    /**
     * 采购单回写。
     *
     * <p><b>金额要取反</b>：流水的符号是「钱对我方的进出」（支出为负），而采购单的
     * paidamount 是「付给供应商的钱」，两者方向相反。付款（支出 -100）应让已付货款 +100，
     * 供应商退款（收入 +100）应让已付货款 -100，取反正好对上。</p>
     */
    private void writeBackPurchaseOrder(FinanceTransaction transaction)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(
                transaction.getShopCode(), transaction.getBizCode());
        if (order == null)
        {
            // 单号是创建时校验过的，走到这里说明采购单被删了，不阻断已经打出去的款
            return;
        }
        purchaseOrderMapper.increasePaidAmount(transaction.getShopCode(), transaction.getBizCode(),
                transaction.getAmount().negate());
    }

    /**
     * 销售回款回写（说明书 5.4 流程2）。
     *
     * <p><b>必须跨月定位订单</b>：资金流水按 createtime 分月表，订单也按 createtime 分月表，
     * 但两者不是同一个月——8 月下的单 9 月才回款是常态。拿流水的月份去查订单会查不到，
     * 表现是「回款完成了但订单上的已收金额纹丝不动」，且没有任何报错。</p>
     */
    private void writeBackSalesOrder(FinanceTransaction transaction)
    {
        SalesOrder order = salesOrderService.findOrderAcrossMonths(
                transaction.getShopCode(), transaction.getBizCode());
        if (order == null)
        {
            // 认领时校验过，走到这里说明订单被删了，不阻断已经到账的钱
            return;
        }
        // 金额<b>原样带符号</b>传下去：订单的 paidamount 是「从客户收到的钱」，与流水同向。
        // 回款（收入 +100）让已收 +100，退款（支出 -30）让已收 -30。
        // 这里曾经写成 abs()，退款会被记成又收了一笔钱——账户扣了钱、订单却加了钱，一笔错两处。
        salesOrderService.applyPayment(transaction.getShopCode(), order.getOrderCode(),
                order.getTableSuffix(), transaction.getAmount(), transaction.getTradeTime());
    }

    /**
     * 认领金额不得超过订单待收，除非调用方显式放行。
     *
     * <p>为什么要拦：一旦超额认领并终审，订单就变成「超额收款」，而
     * {@link ISalesOrderService#selectReceivableOrders} 会把已收满的订单剔除，
     * 于是这张单再也不会出现在认领下拉里——后面真正属于它的回款永远绑不上去。
     * 一笔 99999 元误绑到 100 元的订单，代价是这张单从此报废。</p>
     *
     * <p>为什么不硬拒：钱确实已经到账了，硬拒会让这笔流水卡在待认领态无处安放。
     * 客户多付、几张单合并打款都是真事，所以留一个显式出口，由认领人确认后放行，
     * 而不是默默放过去。</p>
     */
    private void requireWithinReceivable(FinanceTransaction transaction, SalesOrder order)
    {
        requireWithinReceivable(transaction, order, "");
    }

    /** @param hint 接在报错后面的处理建议，提交自动绑定那条路用它告诉财务该怎么办 */
    private void requireWithinReceivable(FinanceTransaction transaction, SalesOrder order, String hint)
    {
        BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);
        BigDecimal amount = transaction.getAmount().abs();
        if (amount.compareTo(remaining) > 0)
        {
            throw new ServiceException("本笔回款 " + amount.toPlainString()
                    + " 超过订单「" + order.getOrderCode() + "」的待收 " + remaining.toPlainString()
                    + "，不允许超额回款" + hint);
        }
    }

    /**
     * 销售退款不能超过该订单的已收金额。
     *
     * <p>退款只能退「已经收到的钱」，没收到的没什么可退。
     * 已收金额为 0 时直接拦住，避免退款金额为 0 的无意义流水。</p>
     */
    private void requireWithinReceived(FinanceTransaction transaction, SalesOrder order)
    {
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        if (paid.signum() <= 0)
        {
            throw new ServiceException("订单「" + order.getOrderCode() + "」没有已收金额，没有可退的钱");
        }
        BigDecimal amount = transaction.getAmount().abs();
        if (amount.compareTo(paid) > 0)
        {
            throw new ServiceException("本笔退款 " + amount.toPlainString()
                    + " 超过订单「" + order.getOrderCode() + "」的已收金额 " + paid.toPlainString()
                    + "，不允许超额退款");
        }
    }

    /**
     * 采购付款不能超过这张采购单的待付货款（硬限制）。
     *
     * <p>总额还没算出来（明细没填）时无从判断超没超，放行。</p>
     */
    private void requireWithinPayable(FinanceTransaction transaction, PurchaseOrder order)
    {
        BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        // 总额还没算出来（明细没填）时无从判断超没超，放行
        if (total.signum() <= 0)
        {
            return;
        }
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);
        BigDecimal amount = transaction.getAmount().abs();
        if (amount.compareTo(remaining) > 0)
        {
            throw new ServiceException("本笔付款 " + amount.toPlainString()
                    + " 超过采购单「" + order.getPurchaseCode() + "」的待付 " + remaining.toPlainString()
                    + "，不允许超额付款");
        }
    }

    /**
     * 采购退款不能超过该采购单的已付金额。
     *
     * <p>供应商退款只能退「已经付出去的钱」，没付过的没什么可退。
     * 已付金额为 0 时直接拦住，避免退款金额为 0 的无意义流水。</p>
     */
    private void requireWithinPaid(FinanceTransaction transaction, PurchaseOrder order)
    {
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        if (paid.signum() <= 0)
        {
            throw new ServiceException("采购单「" + order.getPurchaseCode() + "」没有已付货款，没有可退的钱");
        }
        BigDecimal amount = transaction.getAmount().abs();
        if (amount.compareTo(paid) > 0)
        {
            throw new ServiceException("本笔退款 " + amount.toPlainString()
                    + " 超过采购单「" + order.getPurchaseCode() + "」的已付货款 " + paid.toPlainString()
                    + "，不允许超额退款");
        }
    }

    /**
     * 退款必须在时效窗口内：最后一笔正向收付款完成后 3 天。
     *
     * <p>下拉里已经把过期单据藏起来了，这里再拦一道——藏起来只是不方便选，
     * 直接调接口照样能绑。改一笔旧草稿时也会走到这里，所以过期的退款草稿改不动，
     * 只能删掉重来，这是有意的。</p>
     *
     * <p>只在建单/改单这一步卡。已经提交出去的退款流水不再查窗口，审核和打款照常走完，
     * 否则审批慢一点钱就卡死在半路。</p>
     */
    private void requireWithinRefundWindow(FinanceTransaction transaction, String bizType,
            String bizCode, String documentLabel)
    {
        Date settled = refundWindowResolver.lastSettledTime(transaction.getShopCode(), bizType, bizCode);
        if (settled == null)
        {
            throw new ServiceException(documentLabel + "「" + bizCode + "」没有任何已完成的"
                    + (FinanceTransaction.BIZ_PURCHASE.equals(bizType) ? "付款" : "回款")
                    + "记录，没有可退的钱");
        }
        if (!refundWindowResolver.withinWindow(settled))
        {
            java.text.DateFormat format = new java.text.SimpleDateFormat(ERROR_TIME_PATTERN);
            throw new ServiceException(documentLabel + "「" + bizCode + "」的退款时效已过。"
                    + "最后一笔" + (FinanceTransaction.BIZ_PURCHASE.equals(bizType) ? "付款" : "回款")
                    + "完成于 " + format.format(settled) + "，退款窗口为其后 "
                    + RefundWindowResolver.REFUND_WINDOW_DAYS + " 天，已于 "
                    + format.format(refundWindowResolver.deadline(settled)) + " 截止");
        }
    }

    // ------------------------------------------------------------------
    // 删除与对外查询
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int deleteTransaction(String shopCode, String transactionCode, String tableSuffix)
    {
        String suffix = requireSuffix(tableSuffix);
        FinanceTransaction existing = lock(shopCode, transactionCode, suffix);
        if (!FinanceTransaction.STATUS_DRAFT.equals(existing.getStatus())
                && !FinanceTransaction.STATUS_REJECTED.equals(existing.getStatus()))
        {
            throw new ServiceException("只能删除草稿或已驳回的流水，当前状态：" + existing.getStatus());
        }
        return transactionMapper.deleteTransactionByCode(shopCode, transactionCode, suffix);
    }

    @Override
    public BigDecimal sumCompletedAmount(String shopCode, String bizType, String bizCode)
    {
        if (StringUtils.isEmpty(bizCode))
        {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            BigDecimal amount = transactionMapper.sumCompletedAmountByBizCode(
                    shopCode, bizType, bizCode.trim(), suffix);
            if (amount != null)
            {
                total = total.add(amount);
            }
        }
        return total;
    }

    @Override
    public List<FinanceTransaction> selectByBusinessDocument(String shopCode, String bizType, String bizCode)
    {
        if (StringUtils.isEmpty(bizCode))
        {
            return Collections.emptyList();
        }
        List<FinanceTransaction> result = new ArrayList<>();
        // 单据的钱可以分几个月陆续到账，逐月扫完再统一排序
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            result.addAll(transactionMapper.selectByBizCode(shopCode, bizType, bizCode.trim(), suffix));
        }
        sortNewestFirst(result);
        return result;
    }

    @Override
    public List<FinanceTransaction> selectUnclaimedReceipts(String shopCode, String currency)
    {
        String required = CurrencySupport.require(currency);
        List<FinanceTransaction> result = new ArrayList<>();
        // 待认领的多半在最近一两个月，但补录和跨月到账都有，扫全部月表——待审核的行本来就少
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            result.addAll(transactionMapper.selectUnclaimedReceipts(shopCode, required, suffix));
        }
        sortNewestFirst(result);
        return result;
    }

    /** 跨月拼起来的结果按交易时间倒序，最新的一笔在最上面 */
    private void sortNewestFirst(List<FinanceTransaction> result)
    {
        Collections.sort(result, new Comparator<FinanceTransaction>()
        {
            @Override
            public int compare(FinanceTransaction left, FinanceTransaction right)
            {
                return sortKey(right).compareTo(sortKey(left));
            }
        });
    }

    // ------------------------------------------------------------------
    // 校验与工具
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 方向判定
    //
    // 业务类型（采购/销售）决定回写哪张单，收支方向决定走哪条流程。四种组合：
    //
    //   采购 + 支出  付款给供应商    create → submit → audit → execute → 已完成
    //   采购 + 收入  供应商退款      create → submit → audit          → 已完成
    //   销售 + 收入  客户回款        create → submit → claim → audit  → 已完成
    //   销售 + 支出  退款给客户      create → submit → audit → execute → 已完成
    //
    // 规律：钱要打出去的才有「执行打款」这一步（要选账户、要记实际付款时间）；
    // 认领只属于客户回款，因为只有它在收到钱时还不知道对应哪张单。
    // ------------------------------------------------------------------

    /**
     * 要不要走「执行打款」这一步：钱往外走的业务类流水才要，因为得选付款账户、记实际付款时间。
     *
     * <p>其他这类非业务流水不在此列——它走 complete 一步到位，
     * 账户在建单时就填好了。</p>
     */
    private boolean needsExecuteStep(String bizType, String transType)
    {
        return isBusinessDocument(bizType) && FinanceTransaction.TYPE_EXPENSE.equals(transType);
    }

    /**
     * 是不是退款——即钱的方向与该业务类型的常态相反。
     *
     * <p>采购的常态是付钱出去，收进来就是供应商退款；销售的常态是收钱进来，
     * 付出去就是退款给客户。<b>不能只看收支方向</b>：同样是「收入」，
     * 在采购里是退款、在销售里是正常回款。</p>
     */
    private boolean isRefund(String bizType, String transType)
    {
        if (FinanceTransaction.BIZ_PURCHASE.equals(bizType))
        {
            return FinanceTransaction.TYPE_INCOME.equals(transType);
        }
        if (FinanceTransaction.BIZ_SALE.equals(bizType))
        {
            return FinanceTransaction.TYPE_EXPENSE.equals(transType);
        }
        return false;
    }

    /** 客户回款：唯一需要业务员认领的流水 */
    private boolean isSaleReceipt(FinanceTransaction transaction)
    {
        return FinanceTransaction.BIZ_SALE.equals(transaction.getBizType())
                && FinanceTransaction.TYPE_INCOME.equals(transaction.getTransType());
    }

    /** 采购单或销售订单，即会回写业务单据的两种流水 */
    private boolean isBusinessDocument(String bizType)
    {
        return FinanceTransaction.BIZ_PURCHASE.equals(bizType)
                || FinanceTransaction.BIZ_SALE.equals(bizType);
    }

    /**
     * 创建与修改时的内容校验，同时补齐系统自动生成的字段：
     * 分类名称快照、金额符号、绑定单据的对方名称与业务摘要。
     */
    private void validateContent(FinanceTransaction transaction)
    {
        String transType = StringUtils.trimToNull(transaction.getTransType());
        if (!FinanceTransaction.TYPE_INCOME.equals(transType)
                && !FinanceTransaction.TYPE_EXPENSE.equals(transType))
        {
            throw new ServiceException("收支方向只能是「收入」或「支出」");
        }
        transaction.setTransType(transType);

        String bizType = StringUtils.trimToNull(transaction.getBizType());
        if (bizType == null || !BIZ_TYPES.contains(bizType))
        {
            throw new ServiceException("业务类型只能是：" + String.join("、", BIZ_TYPES));
        }
        transaction.setBizType(bizType);

        FinanceCategory category = categoryMapper.selectCategoryByCode(
                transaction.getShopCode(), StringUtils.trimToEmpty(transaction.getCategoryCode()));
        if (category == null)
        {
            throw new ServiceException("收支分类不存在，请先在收支分类里配置");
        }
        if (Integer.valueOf(0).equals(category.getIsActive()))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」已停用");
        }
        if (!bizType.equals(category.getBizType()))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」不属于业务类型「"
                    + bizType + "」");
        }
        // 其他业务收支都记：一收一支各对应一条系统分类，方向定了分类就定了
        if (FinanceTransaction.BIZ_OTHER.equals(bizType)
                && !FinanceCategory.otherCategoryCode(transType).equals(category.getCategoryCode()))
        {
            throw new ServiceException("业务类型「其他」的" + transType + "只能使用收支分类「"
                    + FinanceCategory.reservedOtherName(FinanceCategory.otherCategoryCode(transType)) + "」");
        }
        if (!category.getType().equals(transType))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」是"
                    + category.getType() + "分类，与本次的" + transType + "对不上");
        }
        transaction.setCategoryCode(category.getCategoryCode());
        // 分类名称存快照，分类以后改名了历史流水仍显示当时的名称
        transaction.setCategoryName(category.getCategoryName());

        if (transaction.getAmount() == null || transaction.getAmount().signum() == 0)
        {
            throw new ServiceException("金额不能为空且不能为 0");
        }
        // 说明书 5.3.2：收入为正、支出为负。前端填的是绝对值，符号在这里统一，
        // 账户余额就可以一律做加法，不用按 transtype 分支。
        BigDecimal absolute = transaction.getAmount().abs().setScale(2, java.math.RoundingMode.HALF_UP);
        transaction.setAmount(FinanceTransaction.TYPE_EXPENSE.equals(transType)
                ? absolute.negate() : absolute);

        FinanceAccount account = null;
        if (StringUtils.isNotEmpty(transaction.getAccountCode()))
        {
            account = accountMapper.selectAccountByCode(
                    transaction.getShopCode(), transaction.getAccountCode().trim());
            if (account == null)
            {
                throw new ServiceException("账户不存在：" + transaction.getAccountCode());
            }
            transaction.setAccountCode(account.getAccountCode());
            transaction.setAccountName(account.getAccountName());
        }
        else
        {
            transaction.setAccountCode(null);
            transaction.setAccountName(null);
        }

        // 钱要打出去的流水（采购付款、销售退款）由财务在「执行打款」那一步选账户，建单时可以不填；
        // 其余流水在录入时就知道钱进/出哪个账户，必须当场填，否则完成时无处入账
        if (!needsExecuteStep(bizType, transType) && StringUtils.isEmpty(transaction.getAccountCode()))
        {
            throw new ServiceException("请选择账户");
        }

        // 用户手填的文本先过一遍长度校验，超长给业务文案，不要让 JDBC 抛
        // 「Data too long for column 'partyname'」这种用户看不懂的报错。
        // 上限对应 u_f_transaction_yyyyMM 各列的 varchar 宽度。
        // 放在绑定单据之前：绑定会用系统生成的对方名称与业务摘要覆盖手填值，
        // 那两个值由 buildXxxSummary 自己截断，不该再按手填的口径报错。
        transaction.setPartyName(FinanceText.optional(transaction.getPartyName(), "对方名称", 100));
        transaction.setBizSummary(FinanceText.optional(transaction.getBizSummary(), "业务摘要", 200));
        transaction.setPartyText(FinanceText.optional(transaction.getPartyText(), "对方文本信息", 500));
        transaction.setPartyImage(FinanceText.optional(transaction.getPartyImage(), "对方图片信息", 500));
        transaction.setBizCode(FinanceText.optional(transaction.getBizCode(), "业务单据号", 50));
        transaction.setAttachment(FinanceText.optional(transaction.getAttachment(), "凭证附件路径", 1000));
        transaction.setRemark(FinanceText.optional(transaction.getRemark(), "备注", 500));

        // 币种由服务端派生，先把前端传的值抹掉：绑了订单就跟订单，否则跟账户，
        // 用户不能单独指定。applyBusinessBinding 绑定销售订单时会写入订单币种。
        transaction.setCurrency(null);
        applyBusinessBinding(transaction);
        applyCurrency(transaction, account);

        if (StringUtils.isEmpty(transaction.getPartyName()))
        {
            throw new ServiceException("对方名称不能为空");
        }
        if (StringUtils.isEmpty(transaction.getBizSummary()))
        {
            throw new ServiceException("业务摘要不能为空");
        }
    }

    /**
     * 提交草稿时按现行规则复核业务类型与分类。
     *
     * <p>这里只读校验，不调用 {@link #validateContent(FinanceTransaction)}：完整校验会重新整理
     * 金额符号、账户和业务单据绑定，提交动作不应悄悄改写已经保存的流水内容。</p>
     */
    private void validateCurrentRules(FinanceTransaction transaction)
    {
        String transType = StringUtils.trimToNull(transaction.getTransType());
        if (!FinanceTransaction.TYPE_INCOME.equals(transType)
                && !FinanceTransaction.TYPE_EXPENSE.equals(transType))
        {
            throw new ServiceException("收支方向只能是「收入」或「支出」");
        }

        String bizType = StringUtils.trimToNull(transaction.getBizType());
        if (bizType == null || !BIZ_TYPES.contains(bizType))
        {
            throw new ServiceException("业务类型只能是：" + String.join("、", BIZ_TYPES));
        }

        FinanceCategory category = categoryMapper.selectCategoryByCode(
                transaction.getShopCode(), StringUtils.trimToEmpty(transaction.getCategoryCode()));
        if (category == null)
        {
            throw new ServiceException("收支分类不存在，请先在收支分类里配置");
        }
        if (!Integer.valueOf(1).equals(category.getIsActive()))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」已停用");
        }
        if (!bizType.equals(category.getBizType()))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」不属于业务类型「"
                    + bizType + "」");
        }
        if (FinanceTransaction.BIZ_OTHER.equals(bizType)
                && !FinanceCategory.otherCategoryCode(transType).equals(category.getCategoryCode()))
        {
            throw new ServiceException("业务类型「其他」的" + transType + "只能使用收支分类「"
                    + FinanceCategory.reservedOtherName(FinanceCategory.otherCategoryCode(transType)) + "」");
        }
        if (!transType.equals(category.getType()))
        {
            throw new ServiceException("收支分类「" + category.getCategoryName() + "」是"
                    + category.getType() + "分类，与本次的" + transType + "对不上");
        }
    }

    /**
     * 绑定业务单据。说明书 5.4 流程1：绑定采购单号后，系统自动生成业务摘要、
     * 自动填充供应商信息，不让用户手填，避免和采购单对不上。
     */
    private void applyBusinessBinding(FinanceTransaction transaction)
    {
        String bizCode = StringUtils.trimToNull(transaction.getBizCode());
        transaction.setBizCode(bizCode);

        boolean refund = isRefund(transaction.getBizType(), transaction.getTransType());

        if (FinanceTransaction.BIZ_PURCHASE.equals(transaction.getBizType()))
        {
            if (bizCode == null)
            {
                throw new ServiceException(refund ? "采购退款必须绑定采购单号" : "采购付款必须绑定采购单号");
            }
            PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(
                    transaction.getShopCode(), bizCode);
            if (order == null)
            {
                throw new ServiceException("采购单不存在：" + bizCode);
            }
            if (refund)
            {
                requireWithinRefundWindow(transaction, FinanceTransaction.BIZ_PURCHASE, bizCode, "采购单");
                requireWithinPaid(transaction, order);
            }
            else
            {
                requireWithinPayable(transaction, order);
            }
            transaction.setPartyName(order.getPartnerName());
            transaction.setBizSummary(buildPurchaseSummary(order, refund));
        }
        else if (FinanceTransaction.BIZ_SALE.equals(transaction.getBizType()))
        {
            // 退款是财务主动发起的，退的是哪张订单建单时就知道，必须当场绑。
            // 回款相反：说明书 5.4 流程2 的常态是「财务先录流水、业务员后认领绑定」，
            // 建单时 bizCode 多半为空，所以只在真填了单号时校验，认领那一步才是必校验的。
            if (refund && bizCode == null)
            {
                throw new ServiceException("销售退款必须绑定订单号，退的是哪张单不能事后再认领");
            }
            if (bizCode != null)
            {
                SalesOrder order = requireSalesOrder(transaction.getShopCode(), bizCode, refund);
                if (refund)
                {
                    requireWithinRefundWindow(transaction, FinanceTransaction.BIZ_SALE,
                            order.getOrderCode(), "订单");
                    requireWithinReceived(transaction, order);
                }
                transaction.setBizCode(order.getOrderCode());
                transaction.setBizSummary(buildSaleSummary(order, transaction.getPartyName(), refund));
                // 币种跟订单走。账户是否同币种由 applyCurrency 统一卡，这里只做快照。
                transaction.setCurrency(CurrencySupport.require(order.getCurrency()));
            }
        }
    }

    /**
     * 定下这笔流水的币种，并卡住「账户币种必须等于订单币种」。
     *
     * <p>优先级：绑定的销售订单 &gt; 账户 &gt; CNY。三种情况：</p>
     * <ul>
     *   <li>绑了销售订单又选了账户——两边必须同币种，不一致直接拒，本系统不做汇率换算；</li>
     *   <li>只选了账户（其他支出、还没认领的回款）——币种跟账户，用户改不了；</li>
     *   <li>两个都没有（采购付款建单时账户留空）——先记 CNY 占位，
     *       {@link #executeTransaction} 选定付款账户时按账户改写。</li>
     * </ul>
     *
     * @param account 已在 {@link #validateContent} 里查过的账户，没选账户时为 null
     */
    private void applyCurrency(FinanceTransaction transaction, FinanceAccount account)
    {
        String orderCurrency = StringUtils.trimToNull(transaction.getCurrency());
        if (account == null)
        {
            transaction.setCurrency(CurrencySupport.require(orderCurrency));
            return;
        }
        String accountCurrency = CurrencySupport.require(account.getCurrency());
        if (orderCurrency != null)
        {
            CurrencySupport.requireSame(orderCurrency, accountCurrency,
                    "订单「" + transaction.getBizCode() + "」",
                    "账户「" + account.getAccountName() + "」");
        }
        requirePurchaseInBaseCurrency(transaction.getBizType(), transaction.getBizCode(),
                accountCurrency, account.getAccountName());
        transaction.setCurrency(accountCurrency);
    }

    /**
     * 采购的收付款只能走本位币账户。
     *
     * <p>采购单没有币种列——{@code u_p_purchase} 的 totalamount / paidamount 都是本位币金额，
     * 采购业务本身也只做人民币。所以绑了采购单的流水，账户币种必须是 {@link CurrencySupport#DEFAULT}。</p>
     *
     * <p>不挡的话：用美元账户付一张采购单，{@code increasePaidAmount} 会把美元数额直接加进
     * 已付货款，再拿它和人民币的订单总额比出 paystatus——账户扣的是美元、采购单记的是人民币，
     * 一笔钱两个口径，事后对账查不出差在哪。{@link #requireWithinPayable} 那道
     * 「付款不能超过待付货款」的校验同样会失效，因为它比的是两种货币的数字。</p>
     *
     * <p>销售侧不需要这道校验：销售订单自己有 currency 列，两边按订单币种对齐（见上面
     * {@link #applyCurrency} 和 {@link #complete}）。将来采购真要支持外币，
     * 正确做法是给 {@code u_p_purchase} 加币种列、按订单币种对齐，而不是把这里放开。</p>
     *
     * @param bizType 业务类型，只有采购才校验
     * @param bizCode 采购单号，用于报错文案
     * @param accountCurrency 已归一化的账户币种
     * @param accountName 账户名称，用于报错文案
     */
    private void requirePurchaseInBaseCurrency(String bizType, String bizCode,
            String accountCurrency, String accountName)
    {
        if (!FinanceTransaction.BIZ_PURCHASE.equals(bizType))
        {
            return;
        }
        CurrencySupport.requireSame(CurrencySupport.DEFAULT, accountCurrency,
                "采购单「" + StringUtils.trimToEmpty(bizCode) + "」（采购只走本位币）",
                "账户「" + accountName + "」");
    }

    /**
     * 取销售订单并校验可绑定。跨月查找——订单与流水不在同一张月表。
     *
     * <p>已取消的订单不能再收款，但<b>可以退款</b>——取消订单后把客户已付的钱退回去
     * 正是退款最常见的场景，拦掉的话这功能等于白做。</p>
     *
     * <p>草稿也不能收款：单子还没成立，钱挂上去之后单子改了金额、甚至删了，这笔钱就悬空了。
     * 与「可回款订单」下拉排除草稿的规则对齐。</p>
     *
     * @param refund true 表示这笔是退款（钱退给客户）
     * @throws ServiceException 订单不存在，或收款绑到了草稿、已取消的订单上
     */
    private SalesOrder requireSalesOrder(String shopCode, String bizCode, boolean refund)
    {
        SalesOrder order = salesOrderService.findOrderAcrossMonths(shopCode, bizCode);
        if (order == null)
        {
            throw new ServiceException("销售订单不存在：" + bizCode);
        }
        if (!refund && SalesOrder.STATE_CANCELLED.equals(order.getOrderState()))
        {
            throw new ServiceException("订单「" + order.getOrderCode() + "」已取消，不能绑定回款");
        }
        if (!refund && SalesOrder.STATE_DRAFT.equals(order.getOrderState()))
        {
            throw new ServiceException("订单「" + order.getOrderCode() + "」还是草稿，先提交审核再绑定回款");
        }
        return order;
    }

    /** @param refund true 表示供应商把钱退回来了，不是我方付款 */
    private String buildPurchaseSummary(PurchaseOrder order, boolean refund)
    {
        StringBuilder summary = new StringBuilder();
        summary.append(refund ? "采购单 " : "支付采购单 ")
                .append(order.getPurchaseCode())
                .append(refund ? " 退款" : " 货款");
        if (StringUtils.isNotEmpty(order.getPartnerName()))
        {
            summary.append("（供应商：").append(order.getPartnerName()).append("）");
        }
        if (order.getTotalAmount() != null)
        {
            summary.append("，订单金额 ").append(order.getTotalAmount().toPlainString());
        }
        return trimSummary(summary.toString());
    }

    /**
     * 回款摘要。客户名优先取流水上财务录的对方名称（银行回单上的付款方），
     * 没有才退到订单收件人——两者可能不是同一个人（公司账户付款、收件人是仓管）。
     */
    private String buildSaleSummary(SalesOrder order, String partyName, boolean refund)
    {
        StringBuilder summary = new StringBuilder();
        summary.append("订单 ").append(order.getOrderCode()).append(refund ? " 退款" : " 回款");
        String customer = StringUtils.isNotEmpty(partyName) ? partyName : order.getReceivePerson();
        if (StringUtils.isNotEmpty(customer))
        {
            summary.append("（客户：").append(customer).append("）");
        }
        if (order.getTotalAmount() != null)
        {
            summary.append("，订单金额 ").append(order.getTotalAmount().toPlainString());
        }
        if (order.getPaidAmount() != null && order.getPaidAmount().signum() > 0)
        {
            summary.append("，已收 ").append(order.getPaidAmount().toPlainString());
        }
        return trimSummary(summary.toString());
    }

    /** bizsummary 是 varchar(200)，超长直接截断，不让入库报错 */
    private String trimSummary(String summary)
    {
        return summary.length() <= 200 ? summary : summary.substring(0, 200);
    }

    /** 只带主键和商家的空壳，用于状态流转语句：没显式设置的字段在 SQL 里用 coalesce 保留原值 */
    private FinanceTransaction flowCommand(String shopCode, String transactionCode)
    {
        FinanceTransaction command = new FinanceTransaction();
        command.setShopCode(shopCode);
        command.setTransactionCode(transactionCode);
        return command;
    }

    private FinanceTransaction lock(String shopCode, String transactionCode, String suffix)
    {
        if (StringUtils.isEmpty(transactionCode))
        {
            throw new ServiceException("流水号不能为空");
        }
        FinanceTransaction existing = transactionMapper.selectTransactionByCodeForUpdate(
                shopCode, transactionCode.trim(), suffix);
        if (existing == null)
        {
            throw new ServiceException("资金流水不存在");
        }
        return existing;
    }

    /** 状态流转语句带了 expectedStatus，影响行数为 0 说明有人抢先改了状态 */
    private int requireAffected(int affected)
    {
        if (affected == 0)
        {
            throw new ServiceException("流水状态已被其他人变更，请刷新后重试");
        }
        return affected;
    }

    private String requireSuffix(String tableSuffix)
    {
        String suffix = StringUtils.trimToEmpty(tableSuffix);
        if (!FinanceTableSuffixes.isValid(suffix))
        {
            throw new ServiceException("月份参数不正确，应为 yyyyMM");
        }
        return suffix;
    }
}
