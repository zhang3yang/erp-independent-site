package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
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

/**
 * 资金流水的状态机、金额符号与账户入账测试。
 *
 * <p>对照说明书 5.4 的三条流程与 5.3.2 的字段口径。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinanceTransactionServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String CODE = "ZJ20260814001";
    private static final String SUFFIX = "202608";
    private static final String USER = "tester";
    private static final String PURCHASE = "CG20260814001";

    @Mock private FinanceTransactionMapper transactionMapper;
    @Mock private FinanceAccountMapper accountMapper;
    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    // 销售回款的绑定校验与回写走订单服务，不直接碰订单表——订单按月分表，
    // 且已收金额与支付/回款状态必须一起派生。
    @Mock private ISalesOrderService salesOrderService;
    // 退款时效窗口，建单时校验退款有没有过期
    @Mock private RefundWindowResolver refundWindowResolver;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;

    @InjectMocks private FinanceTransactionServiceImpl service;

    @BeforeEach
    void setUp()
    {
        lenient().when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList(SUFFIX));
    }

    // ------------------------------------------------------------------
    // 金额符号与分类校验（说明书 5.3.2：收入为正、支出为负）
    // ------------------------------------------------------------------

    @Test
    void create_支出金额入库转为负数()
    {
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        stubAccount("ZH001", new BigDecimal("1000.00"));
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);

        service.createTransaction(expenseDraft(new BigDecimal("300")), USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                new BigDecimal("-300.00").compareTo(t.getAmount()) == 0
                        && FinanceTransaction.STATUS_DRAFT.equals(t.getStatus())
                        && CODE.equals(t.getTransactionCode())), anyString());
    }

    @Test
    void create_其他业务收入金额入库转为正数()
    {
        stubCategory("QT_SR", "其他收入", FinanceTransaction.TYPE_INCOME);
        stubAccount("ZH001", new BigDecimal("1000.00"));
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);

        FinanceTransaction draft = expenseDraft(new BigDecimal("300"));
        draft.setTransType(FinanceTransaction.TYPE_INCOME);
        draft.setCategoryCode("QT_SR");

        service.createTransaction(draft, USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                new BigDecimal("300.00").compareTo(t.getAmount()) == 0
                        && FinanceTransaction.TYPE_INCOME.equals(t.getTransType())
                        && "其他收入".equals(t.getCategoryName())), anyString());
    }

    @Test
    void create_其他业务用错方向的系统分类时拒绝()
    {
        // 其他收入只能挂 QT_SR，挂支出那条要挡住
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        FinanceTransaction draft = expenseDraft(new BigDecimal("300"));
        draft.setTransType(FinanceTransaction.TYPE_INCOME);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(draft, USER));

        assertTrue(error.getMessage().contains("其他收入"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_其他业务不能使用自建分类()
    {
        stubCategory("QT_CL", "差旅", FinanceTransaction.TYPE_EXPENSE);
        FinanceTransaction draft = expenseDraft(new BigDecimal("300"));
        draft.setCategoryCode("QT_CL");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(draft, USER));

        assertTrue(error.getMessage().contains("其他支出"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_分类方向与流水方向不一致时拒绝()
    {
        // 草稿是支出，挂的却是一条收入分类
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_INCOME);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(expenseDraft(new BigDecimal("100")), USER));

        assertTrue(error.getMessage().contains("对不上"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_金额为零时拒绝()
    {
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(expenseDraft(BigDecimal.ZERO), USER));

        assertTrue(error.getMessage().contains("金额"));
    }

    @Test
    void create_已删除的费用和工资业务类型被拒绝()
    {
        FinanceTransaction fee = expenseDraft(new BigDecimal("100"));
        fee.setBizType("费用");
        assertThrows(ServiceException.class, () -> service.createTransaction(fee, USER));

        FinanceTransaction salary = expenseDraft(new BigDecimal("100"));
        salary.setBizType("工资");
        assertThrows(ServiceException.class, () -> service.createTransaction(salary, USER));

        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void update_其他支出草稿可以改成其他收入()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_DRAFT,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("-100.00"));
        stubLock(existing);
        stubCategory("QT_SR", "其他收入", FinanceTransaction.TYPE_INCOME);
        stubAccount("ZH001", new BigDecimal("1000.00"));

        FinanceTransaction update = expenseDraft(new BigDecimal("100"));
        update.setTransactionCode(CODE);
        update.setTableSuffix(SUFFIX);
        update.setTransType(FinanceTransaction.TYPE_INCOME);
        update.setCategoryCode("QT_SR");

        service.updateTransaction(update, USER);

        verify(transactionMapper).updateTransaction(argThat(t ->
                new BigDecimal("100.00").compareTo(t.getAmount()) == 0
                        && FinanceTransaction.TYPE_INCOME.equals(t.getTransType())), anyString());
    }

    @Test
    void create_分类业务类型与流水不一致时拒绝()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        FinanceTransaction draft = expenseDraft(new BigDecimal("100"));
        draft.setCategoryCode("CG_HK");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(draft, USER));

        assertTrue(error.getMessage().contains("不属于业务类型"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_采购付款自动带出供应商与业务摘要()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(purchaseOrder());

        FinanceTransaction draft = expenseDraft(new BigDecimal("500"));
        draft.setCategoryCode("CG_HK");
        draft.setBizType(FinanceTransaction.BIZ_PURCHASE);
        draft.setBizCode(PURCHASE);
        draft.setAccountCode(null);
        // 用户瞎填的对方名称会被采购单上的供应商覆盖
        draft.setPartyName("随手填的名字");
        service.createTransaction(draft, USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                "测试供应商".equals(t.getPartyName())
                        && t.getBizSummary().contains(PURCHASE)
                        && t.getBizSummary().contains("测试供应商")), anyString());
    }

    @Test
    void create_采购付款未绑定采购单时拒绝()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);

        FinanceTransaction draft = expenseDraft(new BigDecimal("500"));
        draft.setCategoryCode("CG_HK");
        draft.setBizType(FinanceTransaction.BIZ_PURCHASE);
        draft.setBizCode(null);
        // 采购付款的账户由财务在执行打款时选，创建时不填
        draft.setAccountCode(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(draft, USER));
        assertTrue(error.getMessage().contains("采购单号"));
    }

    // ------------------------------------------------------------------
    // 采购付款不能超过采购单的待付货款，除非明确确认
    // ------------------------------------------------------------------

    @Test
    void create_采购付款超过待付时拒绝()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        // 总额 1000、已付 900，只剩 100 待付
        stubPurchaseOrder(new BigDecimal("900.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(purchasePayment(new BigDecimal("500")), USER));

        // 提示要说清超了多少、以及怎么放行，不然用户只能干瞪眼
        assertTrue(error.getMessage().contains("待付"), error.getMessage());
        assertTrue(error.getMessage().contains("100"), error.getMessage());
        assertTrue(error.getMessage().contains("允许超额付款"), error.getMessage());
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_刚好把待付付满时放行()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);
        stubPurchaseOrder(new BigDecimal("900.00"));

        // 边界：等于待付不算超额，这是「把尾款付清」的常规操作
        service.createTransaction(purchasePayment(new BigDecimal("100")), USER);

        verify(transactionMapper).insertTransaction(any(), anyString());
    }

    @Test
    void create_采购付款不允许超额()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        stubPurchaseOrder(new BigDecimal("900.00"));

        // 采购单总额 900，已付 800，待付 100，付款 500 超过待付
        assertThrows(ServiceException.class, () -> {
            service.createTransaction(purchasePayment(new BigDecimal("500")), USER);
        });
    }

    @Test
    void create_采购单总额还没算出来时不判超额()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);
        PurchaseOrder order = purchaseOrder();
        // 明细还没填，总额为 0：无从判断超没超，不该把人挡在外面
        order.setTotalAmount(BigDecimal.ZERO);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(order);

        service.createTransaction(purchasePayment(new BigDecimal("500")), USER);

        verify(transactionMapper).insertTransaction(any(), anyString());
    }

    @Test
    void create_采购退款不受待付限制()
    {
        // 退款是供应商把钱退回来，跟「这张单还差多少没付」没有关系。
        // 付满的单退款是最常见的场景，拿待付去卡它等于把退款功能废掉。
        stubCategory("CG_TK", "采购退款", FinanceTransaction.TYPE_INCOME);
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);
        stubAccount("ZH001", new BigDecimal("0.00"));
        stubPurchaseOrder(new BigDecimal("1000.00"));
        // 退款还有另一道独立的关：时效窗口。这里把它放开，单独验「待付」这条不生效
        Date settled = new Date();
        when(refundWindowResolver.lastSettledTime(SHOP, FinanceTransaction.BIZ_PURCHASE, PURCHASE))
                .thenReturn(settled);
        when(refundWindowResolver.withinWindow(settled)).thenReturn(true);

        FinanceTransaction draft = purchasePayment(new BigDecimal("1000"));
        draft.setTransType(FinanceTransaction.TYPE_INCOME);
        draft.setCategoryCode("CG_TK");
        // 钱是退回来的，收款账户建单时就知道，必须当场填
        draft.setAccountCode("ZH001");
        service.createTransaction(draft, USER);

        verify(transactionMapper).insertTransaction(any(), anyString());
    }

    /** 总额 1000、已付 paid 的采购单 */
    private void stubPurchaseOrder(BigDecimal paid)
    {
        PurchaseOrder order = purchaseOrder();
        order.setPaidAmount(paid);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(order);
    }

    /** 一张待保存的采购付款草稿。账户由财务在执行打款那一步选，建单时不填。 */
    private FinanceTransaction purchasePayment(BigDecimal amount)
    {
        FinanceTransaction draft = expenseDraft(amount);
        draft.setCategoryCode("CG_HK");
        draft.setBizType(FinanceTransaction.BIZ_PURCHASE);
        draft.setBizCode(PURCHASE);
        draft.setAccountCode(null);
        return draft;
    }

    // ------------------------------------------------------------------
    // 流程1：采购付款
    // ------------------------------------------------------------------

    @Test
    void audit_采购付款审核通过进入已审核而不是已完成()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-500.00"));
        stubLock(existing);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, true, null, USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_AUDITED.equals(t.getStatus())
                        && USER.equals(t.getAuditUser())
                        && t.getAuditTime() != null),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
        // 审核只是通过，钱还没打出去，账户余额不能动
        verify(accountMapper, never()).increaseBalance(anyString(), anyString(), any());
    }

    @Test
    void execute_打款完成后扣减余额并回写采购单()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-500.00"));
        existing.setBizCode(PURCHASE);
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("500.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(purchaseOrder());

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), "/profile/a.png", USER);

        verify(accountMapper).increaseBalance(SHOP, "ZH001", new BigDecimal("-500.00"));
        // 回写采购单传的是正数
        verify(purchaseOrderMapper).increasePaidAmount(SHOP, PURCHASE, new BigDecimal("500.00"));
        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_COMPLETED.equals(t.getStatus())
                        && t.getCompleteTime() != null),
                eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
    }

    /**
     * 回写金额必须是本次付款额本身，不能带符号也不能翻倍。
     *
     * <p>SQL 层踩过一次：paystatus 的 CASE 里又加了一遍 #{amount}，而 MySQL 的 UPDATE
     * 赋值从左到右生效、此时 paidamount 已是新值，导致付满的单子被判成超额支付。
     * 这里锁住服务层传参，SQL 侧的算法由 PurchaseOrderMapper.xml 的注释与人工验证保证。</p>
     */
    @Test
    void execute_回写采购单的金额是本次付款额()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-123.45"));
        existing.setBizCode(PURCHASE);
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("876.55"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(purchaseOrder());

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), null, USER);

        verify(purchaseOrderMapper).increasePaidAmount(eq(SHOP), eq(PURCHASE),
                argThat(amount -> new BigDecimal("123.45").compareTo(amount) == 0));
    }

    @Test
    void execute_采购单已被删除时不阻断已完成的打款()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-500.00"));
        existing.setBizCode(PURCHASE);
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("500.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(null);

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), null, USER);

        verify(accountMapper).increaseBalance(SHOP, "ZH001", new BigDecimal("-500.00"));
        verify(purchaseOrderMapper, never()).increasePaidAmount(anyString(), anyString(), any());
    }

    @Test
    void execute_未审核的流水不能打款()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_PURCHASE,
                new BigDecimal("-500.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), null, USER));

        assertTrue(error.getMessage().contains("已审核"));
        verify(accountMapper, never()).increaseBalance(anyString(), anyString(), any());
    }

    @Test
    void execute_没填交易时间时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_AUDITED, FinanceTransaction.BIZ_PURCHASE,
                new BigDecimal("-500.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", null, null, USER));

        assertTrue(error.getMessage().contains("交易时间"));
    }

    // ------------------------------------------------------------------
    // 流程2：订单回款
    // ------------------------------------------------------------------

    /** 一张可绑定的销售订单：总额 1000、已收 0，落在 202607 月表（与流水的 202608 不同月）。 */
    private SalesOrder salesOrder(String orderCode, String state, String total, String paid)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(orderCode);
        order.setOrderState(state);
        order.setTotalAmount(new BigDecimal(total));
        order.setPaidAmount(new BigDecimal(paid));
        order.setReceivePerson("张三");
        order.setTableSuffix("202607");
        return order;
    }

    @Test
    void claim_认领绑定订单并记下认领人()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setPartyName("测试客户");
        stubLock(existing);
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260814001"))
                .thenReturn(salesOrder("SO20260814001", SalesOrder.STATE_READY, "1000.00", "0.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.claimTransaction(SHOP, CODE, SUFFIX, "SO20260814001", false, "销售员甲");

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_AUDITED.equals(t.getStatus())
                        && "SO20260814001".equals(t.getBizCode())
                        && "销售员甲".equals(t.getCompleteUser())
                        && t.getBizSummary().contains("SO20260814001")),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
        verify(accountMapper, never()).increaseBalance(anyString(), anyString(), any());
    }

    /**
     * 绑定到不存在的订单必须当场拒绝。
     *
     * <p>放过去的话回写时会静默跳过——钱到账了、订单上却看不到，事后无从发现。
     * 与采购付款绑定采购单的口径一致。</p>
     */
    @Test
    void claim_绑定不存在的订单时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO_NOT_EXIST")).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, "SO_NOT_EXIST", false, "销售员甲"));

        assertTrue(error.getMessage().contains("销售订单不存在"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void claim_绑定已取消的订单时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO_CANCELLED"))
                .thenReturn(salesOrder("SO_CANCELLED", SalesOrder.STATE_CANCELLED, "1000.00", "0.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, "SO_CANCELLED", false, "销售员甲"));

        assertTrue(error.getMessage().contains("已取消"));
    }

    // ------------------------------------------------------------------
    // 超额认领
    // ------------------------------------------------------------------

    /**
     * 认领金额超过订单待收时默认拒绝。
     *
     * <p>放过去的代价：终审后订单变「超额收款」，被 selectReceivableOrders 的已收满规则
     * 永久剔出认领下拉，后面真正属于这张单的回款再也绑不上。</p>
     */
    @Test
    void claim_金额超过订单待收时默认拒绝()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("99999.00"));
        stubLock(existing);
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO001"))
                .thenReturn(salesOrder("SO001", SalesOrder.STATE_READY, "100.00", "0.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, "SO001", false, "销售员甲"));

        assertTrue(error.getMessage().contains("超过订单"));
        assertTrue(error.getMessage().contains("待收 100.00"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /** 客户确实多付了、几张单合并打款：认领人显式确认后放行。 */
    @Test
    void claim_显式允许超额时放行()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("99999.00"));
        stubLock(existing);
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO001"))
                .thenReturn(salesOrder("SO001", SalesOrder.STATE_READY, "100.00", "0.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.claimTransaction(SHOP, CODE, SUFFIX, "SO001", true, "销售员甲");

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_AUDITED.equals(t.getStatus())),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    /** 刚好收满不算超额，边界要能过。 */
    @Test
    void claim_金额刚好等于待收时放行()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("100.00"));
        stubLock(existing);
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO001"))
                .thenReturn(salesOrder("SO001", SalesOrder.STATE_READY, "100.00", "0.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.claimTransaction(SHOP, CODE, SUFFIX, "SO001", false, "销售员甲");

        verify(transactionMapper).updateTransactionStatus(any(),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    /** 摘要要带出订单金额，业务员一眼能对上银行回单的数 */
    @Test
    void claim_摘要带出订单金额与客户()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setPartyName("测试客户");
        stubLock(existing);
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO001"))
                .thenReturn(salesOrder("SO001", SalesOrder.STATE_SHIPPING, "1000.00", "200.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.claimTransaction(SHOP, CODE, SUFFIX, "SO001", false, "销售员甲");

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                t.getBizSummary().contains("SO001")
                        && t.getBizSummary().contains("测试客户")
                        && t.getBizSummary().contains("1000.00")
                        && t.getBizSummary().contains("已收 200.00")),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    @Test
    void audit_回款未认领就终审时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.auditTransaction(SHOP, CODE, SUFFIX, true, null, USER));

        assertTrue(error.getMessage().contains("认领"));
    }

    @Test
    void audit_回款终审通过后入账且认领人不被覆盖()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setCompleteUser("销售员甲");
        existing.setTradeTime(new Date());
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, true, null, "审核员");

        verify(accountMapper).increaseBalance(SHOP, "ZH001", new BigDecimal("800.00"));
        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_COMPLETED.equals(t.getStatus())
                        && t.getCompleteUser() == null
                        && "审核员".equals(t.getAuditUser())),
                eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
        // 方向陷阱：销售回款绝不能走采购那条路。u_p_purchase.paidamount 是「付出去」，
        // u_o_order.paidamount 是「收进来」，同名反向，复用会把钱记反。
        verify(purchaseOrderMapper, never()).increasePaidAmount(anyString(), anyString(), any());
    }

    /**
     * 终审完成时把回款回写到销售订单（说明书 5.4 流程2）。
     *
     * <p><b>必须跨月定位订单</b>：流水在 202608 月表、订单在 202607 月表，
     * 拿流水的月份去查订单会查不到，表现是「回款完成了但订单上已收金额纹丝不动」，
     * 而且不报任何错。这里断言回写用的是订单自己的月表后缀。</p>
     */
    @Test
    void audit_回款终审完成时按订单所在月表回写已收金额()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setBizCode("SO20260714001");
        Date tradeTime = new Date();
        existing.setTradeTime(tradeTime);
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        // 订单落在 202607，流水落在 202608（SUFFIX），故意不同月
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260714001"))
                .thenReturn(salesOrder("SO20260714001", SalesOrder.STATE_SHIPPING, "1000.00", "0.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, true, null, "审核员");

        verify(salesOrderService).applyPayment(SHOP, "SO20260714001", "202607",
                new BigDecimal("800.00"), tradeTime);
        verify(purchaseOrderMapper, never()).increasePaidAmount(anyString(), anyString(), any());
    }

    /** 订单事后被删：不阻断已经到账的钱，静默跳过回写。 */
    @Test
    void audit_回款回写时订单已被删除不阻断入账()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setBizCode("SO_GONE");
        existing.setTradeTime(new Date());
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO_GONE")).thenReturn(null);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, true, null, "审核员");

        // 钱照样入账，只是没地方回写
        verify(accountMapper).increaseBalance(SHOP, "ZH001", new BigDecimal("800.00"));
        verify(salesOrderService, never()).applyPayment(anyString(), anyString(), anyString(), any(), any());
    }

    /** 采购付款不能误走销售回写。两条路径完全独立。 */
    @Test
    void execute_采购付款不碰销售订单()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-500.00"));
        existing.setBizCode("CG20260814001");
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, "CG20260814001"))
                .thenReturn(new PurchaseOrder());
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), null, "财务");

        verify(purchaseOrderMapper).increasePaidAmount(SHOP, "CG20260814001", new BigDecimal("500.00"));
        verify(salesOrderService, never()).applyPayment(anyString(), anyString(), anyString(), any(), any());
    }

    // ------------------------------------------------------------------
    // 流程3：非业务记录
    // ------------------------------------------------------------------

    @Test
    void complete_非业务流水一步完成并入账()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_OTHER,
                new BigDecimal("-120.00"));
        existing.setTradeTime(new Date());
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("880.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.completeTransaction(SHOP, CODE, SUFFIX, USER);

        verify(accountMapper).increaseBalance(SHOP, "ZH001", new BigDecimal("-120.00"));
        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_COMPLETED.equals(t.getStatus())
                        && new BigDecimal("880.00").compareTo(t.getBalance()) == 0
                        && USER.equals(t.getCompleteUser())),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    @Test
    void complete_采购付款不能走完成接口()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_PURCHASE,
                new BigDecimal("-500.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("执行打款"));
    }

    // ------------------------------------------------------------------
    // 驳回、并发与停用账户
    // ------------------------------------------------------------------

    @Test
    void audit_驳回未填原因时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_OTHER, new BigDecimal("-120.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.auditTransaction(SHOP, CODE, SUFFIX, false, "  ", USER));

        assertTrue(error.getMessage().contains("驳回原因"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void audit_驳回时记录原因与审核人()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_OTHER, new BigDecimal("-120.00")));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, false, "  发票没附上  ", USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_REJECTED.equals(t.getStatus())
                        && "发票没附上".equals(t.getRejectReason())),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    @Test
    void submit_状态被别人抢先改掉时报错()
    {
        FinanceTransaction draft = stored(FinanceTransaction.STATUS_DRAFT,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("-120.00"));
        draft.setCategoryCode("QT_ZC");
        stubLock(draft);
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_DRAFT),
                eq(SUFFIX))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("刷新"));
    }

    @Test
    void submit_已删除业务类型的残留草稿按现行规则拒绝()
    {
        FinanceTransaction draft = stored(FinanceTransaction.STATUS_DRAFT,
                "费用", new BigDecimal("-120.00"));
        draft.setCategoryCode("FY_BG");
        stubLock(draft);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("业务类型只能是"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void submit_其他收入草稿可以提交()
    {
        FinanceTransaction draft = stored(FinanceTransaction.STATUS_DRAFT,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("120.00"));
        draft.setCategoryCode("QT_SR");
        stubLock(draft);
        stubCategory("QT_SR", "其他收入", FinanceTransaction.TYPE_INCOME);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_DRAFT),
                eq(SUFFIX))).thenReturn(1);

        service.submitTransaction(SHOP, CODE, SUFFIX, USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_SUBMITTED.equals(t.getStatus())),
                eq(FinanceTransaction.STATUS_DRAFT), eq(SUFFIX));
    }

    @Test
    void submit_其他收入草稿挂着支出分类时拒绝()
    {
        FinanceTransaction draft = stored(FinanceTransaction.STATUS_DRAFT,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("120.00"));
        draft.setCategoryCode("QT_ZC");
        stubLock(draft);
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("其他收入"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void submit_分类已停用时拒绝()
    {
        FinanceTransaction draft = stored(FinanceTransaction.STATUS_DRAFT,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("-120.00"));
        draft.setCategoryCode("QT_ZC");
        stubLock(draft);
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        FinanceCategory disabled = categoryMapper.selectCategoryByCode(SHOP, "QT_ZC");
        disabled.setIsActive(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已停用"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void complete_停用账户不能入账()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_OTHER,
                new BigDecimal("-120.00"));
        existing.setTradeTime(new Date());
        stubLock(existing);
        FinanceAccount disabled = account("ZH001", new BigDecimal("1000.00"));
        disabled.setIsActive(0);
        when(accountMapper.selectAccountByCode(SHOP, "ZH001")).thenReturn(disabled);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("停用"));
        verify(accountMapper, never()).increaseBalance(anyString(), anyString(), any());
    }

    @Test
    void delete_已完成的流水不能删除()
    {
        stubLock(stored(FinanceTransaction.STATUS_COMPLETED, FinanceTransaction.BIZ_OTHER, new BigDecimal("-120.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.deleteTransaction(SHOP, CODE, SUFFIX));

        assertTrue(error.getMessage().contains("草稿"));
    }

    @Test
    void selectList_月份参数非法时拒绝定位月表()
    {
        assertThrows(ServiceException.class,
                () -> service.selectTransactionByCode(SHOP, CODE, "2026-08"));
        assertThrows(ServiceException.class,
                () -> service.selectTransactionByCode(SHOP, CODE, "202613"));
    }

    // ------------------------------------------------------------------
    // 说明书 6.4 对外接口
    // ------------------------------------------------------------------

    @Test
    void sumCompletedAmount_跨月累加已完成金额()
    {
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList("202607", "202608", "坏后缀"));
        when(transactionMapper.sumCompletedAmountByBizCode(SHOP, FinanceTransaction.BIZ_PURCHASE,
                PURCHASE, "202607")).thenReturn(new BigDecimal("300.00"));
        when(transactionMapper.sumCompletedAmountByBizCode(SHOP, FinanceTransaction.BIZ_PURCHASE,
                PURCHASE, "202608")).thenReturn(new BigDecimal("200.00"));

        BigDecimal total = service.sumCompletedAmount(SHOP, FinanceTransaction.BIZ_PURCHASE, PURCHASE);

        assertEquals(0, new BigDecimal("500.00").compareTo(total));
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 多币种：账户一种币，订单一种币，回款的账户必须与订单同币种。本期不做汇率换算。
    // ------------------------------------------------------------------

    /** 没绑单据的支出，币种跟账户走，前端传什么都不算数。 */
    @Test
    void create_其他支出的币种跟随账户()
    {
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        stubAccount("ZH001", new BigDecimal("1000.00"), "USD");
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);

        FinanceTransaction draft = expenseDraft(new BigDecimal("300"));
        draft.setCurrency("EUR");   // 前端硬塞的币种，应被账户币种覆盖
        service.createTransaction(draft, USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                "USD".equals(t.getCurrency())), anyString());
    }

    /** 采购付款建单时不选账户，币种先记 CNY 占位，等执行打款选定账户再改写。 */
    @Test
    void create_采购付款没选账户时币种先记默认值()
    {
        stubCategory("CG_HK", "采购货款", FinanceTransaction.TYPE_EXPENSE);
        stubPurchaseOrder(new BigDecimal("0.00"));
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);

        service.createTransaction(purchasePayment(new BigDecimal("500")), USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                "CNY".equals(t.getCurrency())), anyString());
    }

    /** 建单时就绑了订单的回款，币种取订单的，不取账户的（两者此时必然相同）。 */
    @Test
    void create_绑定订单的回款币种取订单币种()
    {
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        stubAccount("ZH001", new BigDecimal("1000.00"), "USD");
        SalesOrder order = salesOrder("SO20260814001", SalesOrder.STATE_READY, "1000.00", "0.00");
        order.setCurrency("USD");
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260814001")).thenReturn(order);
        when(codeGenerator.nextTransactionCode()).thenReturn(CODE);

        service.createTransaction(saleReceipt(new BigDecimal("300"), "SO20260814001"), USER);

        verify(transactionMapper).insertTransaction(argThat(t ->
                "USD".equals(t.getCurrency())), anyString());
    }

    /** 收 USD 订单的钱却选了人民币账户：钱会以 CNY 记进余额，订单已收却按 USD 累加，直接拒。 */
    @Test
    void create_绑定订单的回款账户币种与订单不一致时拒绝()
    {
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        stubAccount("ZH001", new BigDecimal("1000.00"), "CNY");
        SalesOrder order = salesOrder("SO20260814001", SalesOrder.STATE_READY, "1000.00", "0.00");
        order.setCurrency("USD");
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260814001")).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(saleReceipt(new BigDecimal("300"), "SO20260814001"), USER));

        assertTrue(error.getMessage().contains("不一致"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    @Test
    void create_币种不在白名单时拒绝()
    {
        stubCategory("QT_ZC", "其他支出", FinanceTransaction.TYPE_EXPENSE);
        stubAccount("ZH001", new BigDecimal("1000.00"), "XXX");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTransaction(expenseDraft(new BigDecimal("300")), USER));

        assertTrue(error.getMessage().contains("币种"));
        verify(transactionMapper, never()).insertTransaction(any(), anyString());
    }

    /**
     * 认领是「这笔钱是哪张单的」——钱已经躺在某个币种的账户里了。
     * 认到别的币种的订单上，订单的已收金额会被加上一笔完全不同的钱。
     */
    @Test
    void claim_订单币种与流水币种不一致时拒绝()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("800.00"));
        existing.setCurrency("CNY");
        stubLock(existing);
        SalesOrder order = salesOrder("SO20260814001", SalesOrder.STATE_READY, "1000.00", "0.00");
        order.setCurrency("USD");
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260814001")).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, "SO20260814001", false, "销售员甲"));

        assertTrue(error.getMessage().contains("不一致"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /**
     * 执行打款把币种定死为付款账户的币种：建单时的 CNY 占位要在这一步被改写。
     *
     * <p>用「其他」而不是「采购」来验这条：采购付款现在被
     * {@code requirePurchaseInBaseCurrency} 限定为只能走本位币账户，
     * 拿采购流水配一个 USD 账户会先被那道校验拦下，验不到改写这件事。
     * 采购的那条规则由下面 {@link #execute_采购付款不许走外币账户()} 单独盯。</p>
     */
    @Test
    void execute_币种改写为付款账户的币种()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_OTHER, new BigDecimal("-500.00"));
        existing.setCurrency("CNY");
        existing.setAccountCode(null);
        stubLock(existing);
        stubAccount("ZH002", new BigDecimal("9000.00"), "USD");
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH002", new Date(), null, USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t -> "USD".equals(t.getCurrency())),
                eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
    }

    /**
     * 采购付款只能走本位币账户。
     *
     * <p>{@code u_p_purchase} 没有币种列，totalamount / paidamount 都是本位币金额。
     * 用美元账户付采购单的话，{@code increasePaidAmount} 会把美元数额直接加进已付货款，
     * 再拿它跟人民币的订单总额比出 paystatus——账户扣的是美元、采购单记的是人民币，
     * 事后对账查不出差在哪。</p>
     *
     * <p>账户是在执行打款这一步才选的（建单时可以留空），所以这道校验必须卡在这里，
     * 光靠建单时的 applyCurrency 拦不住。</p>
     */
    @Test
    void execute_采购付款不许走外币账户()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("-500.00"));
        existing.setCurrency("CNY");
        existing.setAccountCode(null);
        stubLock(existing);
        stubAccount("ZH002", new BigDecimal("9000.00"), "USD");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.executeTransaction(SHOP, CODE, SUFFIX, "ZH002", new Date(), null, USER));

        assertTrue(error.getMessage().contains("采购只走本位币"), error.getMessage());
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /**
     * 完成入账是最后一道闸：审批期间账户被改成别的币种，也不能让两种钱加进同一个余额。
     * 已绑订单的销售流水在这里比对的是建单/认领时存下的订单币种快照。
     */
    @Test
    void complete_销售流水的账户币种与订单快照不一致时拒绝()
    {
        // 走执行打款这条路进 complete()，所以取销售退款（钱往外走），金额为负
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("-800.00"));
        existing.setCurrency("USD");
        existing.setBizCode("SO20260814001");
        existing.setTradeTime(new Date());
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1000.00"), "CNY");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", new Date(), null, USER));

        assertTrue(error.getMessage().contains("不一致"));
        verify(accountMapper, never()).increaseBalance(anyString(), anyString(), any());
    }

    /** 一张已绑订单的销售回款草稿 */
    private FinanceTransaction saleReceipt(BigDecimal amount, String orderCode)
    {
        FinanceTransaction draft = new FinanceTransaction();
        draft.setShopCode(SHOP);
        draft.setTransType(FinanceTransaction.TYPE_INCOME);
        draft.setBizType(FinanceTransaction.BIZ_SALE);
        draft.setCategoryCode("XS_HK");
        draft.setBizCode(orderCode);
        draft.setAmount(amount);
        draft.setAccountCode("ZH001");
        draft.setPartyName("测试客户");
        draft.setBizSummary("订单回款");
        return draft;
    }

    private void stubLock(FinanceTransaction existing)
    {
        when(transactionMapper.selectTransactionByCodeForUpdate(SHOP, CODE, SUFFIX))
                .thenReturn(existing);
    }

    private void stubCategory(String code, String name, String type)
    {
        FinanceCategory category = new FinanceCategory();
        category.setShopCode(SHOP);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setType(type);
        category.setBizType(code.startsWith("CG") ? FinanceTransaction.BIZ_PURCHASE
                : code.startsWith("XS") ? FinanceTransaction.BIZ_SALE : FinanceTransaction.BIZ_OTHER);
        category.setIsActive(1);
        when(categoryMapper.selectCategoryByCode(SHOP, code)).thenReturn(category);
    }

    private void stubAccount(String code, BigDecimal balance)
    {
        when(accountMapper.selectAccountByCode(SHOP, code)).thenReturn(account(code, balance));
    }

    /** 指定币种的账户。不传币种的重载建的是人民币账户，与升级脚本把历史账户刷成 CNY 一致。 */
    private void stubAccount(String code, BigDecimal balance, String currency)
    {
        FinanceAccount account = account(code, balance);
        account.setCurrency(currency);
        when(accountMapper.selectAccountByCode(SHOP, code)).thenReturn(account);
    }

    private FinanceAccount account(String code, BigDecimal balance)
    {
        FinanceAccount account = new FinanceAccount();
        account.setShopCode(SHOP);
        account.setAccountCode(code);
        account.setAccountName("建行基本户");
        account.setCurrentBalance(balance);
        account.setCurrency("CNY");
        account.setIsActive(1);
        return account;
    }

    private PurchaseOrder purchaseOrder()
    {
        PurchaseOrder order = new PurchaseOrder();
        order.setShopCode(SHOP);
        order.setPurchaseCode(PURCHASE);
        order.setPartnerName("测试供应商");
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setStatus("approved");
        return order;
    }

    // ------------------------------------------------------------------
    // 退款：方向与业务类型的常态相反，回写要跟着反号
    // ------------------------------------------------------------------

    /**
     * 销售退款回写要用<b>带符号</b>的金额。
     *
     * <p>这里曾经写成 {@code amount.abs()}，退款会被记成又收了一笔钱——账户扣了钱、
     * 订单的已收金额却涨了，一笔错两处，差额是退款额的两倍。</p>
     */
    @Test
    void audit_销售退款回写时已收金额要减少而不是增加()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("-30.00"));
        existing.setBizCode("SO20260714001");
        Date tradeTime = new Date();
        existing.setTradeTime(tradeTime);
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        when(salesOrderService.findOrderAcrossMonths(SHOP, "SO20260714001"))
                .thenReturn(salesOrder("SO20260714001", SalesOrder.STATE_FINISHED, "100.00", "100.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_AUDITED),
                eq(SUFFIX))).thenReturn(1);

        service.executeTransaction(SHOP, CODE, SUFFIX, "ZH001", tradeTime, null, "出纳");

        // -30 原样传下去，订单已收 100 → 70。传 +30 的话就成了「又收一笔」
        verify(salesOrderService).applyPayment(SHOP, "SO20260714001", "202607",
                new BigDecimal("-30.00"), tradeTime);
    }

    /**
     * 采购退款回写要<b>取反</b>：流水符号是「钱对我方的进出」，采购单的 paidamount 是
     * 「付给供应商的钱」，两者方向相反。供应商退回 100（收入 +100）应让已付货款 -100。
     */
    @Test
    void audit_采购退款回写时已付货款要冲减()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_PURCHASE, new BigDecimal("100.00"));
        existing.setBizCode(PURCHASE);
        existing.setTradeTime(new Date());
        stubLock(existing);
        stubAccount("ZH001", new BigDecimal("1800.00"));
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE))
                .thenReturn(purchaseOrder());
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        // 采购退款的钱是进来的，没有「执行打款」这一步，审核通过即完成
        service.auditTransaction(SHOP, CODE, SUFFIX, true, null, "审核员");

        verify(purchaseOrderMapper).increasePaidAmount(SHOP, PURCHASE, new BigDecimal("-100.00"));
    }

    /** 退款在建单时就绑好订单了，不该再走认领。 */
    @Test
    void claim_销售退款不需要认领()
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_SUBMITTED,
                FinanceTransaction.BIZ_SALE, new BigDecimal("-30.00"));
        stubLock(existing);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, "SO20260714001", false, "业务员"));
        assertTrue(error.getMessage().contains("不需要认领"));
    }

    // ------------------------------------------------------------------
    // 查询：扫描范围与过滤条件是两件事
    // ------------------------------------------------------------------

    /**
     * 按交易时间过滤时<b>不能</b>把 createtime 也塞进 where。
     *
     * <p>曾经的实现按 createtime ∈ tradetime ±30 天圈定月表，并把这个范围一起下推进 SQL，
     * 于是「5 月到账的钱 8 月才补录」这种流水按交易时间查 5 月时查不到，且没有任何提示。</p>
     */
    @Test
    void list_按交易时间查询不得限制创建时间()
    {
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList("202605", "202608"));
        FinanceTransaction query = new FinanceTransaction();
        query.setShopCode(SHOP);
        query.setTradeTimeBegin(date(2026, 5, 1));
        query.setTradeTimeEnd(date(2026, 5, 31));

        service.selectTransactionPage(query, 1, 0);

        // createtime 条件必须保持为空，否则 8 月建的那条会被挡掉
        assertNull(query.getCreateTimeBegin());
        assertNull(query.getCreateTimeEnd());
        // 且要扫全部月表——补录的流水可能落在任何一张月表里。
        // 月表一次性交给 SQL union all，不再逐月发查询
        ArgumentCaptor<List<String>> suffixes = ArgumentCaptor.forClass(List.class);
        verify(transactionMapper).selectTransactionPage(eq(query), suffixes.capture(),
                eq(0), eq(0), eq(0));
        // FinanceTableSuffixes.valid 按月份倒序，新月份先扫
        assertEquals(Arrays.asList("202608", "202605"), suffixes.getValue());
    }

    /** 列表页要按页取数：切页和排序都下推到 SQL，不能把全部月份读回来再切。 */
    @Test
    void page_分页参数下推到SQL而不是内存切片()
    {
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList("202607", "202608"));
        FinanceTransaction query = new FinanceTransaction();
        query.setShopCode(SHOP);
        FinanceTransaction row = receipt("ZJ-08", date(2026, 8, 20));
        when(transactionMapper.selectTransactionPage(eq(query), anyList(), eq(20), eq(10), eq(30)))
                .thenReturn(Arrays.asList(row));
        when(transactionMapper.countTransactions(eq(query), anyList())).thenReturn(57L);

        IFinanceTransactionService.TransactionPage page = service.selectTransactionPage(query, 3, 10);

        // 第 3 页每页 10 条 → offset 20、limit 10；分支内截断上界 cap = 20 + 10
        assertEquals(1, page.getRows().size());
        assertEquals(57L, page.getTotal());
    }

    /** 没有任何月表时不该拼出空的 union all，直接返回空页。 */
    @Test
    void page_没有月表时不发查询()
    {
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Collections.<String>emptyList());
        FinanceTransaction query = new FinanceTransaction();
        query.setShopCode(SHOP);

        IFinanceTransactionService.TransactionPage page = service.selectTransactionPage(query, 1, 10);

        assertTrue(page.getRows().isEmpty());
        assertEquals(0L, page.getTotal());
        verify(transactionMapper, never()).selectTransactionPage(any(), anyList(), anyInt(), anyInt(), anyInt());
        verify(transactionMapper, never()).countTransactions(any(), anyList());
    }

    /**
     * 单据流水查询要跨月合并并按交易时间倒序：一张订单的钱可以分几个月陆续到账。
     */
    @Test
    void selectByBusinessDocumentMergesMonthsNewestFirst()
    {
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList("202607", "202608"));
        FinanceTransaction july = receipt("ZJ-07", date(2026, 7, 10));
        FinanceTransaction august = receipt("ZJ-08", date(2026, 8, 20));
        when(transactionMapper.selectByBizCode(SHOP, "销售", "SO001", "202607"))
                .thenReturn(Arrays.asList(july));
        when(transactionMapper.selectByBizCode(SHOP, "销售", "SO001", "202608"))
                .thenReturn(Arrays.asList(august));

        java.util.List<FinanceTransaction> result = service.selectByBusinessDocument(SHOP, "销售", "SO001");

        assertEquals(2, result.size());
        assertEquals("ZJ-08", result.get(0).getTransactionCode());
        assertEquals("ZJ-07", result.get(1).getTransactionCode());
    }

    /** 没有单号就别去扫月表：认领前的回款流水 bizcode 是空的，扫了也只会全表匹配空值。 */
    @Test
    void selectByBusinessDocumentSkipsBlankCode()
    {
        assertTrue(service.selectByBusinessDocument(SHOP, "销售", null).isEmpty());
        verify(transactionMapper, never()).selectByBizCode(anyString(), anyString(), anyString(), anyString());
    }

    private FinanceTransaction receipt(String transactionCode, Date tradeTime)
    {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setShopCode(SHOP);
        transaction.setTransactionCode(transactionCode);
        transaction.setBizType("销售");
        transaction.setBizCode("SO001");
        transaction.setAmount(new BigDecimal("100"));
        transaction.setTradeTime(tradeTime);
        return transaction;
    }

    private Date date(int year, int month, int day)
    {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    private FinanceTransaction expenseDraft(BigDecimal amount)
    {
        FinanceTransaction draft = new FinanceTransaction();
        draft.setShopCode(SHOP);
        draft.setTransType(FinanceTransaction.TYPE_EXPENSE);
        draft.setBizType(FinanceTransaction.BIZ_OTHER);
        draft.setCategoryCode("QT_ZC");
        draft.setAmount(amount);
        draft.setAccountCode("ZH001");
        draft.setPartyName("某某公司");
        draft.setBizSummary("购买办公用品");
        return draft;
    }

    private FinanceTransaction stored(String status, String bizType, BigDecimal amount)
    {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setShopCode(SHOP);
        transaction.setTransactionCode(CODE);
        transaction.setTableSuffix(SUFFIX);
        transaction.setStatus(status);
        transaction.setBizType(bizType);
        transaction.setTransType(amount.signum() < 0
                ? FinanceTransaction.TYPE_EXPENSE : FinanceTransaction.TYPE_INCOME);
        transaction.setAmount(amount);
        transaction.setAccountCode("ZH001");
        return transaction;
    }

    // ------------------------------------------------------------------
    // 流程2 改造：认领搬到销售订单详情（docs/superpowers/specs/2026-09-20-order-side-receipt-claim-design.md）
    // ------------------------------------------------------------------

    private static final String ORDER = "SO20260814001";

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    /**
     * 装一个登录人进安全上下文。不装时 OwnershipScope 与 hasAuditPermission 都按
     * 「没有登录上下文」处理：归属放行、审核权限按无——上面那些老用例就是这么跑的。
     */
    private static void login(String userName, String userCode, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setUserCode(userCode);
        LoginUser loginUser = new LoginUser(user, Set.of(permissions));
        loginUser.setUserCode(userCode);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    /** 一笔客户回款草稿，可选带订单号 */
    private FinanceTransaction receiptDraft(String status, String amount, String bizCode)
    {
        FinanceTransaction draft = stored(status, FinanceTransaction.BIZ_SALE, new BigDecimal(amount));
        draft.setCategoryCode("XS_HK");
        draft.setCurrency("CNY");
        draft.setPartyName("测试客户");
        draft.setBizCode(bizCode);
        return draft;
    }

    /** 建单时就填了订单号的回款，提交即绑定：直接进已审核，认领人记提交的财务，不再让业务员点认领 */
    @Test
    void submit_填了订单号的回款提交后直接进已认领()
    {
        stubLock(receiptDraft(FinanceTransaction.STATUS_DRAFT, "800.00", ORDER));
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER))
                .thenReturn(salesOrder(ORDER, SalesOrder.STATE_READY, "1000.00", "0.00"));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_DRAFT),
                eq(SUFFIX))).thenReturn(1);

        service.submitTransaction(SHOP, CODE, SUFFIX, "财务甲");

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_AUDITED.equals(t.getStatus())
                        && ORDER.equals(t.getBizCode())
                        && "财务甲".equals(t.getCompleteUser())
                        && t.getBizSummary().contains(ORDER)),
                eq(FinanceTransaction.STATUS_DRAFT), eq(SUFFIX));
    }

    /** 没填订单号的回款还是老路：进待审核，等业务员在订单里认领 */
    @Test
    void submit_没填订单号的回款进待审核等业务员认领()
    {
        stubLock(receiptDraft(FinanceTransaction.STATUS_DRAFT, "800.00", null));
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_DRAFT),
                eq(SUFFIX))).thenReturn(1);

        service.submitTransaction(SHOP, CODE, SUFFIX, USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_SUBMITTED.equals(t.getStatus())
                        && t.getBizCode() == null && t.getCompleteUser() == null),
                eq(FinanceTransaction.STATUS_DRAFT), eq(SUFFIX));
        verify(salesOrderService, never()).findOrderAcrossMonths(anyString(), anyString());
    }

    /** 提交这条路没有「放行超额」的入口，超了就拦，并告诉财务两条出路 */
    @Test
    void submit_填了订单号但超过待收时拒绝并提示处理办法()
    {
        stubLock(receiptDraft(FinanceTransaction.STATUS_DRAFT, "1200.00", ORDER));
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER))
                .thenReturn(salesOrder(ORDER, SalesOrder.STATE_READY, "1000.00", "0.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("超过订单"));
        assertTrue(error.getMessage().contains("清空订单号"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /** 草稿订单还没成立，钱挂上去会悬空。建单预填、提交、认领三处走的是同一个 requireSalesOrder */
    @Test
    void submit_填了订单号但订单还是草稿时拒绝()
    {
        stubLock(receiptDraft(FinanceTransaction.STATUS_DRAFT, "800.00", ORDER));
        stubCategory("XS_HK", "销售回款", FinanceTransaction.TYPE_INCOME);
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER))
                .thenReturn(salesOrder(ORDER, SalesOrder.STATE_DRAFT, "1000.00", "0.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("草稿"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void claim_订单还是草稿时拒绝()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER))
                .thenReturn(salesOrder(ORDER, SalesOrder.STATE_DRAFT, "1000.00", "0.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, ORDER, false, "销售员甲"));

        assertTrue(error.getMessage().contains("草稿"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /**
     * 横向权限：订单详情本身只给业务员看自己的单，这条挡的是直接调接口。
     * 报错要说清是归属问题，不能含糊成「订单不存在」。
     */
    @Test
    void claim_业务员不能把钱认到别人的订单上()
    {
        login("lixiao", "us100");
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        SalesOrder order = salesOrder(ORDER, SalesOrder.STATE_READY, "1000.00", "0.00");
        order.setStaffCode("wangwu");
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.claimTransaction(SHOP, CODE, SUFFIX, ORDER, false, "lixiao"));

        assertTrue(error.getMessage().contains("不归属于你"));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void claim_有查看全部订单权限的人可以认领任何人的订单()
    {
        login("boss", "us200", SalesOrderServiceImpl.PERMI_VIEW_ALL);
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        SalesOrder order = salesOrder(ORDER, SalesOrder.STATE_READY, "1000.00", "0.00");
        order.setStaffCode("wangwu");
        when(salesOrderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.claimTransaction(SHOP, CODE, SUFFIX, ORDER, false, "boss");

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_AUDITED.equals(t.getStatus()) && "boss".equals(t.getCompleteUser())),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
    }

    /** 一笔已认领、还没终审的回款 */
    private FinanceTransaction claimedReceipt(String claimer)
    {
        FinanceTransaction existing = stored(FinanceTransaction.STATUS_AUDITED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00"));
        existing.setBizCode(ORDER);
        existing.setBizSummary("订单 " + ORDER + " 回款");
        existing.setCompleteUser(claimer);
        return existing;
    }

    @Test
    void unclaim_认领人本人可以撤回并清掉绑定()
    {
        stubLock(claimedReceipt("销售员甲"));
        when(transactionMapper.clearClaimBinding(any(), eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX)))
                .thenReturn(1);

        service.unclaimTransaction(SHOP, CODE, SUFFIX, "销售员甲");

        verify(transactionMapper).clearClaimBinding(argThat(t ->
                FinanceTransaction.STATUS_SUBMITTED.equals(t.getStatus())
                        && FinanceTransactionServiceImpl.UNCLAIMED_SUMMARY.equals(t.getBizSummary())
                        && t.getRejectReason() == null),
                eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
        // 撤回不走带 coalesce 的那条语句——它清不掉订单号
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    @Test
    void unclaim_别的业务员不能撤回不是自己认的钱()
    {
        login("lixiao", "us100");
        stubLock(claimedReceipt("销售员甲"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unclaimTransaction(SHOP, CODE, SUFFIX, "lixiao"));

        assertTrue(error.getMessage().contains("认领人本人"));
        verify(transactionMapper, never()).clearClaimBinding(any(), anyString(), anyString());
    }

    @Test
    void unclaim_财务审核人可以撤回任何人的认领()
    {
        login("caiwu", "us300", "finance:transaction:audit");
        stubLock(claimedReceipt("销售员甲"));
        when(transactionMapper.clearClaimBinding(any(), eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX)))
                .thenReturn(1);

        service.unclaimTransaction(SHOP, CODE, SUFFIX, "caiwu");

        verify(transactionMapper).clearClaimBinding(any(), eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
    }

    /** 已完成的钱已经进了订单已收金额和账户余额，撤回等于倒账，必须走退款留痕 */
    @Test
    void unclaim_已终审完成的不能撤回()
    {
        FinanceTransaction existing = claimedReceipt("销售员甲");
        existing.setStatus(FinanceTransaction.STATUS_COMPLETED);
        stubLock(existing);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unclaimTransaction(SHOP, CODE, SUFFIX, "销售员甲"));

        assertTrue(error.getMessage().contains("退款"));
        verify(transactionMapper, never()).clearClaimBinding(any(), anyString(), anyString());
    }

    @Test
    void unclaim_还没认领的没有认领可撤()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unclaimTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("只有已认领"));
    }

    @Test
    void unclaim_销售退款没有认领这一步()
    {
        FinanceTransaction refund = stored(FinanceTransaction.STATUS_AUDITED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("-300.00"));
        stubLock(refund);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unclaimTransaction(SHOP, CODE, SUFFIX, USER));

        assertTrue(error.getMessage().contains("只有客户回款"));
    }

    /**
     * 驳回已认领的回款要连绑定一起清掉：提交已改成「填了单号即绑定」，
     * 留着旧单号，财务不改就重提会原样绑回那张认错的单。
     */
    @Test
    void audit_驳回已认领的回款时清掉绑定()
    {
        stubLock(claimedReceipt("销售员甲"));
        when(transactionMapper.clearClaimBinding(any(), eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX)))
                .thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, false, "认错单了", USER);

        verify(transactionMapper).clearClaimBinding(argThat(t ->
                FinanceTransaction.STATUS_REJECTED.equals(t.getStatus())
                        && "认错单了".equals(t.getRejectReason())
                        && FinanceTransactionServiceImpl.UNCLAIMED_SUMMARY.equals(t.getBizSummary())
                        && USER.equals(t.getAuditUser())),
                eq(FinanceTransaction.STATUS_AUDITED), eq(SUFFIX));
        verify(transactionMapper, never()).updateTransactionStatus(any(), anyString(), anyString());
    }

    /** 还没认领的回款本来就没绑定，驳回走原来的语句 */
    @Test
    void audit_驳回还没认领的回款不动绑定()
    {
        stubLock(stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("800.00")));
        when(transactionMapper.updateTransactionStatus(any(), eq(FinanceTransaction.STATUS_SUBMITTED),
                eq(SUFFIX))).thenReturn(1);

        service.auditTransaction(SHOP, CODE, SUFFIX, false, "金额不对", USER);

        verify(transactionMapper).updateTransactionStatus(argThat(t ->
                FinanceTransaction.STATUS_REJECTED.equals(t.getStatus())),
                eq(FinanceTransaction.STATUS_SUBMITTED), eq(SUFFIX));
        verify(transactionMapper, never()).clearClaimBinding(any(), anyString(), anyString());
    }

    /** 待认领列表跨全部月表，按交易时间倒序；币种先归一化再下 SQL */
    @Test
    void unclaimed_跨月汇总并按交易时间倒序()
    {
        when(transactionMapper.selectTransactionTableSuffixes()).thenReturn(Arrays.asList("202607", "202608"));
        FinanceTransaction older = stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("100.00"));
        older.setTransactionCode("ZJ-OLD");
        older.setTradeTime(date(2026, 7, 10));
        FinanceTransaction newer = stored(FinanceTransaction.STATUS_SUBMITTED, FinanceTransaction.BIZ_SALE,
                new BigDecimal("200.00"));
        newer.setTransactionCode("ZJ-NEW");
        newer.setTradeTime(date(2026, 8, 5));
        when(transactionMapper.selectUnclaimedReceipts(SHOP, "USD", "202607"))
                .thenReturn(Collections.singletonList(older));
        when(transactionMapper.selectUnclaimedReceipts(SHOP, "USD", "202608"))
                .thenReturn(Collections.singletonList(newer));

        List<FinanceTransaction> result = service.selectUnclaimedReceipts(SHOP, "usd");

        assertEquals(2, result.size());
        assertEquals("ZJ-NEW", result.get(0).getTransactionCode());
        assertEquals("ZJ-OLD", result.get(1).getTransactionCode());
    }

    @Test
    void unclaimed_不支持的币种直接拒绝()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.selectUnclaimedReceipts(SHOP, "XXX"));

        assertTrue(error.getMessage().contains("币种只能是"));
        verify(transactionMapper, never()).selectUnclaimedReceipts(anyString(), anyString(), anyString());
    }
}
