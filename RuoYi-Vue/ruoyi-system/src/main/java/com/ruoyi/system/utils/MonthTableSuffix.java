package com.ruoyi.system.utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;

/**
 * 月表后缀工具。
 *
 * <p>月表名是拼进 SQL 的（MyBatis 的 {@code ${}}），所以任何来自请求或
 * information_schema 的后缀都必须先过这里，不能直接拼。</p>
 */
public final class MonthTableSuffix
{
    private static final String PATTERN = "yyyyMM";

    private static final DateTimeFormatter SUFFIX_FMT = DateTimeFormatter.ofPattern(PATTERN);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private MonthTableSuffix()
    {
    }

    /** 按业务日期算月表后缀。 */
    public static String of(Date date)
    {
        return new SimpleDateFormat(PATTERN).format(date == null ? new Date() : date);
    }

    /** 当前月的后缀。 */
    public static String current()
    {
        return of(new Date());
    }

    /**
     * 校验后缀合法，非法直接抛。写操作定位月表前必须调一次。
     *
     * @return 原样返回后缀，方便链式使用
     */
    public static String require(String suffix)
    {
        if (!isValid(suffix))
        {
            throw new ServiceException("月表后缀必须是yyyyMM");
        }
        return suffix;
    }

    public static boolean isValid(String suffix)
    {
        if (suffix == null || !suffix.matches("\\d{6}"))
        {
            return false;
        }
        int month = Integer.parseInt(suffix.substring(4));
        return month >= 1 && month <= 12;
    }

    /**
     * 过滤出合法后缀并按月份倒序排列。
     *
     * <p>倒序是为了让跨月查找先撞上最近的月份：订单和发货批次绝大多数落在近几个月，
     * 从最新往回找能少扫很多张表。</p>
     */
    public static List<String> validDescending(List<String> suffixes)
    {
        List<String> valid = new ArrayList<>();
        if (suffixes == null)
        {
            return valid;
        }
        for (String suffix : suffixes)
        {
            if (isValid(suffix))
            {
                valid.add(suffix);
            }
        }
        Collections.sort(valid, Collections.reverseOrder());
        return valid;
    }

    /**
     * 闭区间 [begin, end] 覆盖到的所有月份后缀，倒序。
     *
     * <p>begin 为空时从 end 往前推一年，end 为空时取当月。两头都不给边界的查询不该走
     * 这里——调用方应当直接用现存的全部月表，否则这里会凭空造出一堆并不存在的表名。</p>
     */
    public static List<String> between(Date begin, Date end)
    {
        LocalDate endDate = end == null
                ? LocalDate.now(BUSINESS_ZONE)
                : end.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate beginDate = begin == null
                ? endDate.minusYears(1)
                : begin.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        List<String> result = new ArrayList<>();
        if (beginDate.isAfter(endDate))
        {
            return result;
        }
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

    /**
     * 本次查询要扫哪几张月表：日期区间覆盖到的月份 ∩ 数据库里实际存在的月表。
     *
     * <p>取交集是必须的：区间是用户填的，可以指向任何月份，而 {@code ${}} 拼出来的表名
     * 一旦不存在，整条 union 就直接报 1146。两端都没给区间时返回全部现存月表。</p>
     *
     * @param existing 从 information_schema 查出来的后缀，未必合法，会先过滤
     */
    public static List<String> queryRange(List<String> existing, Date begin, Date end)
    {
        List<String> months = validDescending(existing);
        if (begin == null && end == null)
        {
            return months;
        }
        Set<String> wanted = new LinkedHashSet<>(between(begin, end));
        List<String> result = new ArrayList<>();
        for (String suffix : months)
        {
            if (wanted.contains(suffix))
            {
                result.add(suffix);
            }
        }
        return result;
    }
}
