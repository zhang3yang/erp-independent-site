package com.ruoyi.system.utils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 单据「商品内容」摘要生成器。
 *
 * <p>列表页光看单号看不出这张单装的是什么货，点进详情才知道。这里把明细里的商品名压成一句
 * {@code 可乐等3种}，冗余到主表上，列表查询就不用为每一行再去连明细表。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>按商品编码去重——同一个商品拆成几行（不同仓库、不同批次）算一种，不算几种；</li>
 *   <li>只有一种时直接写商品名，多种时写「首行商品名 + 等 N 种」；</li>
 *   <li>没有明细返回 {@code null}，让列存 NULL 而不是空串，前端统一显示占位符。</li>
 * </ul>
 *
 * <p><b>这是展示用的冗余列，不参与任何计算。</b>它由服务层在建单/改单时整体重算，
 * 不做增量维护——明细本来就是「整体删掉再重建」的，增量反而更容易错。</p>
 */
public final class GoodsSummaryBuilder
{
    /** 列宽 varchar(255)，留足余量截断，避免商品名超长时插入被 MySQL 截掉或报错。 */
    public static final int MAX_LENGTH = 255;

    /** 首个商品名的显示上限。超过这个长度的名字在列表里也看不全，先截断再拼后缀。 */
    private static final int MAX_NAME_LENGTH = 60;

    private GoodsSummaryBuilder()
    {
    }

    /**
     * 按明细生成摘要。
     *
     * @param items       单据明细，按行号顺序传入（首行决定摘要里露出的那个商品名）
     * @param codeGetter  取商品编码，用于去重
     * @param nameGetter  取商品名称，为空时退回商品编码
     * @return {@code 可乐} / {@code 可乐等3种}；没有明细时返回 null
     */
    public static <T> String build(Collection<T> items,
                                   Function<T, String> codeGetter,
                                   Function<T, String> nameGetter)
    {
        if (items == null || items.isEmpty())
        {
            return null;
        }
        Set<String> distinctGoods = new LinkedHashSet<>();
        String firstName = null;
        for (T item : items)
        {
            if (item == null)
            {
                continue;
            }
            String code = trimToNull(codeGetter.apply(item));
            String name = trimToNull(nameGetter.apply(item));
            // 商品名是冗余列，历史数据里可能为空；退回编码总比在列表上留一个「等3种」强
            String display = name != null ? name : code;
            if (display == null)
            {
                continue;
            }
            // 编码为空时用名称当去重键：没有编码的行彼此之间只能按名字区分
            distinctGoods.add(code != null ? code : "@" + display);
            if (firstName == null)
            {
                firstName = display;
            }
        }
        if (firstName == null)
        {
            return null;
        }
        String head = truncate(firstName, MAX_NAME_LENGTH);
        int kinds = distinctGoods.size();
        return kinds <= 1 ? head : truncate(head + "等" + kinds + "种", MAX_LENGTH);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max)
    {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
