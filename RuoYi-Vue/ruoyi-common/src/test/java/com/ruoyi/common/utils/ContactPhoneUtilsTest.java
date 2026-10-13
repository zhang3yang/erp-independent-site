package com.ruoyi.common.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ruoyi.common.exception.ServiceException;

/**
 * 收件 / 联系电话的取值范围。
 *
 * <p>这一套规则存在的意义就是「比 11 位手机号松」，所以前三个用例都是
 * {@link MobileUtils} 会拒、这里必须放行的真实写法。</p>
 */
class ContactPhoneUtilsTest
{
    /** 平台下单带过来的虚拟号，位数不固定，按 11 位收会全部挡掉 */
    @Test
    void acceptsVirtualNumber()
    {
        assertTrue(ContactPhoneUtils.isValid("400-800-1234"));
        assertFalse(MobileUtils.isValid("400-800-1234"), "这正是旧规则挡掉它的地方");
    }

    /** 带区号、带分机的固定电话 */
    @Test
    void acceptsLandline()
    {
        assertTrue(ContactPhoneUtils.isValid("0755-88886666"));
        assertTrue(ContactPhoneUtils.isValid("+86 755 88886666"));
    }

    /** 一次留好几个号码，逗号或分号分隔 */
    @Test
    void acceptsMultipleNumbers()
    {
        assertTrue(ContactPhoneUtils.isValid("13800000000,13900000000"));
        assertTrue(ContactPhoneUtils.isValid("13800000000; 0755-88886666"));
    }

    /** 正常手机号当然还是过 */
    @Test
    void acceptsPlainMobile()
    {
        assertTrue(ContactPhoneUtils.isValid("13800000000"));
    }

    /** 松不等于什么都收：字母、汉字、括号都不是这里认的字符 */
    @Test
    void rejectsNonPhoneCharacters()
    {
        assertFalse(ContactPhoneUtils.isValid("找老王"));
        assertFalse(ContactPhoneUtils.isValid("13800000000转8001"));
        assertFalse(ContactPhoneUtils.isValid("(0755)88886666"));
    }

    /** 光有分隔符没有数字，能过字符集但不是电话 */
    @Test
    void rejectsSeparatorsWithoutDigits()
    {
        assertFalse(ContactPhoneUtils.isValid(",,;"));
        assertFalse(ContactPhoneUtils.isValid("  "));
        assertFalse(ContactPhoneUtils.isValid(""));
        assertFalse(ContactPhoneUtils.isValid(null));
    }

    /** 超过列宽就拦，别等到落库时被 MySQL 截断 */
    @Test
    void rejectsOverlongValue()
    {
        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= ContactPhoneUtils.MAX_LENGTH) tooLong.append("13800000000,");

        assertFalse(ContactPhoneUtils.isValid(tooLong.toString()));
        ServiceException error = assertThrows(ServiceException.class,
                () -> ContactPhoneUtils.validateRequired(tooLong.toString(), "收件电话"));
        assertTrue(error.getMessage().contains("太长"));
    }

    /** 选填：不填放行，填了照样按规则收 */
    @Test
    void optionalSkipsBlankButChecksFilled()
    {
        assertDoesNotThrow(() -> ContactPhoneUtils.validateOptional(null, "收件电话"));
        assertDoesNotThrow(() -> ContactPhoneUtils.validateOptional("  ", "收件电话"));
        assertThrows(ServiceException.class, () -> ContactPhoneUtils.validateOptional("找老王", "收件电话"));
    }

    /** 必填：空值报「不能为空」，格式错报格式 */
    @Test
    void requiredDistinguishesBlankFromMalformed()
    {
        ServiceException blank = assertThrows(ServiceException.class,
                () -> ContactPhoneUtils.validateRequired("", "收件电话"));
        assertTrue(blank.getMessage().contains("不能为空"));

        ServiceException bad = assertThrows(ServiceException.class,
                () -> ContactPhoneUtils.validateRequired("找老王", "收件电话"));
        assertFalse(bad.getMessage().contains("不能为空"));
    }
}
