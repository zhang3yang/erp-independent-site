package com.ruoyi.system.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 资金流水月表后缀工具。
 *
 * <p>后缀直接拼进 SQL 表名（MyBatis 的 {@code ${}}），必须先过白名单校验，
 * 只放行 yyyyMM 形式且月份合法的字符串，否则就是一个 SQL 注入口子。</p>
 */
final class FinanceTableSuffixes
{
    private static final DateTimeFormatter SUFFIX_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private FinanceTableSuffixes()
    {
    }

    /** 过滤出形如 yyyyMM 且月份在 1~12 的后缀，顺带按月份倒序排好 */
    static List<String> valid(List<String> suffixes)
    {
        List<String> result = new ArrayList<String>();
        if (suffixes == null)
        {
            return result;
        }
        for (String suffix : suffixes)
        {
            if (isValid(suffix))
            {
                result.add(suffix);
            }
        }
        Collections.sort(result, Collections.reverseOrder());
        return result;
    }

    static boolean isValid(String suffix)
    {
        if (suffix == null || !suffix.matches("\\d{6}"))
        {
            return false;
        }
        int month = Integer.parseInt(suffix.substring(4));
        return month >= 1 && month <= 12;
    }

    /** 当前月份后缀 */
    static String current()
    {
        return YearMonth.now(BUSINESS_ZONE).format(SUFFIX_FMT);
    }

    /** 某个时间点落在哪张月表 */
    static String of(Date time)
    {
        if (time == null)
        {
            return current();
        }
        return YearMonth.from(time.toInstant().atZone(BUSINESS_ZONE).toLocalDate()).format(SUFFIX_FMT);
    }

    /**
     * 闭区间 [begin, end] 覆盖到的所有月份后缀，倒序。
     * begin 为空时从 end 往前推一年，end 为空时取当月，防止无边界扫全表。
     */
    static List<String> between(Date begin, Date end)
    {
        LocalDate endDate = end == null
                ? LocalDate.now(BUSINESS_ZONE)
                : end.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate beginDate = begin == null
                ? endDate.minusYears(1)
                : begin.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        if (beginDate.isAfter(endDate))
        {
            return new ArrayList<String>();
        }

        List<String> result = new ArrayList<String>();
        YearMonth cursor = YearMonth.from(beginDate);
        YearMonth last = YearMonth.from(endDate);
        while (!cursor.isAfter(last))
        {
            result.add(cursor.format(SUFFIX_FMT));
            cursor = cursor.plusMonths(1);
        }
        Collections.sort(result, Collections.reverseOrder());
        return result;
    }
}
