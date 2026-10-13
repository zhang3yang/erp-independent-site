package com.ruoyi.system.service.impl;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 财务与订单模块共同使用的 ISO 4217 币种白名单。
 *
 * <p>库里只存代码（CNY/USD/...），符号由前端 {@code src/utils/currency.js} 渲染，
 * 两边的取值范围必须一致，加币种要同时改这两处。</p>
 *
 * <p>本期不做汇率换算，也不把不同币种的金额汇总成一个余额：一个账户只对应一种币种，
 * 一张订单只对应一种币种，回款流水的账户币种必须与订单币种相同。</p>
 */
final class CurrencySupport
{
    static final String DEFAULT = "CNY";

    /** 用 LinkedHashSet 保序，报错文案里的顺序与前端下拉一致 */
    private static final Set<String> SUPPORTED = new LinkedHashSet<String>(
            Arrays.asList("CNY", "USD", "EUR", "GBP", "JPY", "HKD"));

    private CurrencySupport()
    {
    }

    /**
     * 归一化币种：去空白、转大写、空值按 {@link #DEFAULT} 处理。
     *
     * <p>空值兜底成 CNY 是给历史数据留的路——升级脚本把老账户和老流水刷成 CNY，
     * 万一有漏网的行读出来是 null，也不该让整个页面报错。新建时前端必选，走不到这个分支。</p>
     */
    static String require(String currency)
    {
        String normalized = StringUtils.trimToNull(currency);
        normalized = normalized == null ? DEFAULT : normalized.toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(normalized))
        {
            throw new ServiceException("币种只能是：" + String.join("、", SUPPORTED));
        }
        return normalized;
    }

    /**
     * 两个币种必须相同，否则按给定的主体名报错。
     *
     * @param expected 基准币种，例如订单币种
     * @param actual 待校验币种，例如账户币种
     * @param expectedLabel 基准方的说法，例如「订单 XS2026080001」
     * @param actualLabel 待校验方的说法，例如「账户 招行基本户」
     */
    static void requireSame(String expected, String actual, String expectedLabel, String actualLabel)
    {
        String left = require(expected);
        String right = require(actual);
        if (!left.equals(right))
        {
            throw new ServiceException(actualLabel + "是 " + right + " 币种，与" + expectedLabel
                    + "的 " + left + " 不一致；本系统不做汇率换算，请改用同币种的账户");
        }
    }
}
