package com.ruoyi.system.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthTableSuffixTest
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static Date at(int year, int month, int day)
    {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    @Test
    void betweenCoversEveryMonthInTheRangeDescending()
    {
        assertEquals(Arrays.asList("202601", "202512", "202511"),
                MonthTableSuffix.between(at(2025, 11, 20), at(2026, 1, 3)));
    }

    @Test
    void betweenKeepsSingleMonthRangeAsOneSuffix()
    {
        assertEquals(Arrays.asList("202608"),
                MonthTableSuffix.between(at(2026, 8, 1), at(2026, 8, 31)));
    }

    @Test
    void betweenReturnsNothingWhenRangeIsInverted()
    {
        assertTrue(MonthTableSuffix.between(at(2026, 8, 10), at(2026, 8, 1)).isEmpty());
    }

    @Test
    void betweenLooksBackOneYearWhenOnlyEndGiven()
    {
        List<String> suffixes = MonthTableSuffix.between(null, at(2026, 3, 15));
        assertEquals(13, suffixes.size());
        assertEquals("202603", suffixes.get(0));
        assertEquals("202503", suffixes.get(12));
    }

    /**
     * 交集是防 1146 的那一道：区间是用户填的，可以指向任何月份，
     * 而后缀是要拼进表名的，表不存在整条 union 就报错。
     */
    @Test
    void queryRangeKeepsOnlyMonthsThatActuallyHaveTables()
    {
        List<String> existing = Arrays.asList("202606", "202607", "202608");
        assertEquals(Arrays.asList("202608", "202607"),
                MonthTableSuffix.queryRange(existing, at(2026, 7, 5), at(2026, 8, 20)));
    }

    @Test
    void queryRangeFallsBackToEveryExistingTableWhenNoRangeGiven()
    {
        List<String> existing = Arrays.asList("202607", "202608", "202606");
        assertEquals(Arrays.asList("202608", "202607", "202606"),
                MonthTableSuffix.queryRange(existing, null, null));
    }

    @Test
    void queryRangeDropsSuffixesThatAreNotValidYearMonths()
    {
        List<String> existing = Arrays.asList("202608", "bak", "20260", "202613");
        assertEquals(Arrays.asList("202608"), MonthTableSuffix.queryRange(existing, null, null));
    }

    @Test
    void queryRangeReturnsNothingWhenRangeMissesEveryExistingTable()
    {
        List<String> existing = Arrays.asList("202607", "202608");
        assertTrue(MonthTableSuffix.queryRange(existing, at(2025, 1, 1), at(2025, 3, 1)).isEmpty());
    }
}
