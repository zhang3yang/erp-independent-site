package com.ruoyi.system.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoodsSummaryBuilderTest
{
    /** 测试用的一行明细，只关心商品编码和名称两列。 */
    private static final class Line
    {
        private final String code;
        private final String name;

        private Line(String code, String name)
        {
            this.code = code;
            this.name = name;
        }
    }

    private static String build(Line... lines)
    {
        List<Line> items = lines == null ? null : Arrays.asList(lines);
        return GoodsSummaryBuilder.build(items, l -> l.code, l -> l.name);
    }

    @Test
    void singleGoodsShowsNameAlone()
    {
        assertEquals("可乐", build(new Line("G001", "可乐")));
    }

    @Test
    void multipleGoodsShowFirstNamePlusKindCount()
    {
        assertEquals("可乐等3种", build(
                new Line("G001", "可乐"),
                new Line("G002", "雪碧"),
                new Line("G003", "芬达")));
    }

    /** 同一个商品拆成几行（不同仓库、不同批次）算一种，不算几种。 */
    @Test
    void sameGoodsOnSeveralLinesCountsOnce()
    {
        assertEquals("可乐", build(
                new Line("G001", "可乐"),
                new Line("G001", "可乐")));
        assertEquals("可乐等2种", build(
                new Line("G001", "可乐"),
                new Line("G001", "可乐"),
                new Line("G002", "雪碧")));
    }

    /** 露出来的是首行的商品名，不是编码最小的那个。 */
    @Test
    void headNameComesFromTheFirstLine()
    {
        assertEquals("雪碧等2种", build(
                new Line("G002", "雪碧"),
                new Line("G001", "可乐")));
    }

    @Test
    void noDetailsGivesNull()
    {
        assertNull(build());
        assertNull(GoodsSummaryBuilder.build(null, l -> null, l -> null));
        assertNull(GoodsSummaryBuilder.build(Collections.singletonList((Line) null), l -> l.code, l -> l.name));
    }

    /** 商品名是冗余列，历史数据里可能为空；退回编码总比显示一个孤零零的「等3种」强。 */
    @Test
    void blankNameFallsBackToCode()
    {
        assertEquals("G001", build(new Line("G001", "   ")));
        assertEquals("G001等2种", build(
                new Line("G001", null),
                new Line("G002", "雪碧")));
    }

    /** 编码为空的行只能按名字彼此区分，不能全部并成一种。 */
    @Test
    void missingCodeFallsBackToNameAsDedupeKey()
    {
        assertEquals("可乐等2种", build(
                new Line(null, "可乐"),
                new Line("", "雪碧")));
    }

    @Test
    void overlongNameIsTruncatedToFitTheColumn()
    {
        String longName = repeat("长", 200);
        String summary = build(new Line("G001", longName), new Line("G002", "雪碧"));
        assertTrue(summary.length() <= GoodsSummaryBuilder.MAX_LENGTH, summary.length() + " 超过列宽");
        assertTrue(summary.endsWith("等2种"), summary);
    }

    private static String repeat(String unit, int times)
    {
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++)
        {
            sb.append(unit);
        }
        return sb.toString();
    }
}
