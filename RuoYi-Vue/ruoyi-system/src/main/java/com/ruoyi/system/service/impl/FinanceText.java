package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 财务模块入参文本校验：去首尾空白、必填、长度上限。
 *
 * <p>长度上限一律照抄建表脚本里对应列的 varchar 宽度。没有这一层，超长入参会一路走到
 * JDBC，用户看到的是「Data too long for column 'accountname' at row 1」——既看不懂，
 * 也不知道是哪个字段超了、上限是多少。</p>
 *
 * <p>用 {@link String#length()} 计数：MySQL 的 varchar(N) 按字符算，Java 的 length()
 * 按 UTF-16 码元算，两者只在增补平面字符（emoji 等）上有差异，且方向是偏严——
 * 宁可提前拦下来，也好过放到 JDBC 那里报错。</p>
 */
final class FinanceText
{
    private FinanceText()
    {
    }

    /**
     * 必填文本：去掉首尾空白后不能为空，且不能超过 maxLength。
     *
     * @param name 字段的业务名称，直接出现在给用户看的提示里
     */
    static String require(String value, String name, int maxLength)
    {
        String trimmed = StringUtils.trimToEmpty(value);
        if (trimmed.isEmpty())
        {
            throw new ServiceException(name + "不能为空");
        }
        return limit(trimmed, name, maxLength);
    }

    /** 选填文本：全空白归一成 null，非空时校验长度 */
    static String optional(String value, String name, int maxLength)
    {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : limit(trimmed, name, maxLength);
    }

    /** 超长即报业务文案，提示里带上上限，用户知道该删到多少字 */
    private static String limit(String trimmed, String name, int maxLength)
    {
        if (trimmed.length() > maxLength)
        {
            throw new ServiceException(name + "不能超过 " + maxLength + " 个字符，当前 "
                    + trimmed.length() + " 个");
        }
        return trimmed;
    }
}
