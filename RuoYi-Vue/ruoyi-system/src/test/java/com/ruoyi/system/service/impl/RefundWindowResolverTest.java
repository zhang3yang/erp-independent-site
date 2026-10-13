package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.mapper.FinanceTransactionMapper;

/**
 * 退款时效窗口：最后一笔正向收付款完成后 3 天。
 */
@ExtendWith(MockitoExtension.class)
class RefundWindowResolverTest
{
    private static final String SHOP = "sp001";

    @Mock private FinanceTransactionMapper transactionMapper;

    @InjectMocks private RefundWindowResolver resolver;

    private Date daysAgo(double days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, (int) Math.round(-days * 24 * 60));
        return calendar.getTime();
    }

    @Test
    void 窗口内的算数刚过三天的不算()
    {
        assertTrue(resolver.withinWindow(daysAgo(0)), "刚付完款");
        assertTrue(resolver.withinWindow(daysAgo(2.9)), "第 3 天还差一点");
        assertFalse(resolver.withinWindow(daysAgo(3.1)), "过了 3 天");
        assertFalse(resolver.withinWindow(daysAgo(30)), "一个月前");
    }

    /** 从没收付过款的单据没有窗口可言，不该出现在退款下拉里。 */
    @Test
    void 没有收付款记录时窗口是关着的()
    {
        assertFalse(resolver.withinWindow(null));
    }

    @Test
    void 截止时刻是最后一笔收付款加三天()
    {
        Date settled = daysAgo(1);
        Calendar expected = Calendar.getInstance();
        expected.setTime(settled);
        expected.add(Calendar.DAY_OF_MONTH, RefundWindowResolver.REFUND_WINDOW_DAYS);
        assertEquals(expected.getTime(), resolver.deadline(settled));
    }

    /**
     * 采购看支出、销售看收入——只有<b>正向</b>收付款才开启窗口。
     *
     * <p>退款流水方向相反，天然被 SQL 的 transtype 条件排除掉；
     * 否则退一笔就能把窗口无限续期。</p>
     */
    @Test
    void 采购查支出销售查收入()
    {
        when(transactionMapper.selectTransactionTableSuffixes()).thenReturn(Arrays.asList("202608"));
        when(transactionMapper.selectLastSettledTimeByBizType(eq(SHOP),
                eq(FinanceTransaction.BIZ_PURCHASE), eq(FinanceTransaction.TYPE_EXPENSE), eq("202608")))
                .thenReturn(new ArrayList<Map<String, Object>>());

        resolver.lastSettledTimes(SHOP, FinanceTransaction.BIZ_PURCHASE);

        when(transactionMapper.selectLastSettledTimeByBizType(eq(SHOP),
                eq(FinanceTransaction.BIZ_SALE), eq(FinanceTransaction.TYPE_INCOME), eq("202608")))
                .thenReturn(new ArrayList<Map<String, Object>>());

        resolver.lastSettledTimes(SHOP, FinanceTransaction.BIZ_SALE);
    }

    /** 一张单的付款可以散在多个月表里，跨月取最大值——最后一笔才决定窗口。 */
    @Test
    void 跨月取最后一笔的时间()
    {
        Date july = daysAgo(40);
        Date august = daysAgo(1);
        when(transactionMapper.selectTransactionTableSuffixes())
                .thenReturn(Arrays.asList("202607", "202608"));
        when(transactionMapper.selectLastSettledTimeByBizType(eq(SHOP), eq(FinanceTransaction.BIZ_SALE),
                eq(FinanceTransaction.TYPE_INCOME), eq("202607")))
                .thenReturn(rows("SO001", july));
        when(transactionMapper.selectLastSettledTimeByBizType(eq(SHOP), eq(FinanceTransaction.BIZ_SALE),
                eq(FinanceTransaction.TYPE_INCOME), eq("202608")))
                .thenReturn(rows("SO001", august));

        Map<String, Date> result = resolver.lastSettledTimes(SHOP, FinanceTransaction.BIZ_SALE);

        assertEquals(august, result.get("SO001"));
        assertTrue(resolver.withinWindow(result.get("SO001")), "按最后一笔算，窗口应该还开着");
    }

    private List<Map<String, Object>> rows(String bizCode, Date lastTime)
    {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("bizcode", bizCode);
        row.put("lasttime", lastTime);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        rows.add(row);
        return rows;
    }
}
