package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;

/**
 * 财务入参文本校验。重点是超长入参必须在服务层就被业务文案挡下来，
 * 不能放到 JDBC 去报「Data too long for column」。
 */
@DisplayName("财务入参文本校验")
class FinanceTextTest
{
    @Test
    void require_去掉首尾空白后返回()
    {
        assertEquals("建行基本户", FinanceText.require("  建行基本户  ", "账户简称", 50));
    }

    @Test
    void require_空白视同未填()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> FinanceText.require("   ", "账户简称", 50));
        assertEquals("账户简称不能为空", error.getMessage());
    }

    @Test
    void require_刚好到上限时放行()
    {
        String exact = repeat('长', 50);
        assertEquals(exact, FinanceText.require(exact, "账户简称", 50));
    }

    @Test
    void require_超长时报业务文案而不是数据库报错()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> FinanceText.require(repeat('长', 51), "账户简称", 50));
        // 提示要带上字段名和上限，用户才知道该删到多少字
        assertTrue(error.getMessage().contains("账户简称"), error.getMessage());
        assertTrue(error.getMessage().contains("50"), error.getMessage());
        assertTrue(error.getMessage().contains("51"), error.getMessage());
    }

    @Test
    void require_长度按去空白后的内容算()
    {
        // 尾部空白不该把用户挤出上限
        assertEquals(repeat('长', 50), FinanceText.require(repeat('长', 50) + "   ", "账户简称", 50));
    }

    @Test
    void optional_空白归一成null()
    {
        assertNull(FinanceText.optional("   ", "备注", 500));
        assertNull(FinanceText.optional(null, "备注", 500));
    }

    @Test
    void optional_有值时同样卡长度()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> FinanceText.optional(repeat('备', 501), "备注", 500));
        assertTrue(error.getMessage().contains("备注"), error.getMessage());
    }

    private String repeat(char ch, int times)
    {
        StringBuilder builder = new StringBuilder(times);
        for (int i = 0; i < times; i++)
        {
            builder.append(ch);
        }
        return builder.toString();
    }
}
